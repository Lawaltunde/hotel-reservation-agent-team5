import { formatTime, statusLabel } from "@/components/format";
import type { AuthorizationMode, ProgressEvent, TaskStatus } from "@/models/task";
import { isProblemStatus, MAIN_PATH_STATUSES } from "@/models/task";
import type { SubscriptionMode } from "@/services/task-service";

interface ProgressTimelineProps {
  status: TaskStatus | null;
  authorizationMode: AuthorizationMode | null;
  events: ProgressEvent[];
  mode: SubscriptionMode;
}

type StepState = "done" | "current" | "upcoming";

/** Step list for the task's happy path plus a log of every progress message. */
export function ProgressTimeline({ status, authorizationMode, events, mode }: ProgressTimelineProps) {
  // BOOKING only appears when the user allowed the agent to book.
  const steps = MAIN_PATH_STATUSES.filter(
    (s) => s !== "BOOKING" || (authorizationMode !== null && authorizationMode !== "ENQUIRY_ONLY"),
  );

  // Furthest happy-path step seen, from the current status or any earlier event.
  const seen = new Set<string>(events.map((e) => e.status));
  if (status) seen.add(status);
  const reachedIndex = steps.reduce((max, s, i) => (seen.has(s) ? i : max), -1);

  const problem = status && isProblemStatus(status) ? status : null;
  const finished = status === "COMPLETED";

  const stateOf = (index: number): StepState => {
    if (finished || index < reachedIndex) return "done";
    if (index === reachedIndex) return problem ? "done" : "current";
    return "upcoming";
  };

  return (
    <section aria-labelledby="progress-heading" className="rounded-xl border border-slate-200 bg-white p-5 sm:p-6">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="progress-heading" className="text-lg font-semibold">
          Progress
        </h2>
        <ConnectionBadge mode={mode} />
      </div>

      <ol aria-label="Search steps" className="mt-4 flex flex-col gap-0">
        {steps.map((step, index) => {
          const state = stateOf(index);
          const isLast = index === steps.length - 1 && !problem;
          return (
            <li key={step} aria-current={state === "current" ? "step" : undefined} className="relative flex gap-3 pb-4 last:pb-0">
              {!isLast && (
                <span
                  aria-hidden="true"
                  className={`absolute top-6 left-[11px] h-[calc(100%-1.25rem)] w-0.5 ${state === "done" ? "bg-emerald-400" : "bg-slate-200"}`}
                />
              )}
              <StepDot state={state} />
              <span
                className={`pt-0.5 text-sm ${
                  state === "current" ? "font-semibold text-sky-800" : state === "done" ? "text-slate-800" : "text-slate-400"
                }`}
              >
                {statusLabel(step)}
                <span className="sr-only">
                  {state === "done" ? " (done)" : state === "current" ? " (in progress)" : " (not started)"}
                </span>
              </span>
            </li>
          );
        })}
        {problem && (
          <li aria-current="step" className="relative flex gap-3">
            <span aria-hidden="true" className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-rose-100 text-sm font-bold text-rose-700">
              !
            </span>
            <span className="pt-0.5 text-sm font-semibold text-rose-800">{statusLabel(problem)}</span>
          </li>
        )}
      </ol>

      <h3 id="activity-heading" className="mt-6 text-sm font-semibold text-slate-700">
        Agent activity
      </h3>
      {/* Scrollable, so it must be focusable for keyboard users. */}
      <ol
        id="activity-log"
        role="log"
        aria-live="polite"
        aria-relevant="additions"
        aria-labelledby="activity-heading"
        tabIndex={0}
        className="mt-2 max-h-72 overflow-y-auto rounded-lg bg-slate-50 p-3 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
      >
        {events.length === 0 ? (
          <li className="text-slate-500">Waiting for the first update…</li>
        ) : (
          events.map((event, i) => (
            <li key={`${event.at}-${i}`} className="flex gap-3 py-1">
              <time dateTime={event.at} className="w-24 shrink-0 font-mono text-xs leading-5 whitespace-nowrap text-slate-500 tabular-nums">
                {formatTime(event.at)}
              </time>
              <span className="text-slate-800">
                {event.message || statusLabel(event.status)}
              </span>
            </li>
          ))
        )}
      </ol>
    </section>
  );
}

function StepDot({ state }: { state: StepState }) {
  if (state === "done") {
    return (
      <span aria-hidden="true" className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-emerald-500 text-white">
        <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2.5">
          <path d="M3.5 8.5l3 3 6-7" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </span>
    );
  }
  if (state === "current") {
    return (
      <span aria-hidden="true" className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 border-sky-600 bg-white">
        <span className="h-2.5 w-2.5 rounded-full bg-sky-600 motion-safe:animate-pulse" />
      </span>
    );
  }
  return <span aria-hidden="true" className="mt-0.5 h-6 w-6 shrink-0 rounded-full border-2 border-slate-200 bg-white" />;
}

function ConnectionBadge({ mode }: { mode: SubscriptionMode }) {
  const map: Record<SubscriptionMode, { text: string; cls: string }> = {
    connecting: { text: "Connecting…", cls: "text-slate-500" },
    live: { text: "Live updates", cls: "text-emerald-700" },
    polling: { text: "Checking every few seconds", cls: "text-amber-700" },
    closed: { text: "Finished", cls: "text-slate-500" },
  };
  const { text, cls } = map[mode];
  return (
    <span className={`text-xs ${cls}`}>
      <span className="sr-only">Connection: </span>
      {text}
    </span>
  );
}
