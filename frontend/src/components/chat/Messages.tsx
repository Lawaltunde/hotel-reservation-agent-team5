import type { ReactNode } from "react";

import { formatTime } from "@/components/format";

/** The agent's avatar: a front-desk bell, drawn rather than an emoji so it follows the palette. */
function AgentMark() {
  return (
    <span aria-hidden="true" className="mt-1 hidden h-8 w-8 shrink-0 items-center justify-center rounded-full bg-pine text-paper sm:flex">
      <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
        <path d="M4 17h16M6 17a6 6 0 0 1 12 0M12 8V6M10 6h4" />
      </svg>
    </span>
  );
}

/** A turn from the agent. `wide` lets cards (offers, email) use the full column. */
export function AgentMessage({
  children,
  tone = "default",
  wide = false,
}: {
  children: ReactNode;
  tone?: "default" | "error";
  wide?: boolean;
}) {
  return (
    <li className="chat-in flex gap-3">
      <AgentMark />
      <div className={`min-w-0 ${wide ? "flex-1" : "max-w-[42rem]"}`}>
        <p className="sr-only">Agent:</p>
        <div
          className={`rounded-2xl rounded-tl-sm border px-4 py-3 leading-relaxed ${
            tone === "error" ? "border-alarm/40 bg-alarm-soft text-ink" : "border-line bg-paper text-ink"
          }`}
        >
          {children}
        </div>
      </div>
    </li>
  );
}

/** A card that belongs to the agent turn above it (result, quotes, email), aligned with its text. */
export function AgentAttachment({ children, label }: { children: ReactNode; label?: string }) {
  return (
    <li className="chat-in sm:ml-11" aria-label={label}>
      {children}
    </li>
  );
}

/** A turn from the user, right-aligned. Line breaks they typed are kept. */
export function UserMessage({ text }: { text: string }) {
  return (
    <li className="chat-in flex justify-end">
      <div className="max-w-[85%] sm:max-w-[36rem]">
        <p className="sr-only">You:</p>
        <p className="rounded-2xl rounded-tr-sm bg-pine px-4 py-3 leading-relaxed break-words whitespace-pre-wrap text-paper">{text}</p>
      </div>
    </li>
  );
}

export interface StatusLineData {
  key: string;
  text: string;
  at?: string;
  tone?: "default" | "problem";
}

/**
 * The call log: consecutive progress updates on one continuous rule, each with the time it
 * happened. One list item in the conversation, so the rule isn't broken by the message spacing.
 */
export function StatusLog({ lines }: { lines: StatusLineData[] }) {
  if (lines.length === 0) return null;
  return (
    <li className="ml-4">
      <ol aria-label="Progress updates" className="border-l border-rule">
        {lines.map(({ key, text, at, tone = "default" }) => (
          <li key={key} className="chat-in relative flex items-baseline gap-3 py-1.5 pl-[2.1rem] text-sm">
            <span
              aria-hidden="true"
              className={`absolute top-[0.8rem] -left-[4.5px] h-2 w-2 rounded-full ring-4 ring-mist ${tone === "problem" ? "bg-lamp" : "bg-lake"}`}
            />
            {at && (
              <time dateTime={at} className="shrink-0 font-mono text-xs text-muted tabular-nums">
                {formatTime(at)}
              </time>
            )}
            <span className={tone === "problem" ? "text-lamp" : "text-ink"}>{text}</span>
          </li>
        ))}
      </ol>
    </li>
  );
}

/** Shown while waiting on the server; `label` says what for. */
export function TypingIndicator({ label }: { label: string }) {
  return (
    <li className="chat-in flex items-center gap-3">
      <AgentMark />
      <div className="flex items-center gap-3 rounded-2xl rounded-tl-sm border border-line bg-paper px-4 py-3">
        <span aria-hidden="true" className="flex gap-1">
          {[0, 1, 2].map((i) => (
            <span key={i} className="typing-dot h-1.5 w-1.5 rounded-full bg-muted" style={{ animationDelay: `${i * 160}ms` }} />
          ))}
        </span>
        <span className="text-sm text-muted">{label}</span>
      </div>
    </li>
  );
}

/** Reply chips. Picking one sends its `reply` as the user's next message. */
export function Choices({
  label,
  choices,
  onPick,
  disabled,
}: {
  label: string;
  choices: Array<{ label: string; reply: string }>;
  onPick: (reply: string) => void;
  disabled: boolean;
}) {
  return (
    <ul aria-label={label} className="mt-3 flex flex-wrap gap-2">
      {choices.map((c) => (
        <li key={c.reply}>
          <button
            type="button"
            disabled={disabled}
            onClick={() => onPick(c.reply)}
            className="focus-ring rounded-full border border-lake/40 bg-lake-soft px-3 py-1.5 text-sm font-medium text-ink transition hover:border-lake disabled:opacity-50"
          >
            {c.label}
          </button>
        </li>
      ))}
    </ul>
  );
}
