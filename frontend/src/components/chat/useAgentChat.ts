"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { buildPrompt, followUpForCity, followUpForErrors, MAX_PROMPT_LENGTH, type FollowUp } from "@/components/chat/intake";
import type { ProgressEvent, Task } from "@/models/task";
import { ApiError } from "@/services/api-client";
import { createTaskFromPrompt, subscribeToTask, type SubscriptionMode } from "@/services/task-service";

/** One turn of the intake conversation, before a task exists. Task progress is derived from the task, not stored here. */
export type IntakeEntry =
  | { id: string; kind: "user"; text: string }
  | { id: string; kind: "agent"; text: string; tone?: "error"; choices?: FollowUp["choices"]; canRetry?: boolean };

export interface TaskState {
  task: Task | null;
  /** Live progress events received while this page was open (none after a refresh of a finished task). */
  events: ProgressEvent[];
  mode: SubscriptionMode;
  /** The last fetch error; cleared by the next successful fetch. */
  error: ApiError | null;
  notFound: boolean;
}

const EMPTY_TASK_STATE: TaskState = { task: null, events: [], mode: "connecting", error: null, notFound: false };

let counter = 0;
const nextId = () => `m${++counter}`;

/** `crypto.randomUUID` needs a secure context; plain http on a LAN address doesn't have one. */
function newIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto && window.isSecureContext) return crypto.randomUUID();
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
}

function replyForFailure(error: unknown, prompt: string): Omit<Extract<IntakeEntry, { kind: "agent" }>, "id" | "kind"> {
  if (error instanceof ApiError) {
    if (error.status === 422) return followUpForCity(error.problem?.detail, error.suggestions);
    if (error.status === 400) return followUpForErrors(error.errors, prompt);
    if (error.isNetworkError) {
      return { text: "I couldn't reach the server, so nothing was started. Check your connection and try again.", tone: "error", canRetry: true };
    }
    // 503 means the request reader (the language model) is down; the backend's detail says so plainly.
    const detail = error.problem?.detail?.trim();
    return {
      text: `${detail || `The server ran into a problem (HTTP ${error.status}).`} Nothing has been started.`,
      tone: "error",
      canRetry: true,
    };
  }
  return { text: "Something went wrong, so nothing was started. Try again in a moment.", tone: "error", canRetry: true };
}

/**
 * State of the chat: the intake turns, the in-flight request, and once a task exists (`?task=<id>`),
 * the live task. Each user message resends the whole conversation as one prompt.
 */
export function useAgentChat() {
  const router = useRouter();
  const taskId = useSearchParams().get("task")?.trim() || null;

  const [intake, setIntake] = useState<IntakeEntry[]>([]);
  const [pending, setPending] = useState(false);
  // Tagged with the task it belongs to, so switching tasks never shows the previous one's state.
  const [taskState, setTaskState] = useState<TaskState & { for: string | null }>({ ...EMPTY_TASK_STATE, for: null });
  /** The last attempt, so "Try again" resends the same prompt with the same idempotency key. */
  const lastAttempt = useRef<{ prompt: string; key: string } | null>(null);

  const submit = useCallback(
    async (prompt: string, key: string) => {
      lastAttempt.current = { prompt, key };
      setPending(true);
      try {
        const { taskId: created } = await createTaskFromPrompt({ prompt }, key);
        router.replace(`/?task=${encodeURIComponent(created)}`, { scroll: false });
      } catch (error) {
        setIntake((prev) => [...prev, { id: nextId(), kind: "agent", ...replyForFailure(error, prompt) }]);
      } finally {
        setPending(false);
      }
    },
    [router],
  );

  const send = useCallback(
    (raw: string) => {
      const text = raw.trim();
      if (!text || pending || taskId) return;
      const userTexts = [...intake.filter((e) => e.kind === "user").map((e) => e.text), text];
      const prompt = buildPrompt(userTexts);
      setIntake((prev) => [...prev, { id: nextId(), kind: "user", text }]);
      if (prompt.length > MAX_PROMPT_LENGTH) {
        setIntake((prev) => [
          ...prev,
          {
            id: nextId(),
            kind: "agent",
            tone: "error",
            text: `Our conversation is now longer than I can read at once (${MAX_PROMPT_LENGTH.toLocaleString("en-CA")} characters). Start a new search and put everything in one shorter message.`,
          },
        ]);
        return;
      }
      void submit(prompt, newIdempotencyKey());
    },
    [intake, pending, taskId, submit],
  );

  const retry = useCallback(() => {
    if (!lastAttempt.current || pending) return;
    // The retry button belongs to the last failure; take it away so it can't be pressed twice.
    setIntake((prev) => prev.map((e) => (e.kind === "agent" && e.canRetry ? { ...e, canRetry: false } : e)));
    void submit(lastAttempt.current.prompt, lastAttempt.current.key);
  }, [pending, submit]);

  const reset = useCallback(() => {
    lastAttempt.current = null;
    setIntake([]);
    router.replace("/", { scroll: false });
  }, [router]);

  useEffect(() => {
    if (!taskId) return;
    const update = (patch: (s: TaskState) => Partial<TaskState>) =>
      setTaskState((prev) => {
        const base = prev.for === taskId ? prev : { ...EMPTY_TASK_STATE, for: taskId };
        return { ...base, ...patch(base) };
      });
    return subscribeToTask(taskId, {
      onTask: (task) => update(() => ({ task, error: null })),
      onProgress: (event) => update((s) => ({ events: [...s.events, event] })),
      onModeChange: (mode) => update(() => ({ mode })),
      onError: (error) => update(() => ({ error, notFound: error.status === 404 })),
    });
  }, [taskId]);

  const current: TaskState = taskId && taskState.for === taskId ? taskState : EMPTY_TASK_STATE;
  return { taskId, intake, pending, taskState: current, send, retry, reset };
}
