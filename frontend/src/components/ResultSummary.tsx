import { formatMoney, outcomeLabel, statusLabel, taxInclusionLabel } from "@/components/format";
import type { Task } from "@/models/task";
import { isTerminalStatus } from "@/models/task";

type Tone = "success" | "info" | "warning" | "danger";

interface Summary {
  tone: Tone;
  title: string;
  body: string;
}

/** True only for a hotel-confirmed booking. Quotes and enquiries never count. */
export function isConfirmedBooking(task: Task): boolean {
  return task.outcome === "BOOKING_CONFIRMED";
}

function summarize(task: Task): Summary {
  const best = task.bestOffer;
  const price = best ? formatMoney(best.negotiatedTotal ?? best.initialQuotedTotal, best.currency || "CAD") : null;

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
          title: `Best quote: ${price} at ${best.hotelName ?? "an unnamed hotel"}`,
          body: "This is a quote, not a booking. Nothing has been reserved. The result and the call transcript have been emailed to you.",
        };
      }
      return {
        tone: "info",
        title: "Search finished without a usable quote",
        body: "The agent didn't get a quote it could stand behind. The details and transcripts have been emailed to you.",
      };
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
      return { tone: "danger", title: "We only search Ontario", body: "That city isn't an Ontario municipality we support. Please start a new search." };
    case "NO_CANDIDATES":
      return { tone: "warning", title: "No suitable hotels found", body: "No hotel in that city matched your dates, guests and budget." };
    case "NO_VALID_OFFER":
      return { tone: "warning", title: "No usable quote yet", body: "Hotels were called, but none gave a quote that meets your requirements." };
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
        body: best ? `Best quote so far: ${price} (a quote, not a booking).` : "The agent is still working.",
      };
  }
}

const TONE_CLASSES: Record<Tone, string> = {
  success: "border-emerald-200 bg-emerald-50 text-emerald-950",
  info: "border-sky-200 bg-sky-50 text-sky-950",
  warning: "border-amber-200 bg-amber-50 text-amber-950",
  danger: "border-rose-200 bg-rose-50 text-rose-950",
};

/** Headline result of a task, worded so a quote never reads as a booking. */
export function ResultSummary({ task }: { task: Task }) {
  const { tone, title, body } = summarize(task);
  const finished = isTerminalStatus(task.status);
  const best = task.bestOffer;

  return (
    <section aria-labelledby="result-heading" role={finished ? "status" : undefined} className={`rounded-xl border p-5 sm:p-6 ${TONE_CLASSES[tone]}`}>
      <p className="text-xs font-medium tracking-wide uppercase opacity-75">{finished ? "Result" : "So far"}</p>
      <h2 id="result-heading" className="mt-1 text-xl font-semibold">
        {title}
      </h2>
      <p className="mt-2 text-sm">{body}</p>

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
        <p className="mt-4 text-xs opacity-80">
          {taxInclusionLabel(best.taxInclusion)}. Any hotel reference shown is an enquiry reference, not a confirmation number.
        </p>
      )}
    </section>
  );
}

function Stat({ term, value }: { term: string; value: number | null }) {
  return (
    <div>
      <dt className="opacity-75">{term}</dt>
      <dd className="text-lg font-semibold tabular-nums">{value ?? "Unknown"}</dd>
    </div>
  );
}
