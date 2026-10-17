import { EmailPreview } from "@/components/chat/EmailPreview";
import { AgentAttachment, AgentMessage, StatusLog, TypingIndicator, UserMessage, type StatusLineData } from "@/components/chat/Messages";
import { UnderstoodCard } from "@/components/chat/UnderstoodCard";
import type { TaskState } from "@/components/chat/useAgentChat";
import { statusLabel } from "@/components/format";
import { OfferCard } from "@/components/OfferCard";
import { isConfirmedBooking, ResultSummary } from "@/components/ResultSummary";
import { isProblemStatus, isTerminalStatus } from "@/models/task";

interface TaskThreadProps {
  state: TaskState;
  /** False after a refresh, when the intake turns are gone; the original prompt stands in for them. */
  hasIntake: boolean;
}

/**
 * The part of the conversation that follows a created task, derived entirely from the task and
 * its progress events, so a refresh (`?task=<id>`) rebuilds it.
 */
export function TaskThread({ state, hasIntake }: TaskThreadProps) {
  const { task, events, error, notFound } = state;

  if (notFound) {
    return (
      <AgentMessage tone="error">
        I can&apos;t find that search. The link may be wrong or the search may have expired. Start a new search to try again.
      </AgentMessage>
    );
  }
  if (!task) {
    return error ? (
      <AgentMessage tone="error">I can&apos;t reach the server to load this search ({error.message}). I&apos;ll keep trying.</AgentMessage>
    ) : (
      <TypingIndicator label="Loading your search…" />
    );
  }

  const status = events.length > 0 ? events[events.length - 1].status : task.status;
  const finished = isTerminalStatus(status);
  const booked = isConfirmedBooking(task);
  const email = task.request?.email;
  const otherOffers = task.offers.filter((o) => o.offerId !== task.bestOffer?.offerId);
  const offers = task.bestOffer ? [task.bestOffer, ...otherOffers] : otherOffers;

  // Live events when we have them; after a refresh, just the current step.
  const statusLines: StatusLineData[] =
    events.length > 0
      ? events.map((e, i) => ({
          key: `${e.at}-${i}`,
          at: e.at,
          text: e.message || statusLabel(e.status),
          tone: isProblemStatus(e.status) ? "problem" : "default",
        }))
      : finished
        ? []
        : [{ key: "current", text: statusLabel(status), tone: isProblemStatus(status) ? "problem" : "default" }];
  if (error) statusLines.push({ key: "error", tone: "problem", text: `Lost contact with the server (${error.message}). Still trying…` });

  return (
    <>
      {!hasIntake && task.request?.originalPrompt && <UserMessage text={task.request.originalPrompt} />}

      <AgentMessage wide>
        <p>{finished ? "Here's what I understood from your request." : `Got it. Here's what I understood. I'm starting now: you can watch progress here, and ${email ? `I'll email the result to ${email}` : "I'll email you the result"}.`}</p>
        <UnderstoodCard task={task} />
      </AgentMessage>

      <StatusLog lines={statusLines} />

      {!finished && <TypingIndicator label={`${statusLabel(status)}…`} />}

      {finished && (
        <AgentAttachment label="Result">
          <ResultSummary task={{ ...task, status }} />
        </AgentAttachment>
      )}

      {finished && offers.length > 0 && (
        <>
          <AgentMessage>
            Here {offers.length === 1 ? "is the quote" : `are the ${offers.length} quotes`} I collected. Prices are all-in totals in
            Canadian dollars. A quote is not a booking: nothing is reserved.
          </AgentMessage>
          {offers.map((offer, i) => (
            <AgentAttachment key={offer.offerId}>
              <OfferCard offer={offer} best={i === 0 && offer.offerId === task.bestOffer?.offerId} booked={booked && i === 0} />
            </AgentAttachment>
          ))}
        </>
      )}

      {finished && task.report && (
        <AgentMessage wide>
          <p>
            {task.report.sentAt
              ? `This is the email I sent${task.report.to ? ` to ${task.report.to}` : ""}, exactly as it went out.`
              : "This is the result email. It hasn't been sent yet; its status is shown on the card."}
          </p>
          <EmailPreview report={task.report} />
        </AgentMessage>
      )}
    </>
  );
}
