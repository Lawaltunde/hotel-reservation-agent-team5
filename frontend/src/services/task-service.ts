import type { CreateTaskRequest, CreateTaskResponse, ProgressEvent, Task } from "@/models/task";
import { isTerminalStatus } from "@/models/task";
import { API_BASE, ApiError, apiRequest, isAbortError, type RequestOptions } from "@/services/api-client";

/** `POST /api/tasks`: starts a search. Resolves with the `202` body. */
export function createTask(request: CreateTaskRequest, options?: RequestOptions): Promise<CreateTaskResponse> {
  return apiRequest<CreateTaskResponse>("/tasks", { ...options, method: "POST", body: request });
}

/** `GET /api/tasks/{id}`: current task state, offers included. */
export function getTask(taskId: string, options?: RequestOptions): Promise<Task> {
  return apiRequest<Task>(`/tasks/${encodeURIComponent(taskId)}`, { ...options, method: "GET" });
}

export type SubscriptionMode = "connecting" | "live" | "polling" | "closed";

export interface TaskSubscriptionHandlers {
  /** A progress event, from SSE or synthesized from a status change while polling (message `null`). */
  onProgress?: (event: ProgressEvent) => void;
  /** A fresh task snapshot (fetched at start, after each progress event, and on every poll). */
  onTask?: (task: Task) => void;
  /** How updates are currently arriving. */
  onModeChange?: (mode: SubscriptionMode) => void;
  /** A fetch failed. Polling keeps retrying, except for 404 which ends the subscription. */
  onError?: (error: ApiError) => void;
}

export interface TaskSubscriptionOptions {
  pollIntervalMs?: number;
}

/**
 * Follows a task until it reaches a terminal status. Prefers SSE (`GET /api/tasks/{id}/events`,
 * event `progress`); if the stream errors, switches to polling `GET /api/tasks/{id}`.
 *
 * @returns an unsubscribe function that stops all network activity.
 */
export function subscribeToTask(
  taskId: string,
  handlers: TaskSubscriptionHandlers,
  { pollIntervalMs = 3000 }: TaskSubscriptionOptions = {},
): () => void {
  const controller = new AbortController();
  let closed = false;
  let eventSource: EventSource | null = null;
  let pollTimer: ReturnType<typeof setTimeout> | null = null;
  let lastStatus: string | null = null;

  const setMode = (mode: SubscriptionMode) => {
    if (!closed || mode === "closed") handlers.onModeChange?.(mode);
  };

  const close = () => {
    if (closed) return;
    closed = true;
    controller.abort();
    eventSource?.close();
    eventSource = null;
    if (pollTimer) clearTimeout(pollTimer);
    setMode("closed");
  };

  const reportError = (error: unknown) => {
    if (closed || isAbortError(error)) return;
    const apiError = error instanceof ApiError ? error : new ApiError(String(error));
    handlers.onError?.(apiError);
    if (apiError.status === 404) close();
  };

  /** Fetches the task, coalescing overlapping requests into one follow-up fetch. */
  let inFlight: Promise<Task | null> | null = null;
  let refetchQueued = false;
  const refresh = async (): Promise<Task | null> => {
    if (inFlight) {
      refetchQueued = true;
      return inFlight;
    }
    inFlight = (async () => {
      try {
        const task = await getTask(taskId, { signal: controller.signal });
        if (closed) return null;
        lastStatus ??= task.status;
        handlers.onTask?.(task);
        return task;
      } catch (error) {
        reportError(error);
        return null;
      } finally {
        inFlight = null;
        if (refetchQueued && !closed) {
          refetchQueued = false;
          void refresh();
        }
      }
    })();
    return inFlight;
  };

  const startPolling = () => {
    if (closed) return;
    eventSource?.close();
    eventSource = null;
    setMode("polling");

    const tick = async () => {
      const task = await refresh();
      if (closed) return;
      if (task) {
        if (task.status !== lastStatus) {
          lastStatus = task.status;
          handlers.onProgress?.({ taskId, status: task.status, message: null, at: task.updatedAt });
        }
        if (isTerminalStatus(task.status)) {
          close();
          return;
        }
      }
      pollTimer = setTimeout(tick, pollIntervalMs);
    };
    pollTimer = setTimeout(tick, pollIntervalMs);
  };

  const startStream = () => {
    if (closed) return;
    if (typeof EventSource === "undefined") {
      startPolling();
      return;
    }
    const source = new EventSource(`${API_BASE}/tasks/${encodeURIComponent(taskId)}/events`);
    eventSource = source;

    source.onopen = () => setMode("live");
    source.addEventListener("progress", (message: MessageEvent<string>) => {
      if (closed) return;
      let event: ProgressEvent;
      try {
        event = JSON.parse(message.data) as ProgressEvent;
      } catch {
        return; // ignore malformed frames
      }
      lastStatus = event.status;
      handlers.onProgress?.(event);
      if (isTerminalStatus(event.status)) {
        source.close();
        void refresh().finally(close);
      } else {
        void refresh();
      }
    });
    // EventSource would reconnect on its own, but an error usually means the endpoint is
    // unavailable or a proxy dropped it; polling is predictable and good enough at 3 s.
    source.onerror = () => {
      if (!closed) startPolling();
    };
  };

  setMode("connecting");
  void refresh().then((task) => {
    if (closed) return;
    if (task && isTerminalStatus(task.status)) close();
    else startStream();
  });

  return close;
}
