"use client";

import { useEffect, useRef, useState } from "react";

import { BackendStatus } from "@/components/BackendStatus";
import { ChatLog } from "@/components/chat/ChatLog";
import { Composer } from "@/components/chat/Composer";
import { AgentMessage, Choices, TypingIndicator, UserMessage } from "@/components/chat/Messages";
import { TaskThread } from "@/components/chat/TaskThread";
import { useAgentChat } from "@/components/chat/useAgentChat";
import { isTerminalStatus } from "@/models/task";

const EXAMPLES = [
  "Halifax, Nov 20–22, 2 adults, under $250 a night. Email me at alex@example.com",
  "yo need a hotel in montréal friday cheap pls, free parking. my mail is sam@example.com",
  "Cheapest room in downtown Calgary for 1 adult, Jan 14–16, must have Wi-Fi. Results to jordan@example.com",
  "Vancouver, family of 4, Dec 27 to Jan 2, pool please. Send results to casey@example.com",
];

const STEPS = [
  { title: "One message", body: "The city, your dates and guests, a budget and your email. Whatever you leave out, the agent asks the hotels." },
  { title: "We call hotels", body: "An AI agent phones the most promising ones and asks for price and availability." },
  { title: "Cheapest confirmed-available price by email", body: "All-in, in Canadian dollars, with each call's transcript." },
];

const TRUST = [
  "The agent never books, holds or pays. It only asks for prices and availability.",
  "On every call, the agent says it's an AI.",
  "Prices are compared all-in: room, sales taxes, levies and fees.",
  "Hotels anywhere in Canada.",
];

/** The whole app: a short explainer rail and the conversation with the agent. */
export function ChatApp() {
  const { taskId, intake, pending, taskState, send, retry, reset } = useAgentChat();
  const [draft, setDraft] = useState("");
  const [sentCount, setSentCount] = useState(0);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const hasConversation = intake.length > 0 || taskId !== null;
  const status = taskState.events.at(-1)?.status ?? taskState.task?.status ?? null;
  const finished = status !== null && isTerminalStatus(status);
  const last = intake.at(-1);

  // Give the keyboard back to the composer when the agent has replied.
  useEffect(() => {
    if (!pending && !taskId && intake.length > 0) inputRef.current?.focus();
  }, [pending, taskId, intake.length]);

  function handleSend(text: string) {
    send(text);
    setDraft("");
    setSentCount((n) => n + 1);
  }

  function startOver() {
    reset();
    setDraft("");
    requestAnimationFrame(() => inputRef.current?.focus());
  }

  return (
    <div className="lg:grid lg:min-h-dvh lg:grid-cols-[minmax(20rem,26rem)_minmax(0,1fr)]">
      <main className="flex h-dvh flex-col bg-mist lg:order-2 lg:border-l lg:border-line" aria-label="Chat with the hotel agent">
        <header className="flex items-center justify-between gap-3 border-b border-line bg-paper/80 px-4 py-3 backdrop-blur sm:px-6">
          <div className="min-w-0">
            <h1 className="truncate font-display text-lg leading-tight text-ink">Hotel Deal Agent</h1>
            <p className="truncate text-xs text-muted">AI agent · phones hotels across Canada</p>
          </div>
          <div className="flex shrink-0 items-center gap-4">
            <BackendStatus />
            {hasConversation && (
              <button
                type="button"
                onClick={startOver}
                className="focus-ring rounded-md border border-line px-3 py-1.5 text-sm font-medium text-ink hover:border-lake"
              >
                New search
              </button>
            )}
          </div>
        </header>

        <ChatLog followKey={sentCount} label="Conversation with the hotel agent">
          <AgentMessage>
            <p>
              Hi! I&apos;m an AI agent that phones hotels anywhere in Canada and finds the cheapest confirmed-available price,
              all-in. I never book, hold or pay; I only ask for prices and availability.
            </p>
            <p className="mt-2">
              Tell me everything in one message: <strong className="font-semibold">the city</strong>,{" "}
              <strong className="font-semibold">your dates and guests</strong>, a budget, any must-haves, and{" "}
              <strong className="font-semibold">the email for the results</strong>. Write it however you like. Anything you
              leave out, I&apos;ll ask the hotels about.
            </p>
            {!hasConversation && (
              <>
                <p className="mt-3 text-sm text-muted">Or start from an example:</p>
                <ul className="mt-2 flex flex-col gap-2">
                  {EXAMPLES.map((example) => (
                    <li key={example}>
                      <button
                        type="button"
                        onClick={() => {
                          setDraft(example);
                          inputRef.current?.focus();
                        }}
                        className="focus-ring w-full rounded-lg border border-dashed border-line px-3 py-2 text-left text-sm text-ink transition hover:border-lake hover:bg-lake-soft"
                      >
                        {example}
                      </button>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </AgentMessage>

          {intake.map((entry) =>
            entry.kind === "user" ? (
              <UserMessage key={entry.id} text={entry.text} />
            ) : (
              <AgentMessage key={entry.id} tone={entry.tone}>
                <p>{entry.text}</p>
                {entry.choices && entry.choices.length > 0 && (
                  <Choices
                    label="Suggested replies"
                    choices={entry.choices}
                    onPick={handleSend}
                    disabled={pending || taskId !== null || entry !== last}
                  />
                )}
                {entry.canRetry && (
                  <button
                    type="button"
                    onClick={retry}
                    disabled={pending}
                    className="focus-ring mt-3 rounded-md border border-alarm/40 bg-paper px-3 py-1.5 text-sm font-semibold text-alarm hover:border-alarm disabled:opacity-50"
                  >
                    Try again
                  </button>
                )}
              </AgentMessage>
            ),
          )}

          {pending && <TypingIndicator label="Reading your message…" />}

          {taskId && <TaskThread state={taskState} hasIntake={intake.length > 0} />}
        </ChatLog>

        {taskId ? (
          <div className="border-t border-line bg-paper/80 px-4 py-3 backdrop-blur sm:px-6">
            <div className="mx-auto flex max-w-3xl flex-wrap items-center justify-between gap-3">
              <p className="text-sm text-muted">
                {finished
                  ? "This search is finished."
                  : "The agent is working on this search. You can close this page; the result will be emailed."}
              </p>
              <button
                type="button"
                onClick={startOver}
                className="focus-ring rounded-md bg-pine px-4 py-2 text-sm font-semibold text-paper hover:brightness-110"
              >
                Start a new search
              </button>
            </div>
          </div>
        ) : (
          <div className="pt-2">
            <Composer value={draft} onChange={setDraft} onSend={handleSend} disabled={pending} inputRef={inputRef} />
          </div>
        )}
      </main>

      <aside className="flex flex-col gap-10 px-6 py-10 sm:px-10 lg:order-1 lg:h-dvh lg:overflow-y-auto lg:py-12" aria-label="About the hotel agent">
        <div>
          <p className="font-mono text-[0.7rem] tracking-[0.14em] text-muted uppercase">Canada · hotels by phone</p>
          <p className="mt-4 font-display text-4xl leading-[1.08] text-ink">
            Tell us the stay.
            <br />
            <span className="text-pine">We&apos;ll call the hotels.</span>
          </p>
          <p className="mt-4 text-muted">
            Hotels sometimes offer a better rate on the phone than online. The agent asks for it, so
            you don&apos;t have to.
          </p>
        </div>

        <section aria-labelledby="how-heading">
          <h2 id="how-heading" className="font-mono text-[0.7rem] tracking-[0.14em] text-muted uppercase">
            How it works
          </h2>
          <ol className="mt-4 flex flex-col gap-4">
            {STEPS.map((step, i) => (
              <li key={step.title} className="flex gap-3">
                <span aria-hidden="true" className="w-5 shrink-0 font-display text-xl leading-6 text-pine">
                  {i + 1}
                </span>
                <div>
                  <h3 className="font-semibold text-ink">{step.title}</h3>
                  <p className="mt-0.5 text-sm text-muted">{step.body}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>

        <section aria-labelledby="trust-heading">
          <h2 id="trust-heading" className="font-mono text-[0.7rem] tracking-[0.14em] text-muted uppercase">
            Our promises
          </h2>
          <ul className="mt-4 flex flex-col gap-2.5 text-sm text-ink">
            {TRUST.map((line) => (
              <li key={line} className="flex gap-2.5">
                <svg aria-hidden="true" viewBox="0 0 16 16" className="mt-0.5 h-4 w-4 shrink-0 text-pine" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M3.5 8.5l3 3 6-7" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                {line}
              </li>
            ))}
          </ul>
        </section>

        <footer className="mt-auto border-t border-line pt-6 text-xs text-muted">
          <p>Hotel Deal Agent finds the cheapest confirmed-available hotel price across Canada. It never books.</p>
        </footer>
      </aside>
    </div>
  );
}
