import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { ProgressEvent, Task, TaskStatus } from "@/models/task";
import { ApiError } from "@/services/api-client";
import { createTaskFromPrompt, getTask, subscribeToTask, type SubscriptionMode } from "@/services/task-service";
import { makeTask } from "@/test/fixtures";

const TASK_ID = "11111111-1111-4111-8111-111111111111";

/** Minimal EventSource double: tests push frames and errors by hand. */
class FakeEventSource {
  static instances: FakeEventSource[] = [];
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;
  private listeners = new Map<string, ((e: MessageEvent<string>) => void)[]>();

  constructor(readonly url: string) {
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (e: MessageEvent<string>) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  close() {
    this.closed = true;
  }

  emit(type: string, data: string) {
    for (const l of this.listeners.get(type) ?? []) l(new MessageEvent(type, { data }));
  }

  progress(status: TaskStatus, message = `now ${status}`) {
    const event: ProgressEvent = { taskId: TASK_ID, status, message, at: "2026-11-02T12:00:01Z" };
    this.emit("progress", JSON.stringify(event));
  }
}

const fetchMock = vi.fn<typeof fetch>();

function taskResponse(status: TaskStatus) {
  return Response.json(makeTask({ status, outcome: null }));
}

/** Lets pending promise callbacks run. */
async function flush() {
  for (let i = 0; i < 5; i++) await Promise.resolve();
  await new Promise((r) => setTimeout(r, 0));
}

function recorder() {
  const modes: SubscriptionMode[] = [];
  const tasks: Task[] = [];
  const events: ProgressEvent[] = [];
  const errors: ApiError[] = [];
  return {
    modes,
    tasks,
    events,
    errors,
    handlers: {
      onModeChange: (m: SubscriptionMode) => modes.push(m),
      onTask: (t: Task) => tasks.push(t),
      onProgress: (e: ProgressEvent) => events.push(e),
      onError: (e: ApiError) => errors.push(e),
    },
  };
}

beforeEach(() => {
  FakeEventSource.instances = [];
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  vi.stubGlobal("EventSource", FakeEventSource);
});

afterEach(() => {
  vi.useRealTimers();
});

describe("createTaskFromPrompt / getTask", () => {
  it("POSTs the prompt to /api/tasks/prompt with the idempotency key", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ taskId: TASK_ID, status: "VALIDATING" }, { status: 202 }));

    const request = { prompt: "Ottawa, Nov 20-22, 2 adults, a@example.com" };
    await expect(createTaskFromPrompt(request, "key-1")).resolves.toEqual({ taskId: TASK_ID, status: "VALIDATING" });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/tasks/prompt");
    expect(init?.method).toBe("POST");
    expect(new Headers(init?.headers).get("Idempotency-Key")).toBe("key-1");
    expect(JSON.parse(String(init?.body))).toEqual(request);
  });

  it("encodes the task id in the path", async () => {
    fetchMock.mockResolvedValueOnce(taskResponse("COMPLETED"));
    await getTask("a/b c");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/tasks/a%2Fb%20c");
  });
});

describe("subscribeToTask", () => {
  it("does not open a stream for a task that is already finished", async () => {
    fetchMock.mockResolvedValueOnce(taskResponse("COMPLETED"));
    const r = recorder();

    subscribeToTask(TASK_ID, r.handlers);
    await flush();

    expect(r.tasks.map((t) => t.status)).toEqual(["COMPLETED"]);
    expect(FakeEventSource.instances).toHaveLength(0);
    expect(r.modes).toEqual(["connecting", "closed"]);
  });

  it("streams progress over SSE, refreshing the task after each event, until a terminal status", async () => {
    fetchMock
      .mockResolvedValueOnce(taskResponse("DISCOVERING"))
      .mockResolvedValueOnce(taskResponse("NO_CANDIDATES"))
      .mockResolvedValueOnce(taskResponse("COMPLETED"));
    const r = recorder();

    subscribeToTask(TASK_ID, r.handlers);
    await flush();

    const source = FakeEventSource.instances[0];
    expect(source.url).toBe(`/api/tasks/${TASK_ID}/events`);
    source.onopen?.();
    expect(r.modes).toEqual(["connecting", "live"]);

    source.progress("NO_CANDIDATES", "No callable hotels found");
    await flush();
    expect(r.events.map((e) => e.status)).toEqual(["NO_CANDIDATES"]);
    expect(r.events[0].message).toBe("No callable hotels found");
    expect(source.closed).toBe(false);

    source.progress("COMPLETED");
    await flush();
    expect(r.events.map((e) => e.status)).toEqual(["NO_CANDIDATES", "COMPLETED"]);
    expect(source.closed).toBe(true);
    expect(r.tasks.map((t) => t.status)).toEqual(["DISCOVERING", "NO_CANDIDATES", "COMPLETED"]);
    expect(r.modes.at(-1)).toBe("closed");
  });

  it("ignores malformed frames", async () => {
    fetchMock.mockResolvedValueOnce(taskResponse("DISCOVERING"));
    const r = recorder();

    subscribeToTask(TASK_ID, r.handlers);
    await flush();
    FakeEventSource.instances[0].emit("progress", "{not json");
    await flush();

    expect(r.events).toEqual([]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("falls back to polling when the stream fails, and synthesizes progress from status changes", async () => {
    vi.useFakeTimers();
    fetchMock
      .mockResolvedValueOnce(taskResponse("CALLING_FOR_QUOTES"))
      .mockResolvedValueOnce(taskResponse("CALLING_FOR_QUOTES"))
      .mockResolvedValueOnce(taskResponse("EMAIL_PENDING"))
      .mockResolvedValueOnce(taskResponse("COMPLETED"));
    const r = recorder();

    subscribeToTask(TASK_ID, r.handlers, { pollIntervalMs: 1000 });
    await vi.advanceTimersByTimeAsync(0);
    const source = FakeEventSource.instances[0];
    source.onerror?.();

    expect(source.closed).toBe(true);
    expect(r.modes).toEqual(["connecting", "polling"]);

    await vi.advanceTimersByTimeAsync(1000); // unchanged
    expect(r.events).toEqual([]);
    await vi.advanceTimersByTimeAsync(1000);
    expect(r.events.map((e) => [e.status, e.message])).toEqual([["EMAIL_PENDING", null]]);
    await vi.advanceTimersByTimeAsync(1000);
    expect(r.events.map((e) => e.status)).toEqual(["EMAIL_PENDING", "COMPLETED"]);
    expect(r.modes.at(-1)).toBe("closed");

    await vi.advanceTimersByTimeAsync(10_000);
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it("keeps polling through transient errors", async () => {
    vi.useFakeTimers();
    vi.stubGlobal("EventSource", undefined);
    fetchMock
      .mockResolvedValueOnce(taskResponse("RANKING"))
      .mockResolvedValueOnce(new Response("busy", { status: 503 }))
      .mockResolvedValueOnce(taskResponse("COMPLETED"));
    const r = recorder();

    subscribeToTask(TASK_ID, r.handlers, { pollIntervalMs: 500 });
    await vi.advanceTimersByTimeAsync(0);
    expect(r.modes).toEqual(["connecting", "polling"]);

    await vi.advanceTimersByTimeAsync(500);
    expect(r.errors.map((e) => e.status)).toEqual([503]);
    await vi.advanceTimersByTimeAsync(500);
    expect(r.events.map((e) => e.status)).toEqual(["COMPLETED"]);
    expect(r.modes.at(-1)).toBe("closed");
  });

  it("stops for good on 404", async () => {
    fetchMock.mockResolvedValueOnce(
      Response.json({ status: 404, title: "Task not found" }, { status: 404, headers: { "Content-Type": "application/problem+json" } }),
    );
    const r = recorder();

    subscribeToTask(TASK_ID, r.handlers);
    await flush();

    expect(r.errors).toHaveLength(1);
    expect(r.errors[0].status).toBe(404);
    expect(r.modes).toEqual(["connecting", "closed"]);
    expect(FakeEventSource.instances).toHaveLength(0);
  });

  it("unsubscribing stops the stream and further callbacks", async () => {
    fetchMock.mockResolvedValueOnce(taskResponse("DISCOVERING"));
    const r = recorder();

    const unsubscribe = subscribeToTask(TASK_ID, r.handlers);
    await flush();
    const source = FakeEventSource.instances[0];
    unsubscribe();
    source.progress("RANKING");
    await flush();

    expect(source.closed).toBe(true);
    expect(r.events).toEqual([]);
    expect(r.modes.at(-1)).toBe("closed");
  });
});
