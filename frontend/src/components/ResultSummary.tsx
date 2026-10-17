import { ESTIMATE_NOTE, formatMoney, formatQuoteValidity, outcomeLabel, rankingPrice, statusLabel } from "@/components/format";
import type { Task } from "@/models/task";
import { isTerminalStatus } from "@/models/task";

type Tone = "success" | "info" | "warning" | "danger";

interface Summary {
  tone: Tone;
  title: string;
  body: string;
  /** Concrete next steps, shown as a list. */
  nextSteps?: string[];
}

/** What a user can change when no hotel fits. */
const WIDEN_SEARCH = [
  "Try different or more flexible dates.",
  "Raise or remove the maximum budget.",
  "Remove amenities you can do without.",
  "Search a nearby city.",
];

/** True only for a hotel-confirmed booking. Quotes and enquiries never count. */
export function isConfirmedBooking(task: Task): boolean {
  return task.outcome === "BOOKING_CONFIRMED";
}

/** "CAD 412.30 all-in at Hotel X", or a plain statement that the all-in total is unknown. */
function bestQuotePhrase(task: Task): string | null {
  const best = task.bestOffer;
  if (!best) return null;
  const hotel = best.hotelName ?? "an unnamed hotel";
  const { amount, estimated } = rankingPrice(best);
  if (amount === null) return `from ${hotel}, all-in total unknown`;
  const money = formatMoney(amount, best.currency || "CAD");
  return estimated ? `≈ ${money} at ${hotel} (${ESTIMATE_NOTE})` : `${money} all-in at ${hotel}`;
}

/** Who the result email went to: the user, or the organizer inbox when the prompt had no email. */
function emailedTo(task: Task): string {
  const source = task.report?.emailSource ?? task.request?.emailSource;
  return source === "ORGANIZER_FALLBACK" ? "the organizer inbox" : "you";
}

/** Summary for a finished search that has no usable quote, keyed by outcome. */
function noQuoteSummary(task: Task): Summary {
  switch (task.outcome) {
    case "NO_ELIGIBLE_HOTELS":
      return {
        tone: "warning",
        title: "No eligible hotels found",
        body: "No hotel in that city matched your dates, guests, budget and amenities, so no calls were made.",
        nextSteps: WIDEN_SEARCH,
      };
    case "CALL_FAILED":
      return {
        tone: "warning",
        title: "We couldn't get through to the hotels",
        body: "The calls didn't connect or ended before a quote was given. Nothing was reserved. You can try again later.",
      };
    case "FOLLOW_UP_REQUIRED":
      return {
        tone: "info",
        title: "A hotel needs to follow up",
        body: `A hotel couldn't give a full quote on the call. Nothing was reserved. The details have been emailed to ${emailedTo(task)}.`,
      };
    default:
      return {
        tone: "info",
        title: "Search finished without a usable quote",
        body: `The agent didn't get a quote it could stand behind. Nothing was reserved. The details and transcripts have been emailed to ${emailedTo(task)}.`,
        nextSteps: WIDEN_SEARCH,
      };
  }
}

function summarize(task: Task): Summary {
  const best = task.bestOffer;
  const price = bestQuotePhrase(task);

  switch (task.status) {
    case "COMPLETED":
      if (isConfirmedBooking(task)) {
        return {
          tone: "success",
          title: "Booking confirmed by the hotel",
          body: "The hotel confirmed the reservation. The confirmation details and call transcript are in your email.",
        };
      }
      if (best) {
        return {
          tone: "success",
          title: `Cheapest confirmed-available: ${price}`,
          body: `This is a quote, not a booking. Nothing has been reserved. The result and the call transcript have been emailed to ${emailedTo(task)}.`,
        };
      }
      return noQuoteSummary(task);
    case "EMAIL_FAILED":
      return {
        tone: "warning",
        title: "We couldn't send your email",
        body: best
          ? `The search finished (best quote ${price}, not a booking), but the report email failed. The result is shown below.`
          : "The search finished, but the report email failed. What we found is shown below.",
      };
    case "DEADLINE_EXCEEDED":
      return {
        tone: "warning",
        title: "The deadline passed before the search finished",
        body: best
          ? `The best quote found in time was ${price}. It is a quote, not a booking.`
          : "No usable quote was found before the deadline.",
      };
    case "INVALID_REQUEST":
      return { tone: "danger", title: "The request was rejected", body: "Something in the request was invalid. Please start a new search." };
    case "OUT_OF_SCOPE_LOCATION":
      return { tone: "danger", title: "We only search Canada", body: "That place isn't a Canadian city we can search. Please start a new search." };
    case "NO_CANDIDATES":
      return {
        tone: "warning",
        title: "No suitable hotels found",
        body: "No hotel in that city matched your dates, guests, budget and amenities, so no calls were made.",
        nextSteps: WIDEN_SEARCH,
      };
    case "NO_VALID_OFFER":
      return {
        tone: "warning",
        title: "No usable quote yet",
        body: "Hotels were called, but none gave a complete all-in quote that meets your requirements. Nothing was reserved.",
      };
    case "BOOKING_UNCERTAIN":
      return {
        tone: "danger",
        title: "Booking status is uncertain",
        body: "A call ended before we could confirm whether a room was reserved. Do not book again until this is resolved; check your email for the transcript.",
      };
    case "BOOKING_FAILED":
      return { tone: "danger", title: "The booking did not go through", body: "Nothing was reserved. Any quote below is still only a quote." };
    default:
      return {
        tone: "info",
        title: statusLabel(task.status),
        body: best ? `Cheapest so far: ${price} (a quote, not a booking).` : "The agent is still working.",
      };
  }
}

const TONE_CLASSES: Record<Tone, string> = {
  success: "border-pine/30 bg-pine-soft text-ink",
  info: "border-lake/30 bg-lake-soft text-ink",
  warning: "border-lamp-line bg-lamp-soft text-ink",
  danger: "border-alarm/30 bg-alarm-soft text-ink",
};

/** Headline result of a task, worded so a quote never reads as a booking. */
export function ResultSummary({ task }: { task: Task }) {
  const { tone, title, body, nextSteps } = summarize(task);
  const finished = isTerminalStatus(task.status);
  const best = task.bestOffer;
  const validity = best ? formatQuoteValidity(best) : null;

  return (
    <section aria-labelledby="result-heading" className={`rounded-lg border p-5 sm:p-6 ${TONE_CLASSES[tone]}`}>
      <p className="font-mono text-[0.7rem] tracking-[0.14em] uppercase opacity-75">{finished ? "Result" : "So far"}</p>
      <h2 id="result-heading" className="mt-1 font-display text-2xl leading-snug">
        {title}
      </h2>
      <p className="mt-2 text-sm">{body}</p>
      {nextSteps && nextSteps.length > 0 && (
        <div className="mt-3 text-sm">
          <p className="font-medium">You could:</p>
          <ul className="mt-1 list-disc space-y-0.5 pl-5">
            {nextSteps.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ul>
        </div>
      )}

      <dl className="mt-4 grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
        <Stat term="Hotels screened" value={task.candidatesScreened} />
        <Stat term="Calls placed" value={task.callsPlaced} />
        <Stat term="Quotes captured" value={task.offers.length} />
        {task.outcome && (
          <div>
            <dt className="opacity-75">Outcome</dt>
            <dd className="font-medium">{outcomeLabel(task.outcome)}</dd>
          </div>
        )}
      </dl>

      {best && !isConfirmedBooking(task) && (
        <div className="mt-4 space-y-1 text-xs opacity-80">
          {validity && <p>{validity}.</p>}
          {best.totalsDisagree && <p>The hotel&apos;s stated total and its itemized figures differ; see the quote below.</p>}
          <p>
            All-in means room, sales taxes, levies and fees, in Canadian dollars. Any hotel reference
            shown is an enquiry reference, not a confirmation number.
          </p>
        </div>
      )}
    </section>
  );
}

function Stat({ term, value }: { term: string; value: number | null }) {
  return (
    <div>
      <dt className="opacity-75">{term}</dt>
      <dd className="font-mono text-lg tabular-nums">{value ?? "Unknown"}</dd>
    </div>
  );
}
