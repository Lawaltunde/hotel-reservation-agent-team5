import type { Offer, TaskOutcome, TaskStatus, TaxInclusion } from "@/models/task";

/** Placeholder for any value the backend reported as unknown (`null`). Never shown as 0. */
export const UNKNOWN = "Unknown";

/** "CAD 1,234.50" style money, or "Unknown" for null. */
export function formatMoney(amount: number | null | undefined, currency: string = "CAD"): string {
  if (amount === null || amount === undefined || !Number.isFinite(amount)) return UNKNOWN;
  try {
    return new Intl.NumberFormat("en-CA", { style: "currency", currency, currencyDisplay: "code" }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

/** A `YYYY-MM-DD` stay date as e.g. "Fri, Oct 23, 2026". Parsed as a calendar date, not an instant. */
export function formatStayDate(isoDate: string | null | undefined): string {
  if (!isoDate) return UNKNOWN;
  const [y, m, d] = isoDate.split("-").map(Number);
  if (!y || !m || !d) return isoDate;
  return new Intl.DateTimeFormat("en-CA", {
    weekday: "short",
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(y, m - 1, d)));
}

/** An ISO instant in the viewer's time zone, e.g. "Oct 17, 2026, 3:45 p.m. EDT". */
export function formatInstant(iso: string | null | undefined): string {
  if (!iso) return UNKNOWN;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  // dateStyle/timeStyle can't be combined with timeZoneName, so spell the fields out.
  return new Intl.DateTimeFormat("en-CA", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZoneName: "short",
  }).format(date);
}

/**
 * How long a quote holds, as `Valid until <time> (hotel said: "<text>")`, or `null` when the
 * hotel gave nothing usable. Either half may be missing; the hotel's words are never paraphrased.
 */
export function formatQuoteValidity(offer: Pick<Offer, "quoteValidUntil" | "quoteValidityText">): string | null {
  const until = offer.quoteValidUntil ? formatInstant(offer.quoteValidUntil) : null;
  const said = offer.quoteValidityText?.trim() || null;
  if (until && said) return `Valid until ${until} (hotel said: "${said}")`;
  if (until) return `Valid until ${until}`;
  if (said) return `Hotel said: "${said}"`;
  return null;
}

/** Time of day only, for the progress log. */
export function formatTime(iso: string | null | undefined): string {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("en-CA", { timeStyle: "medium" }).format(date);
}

/** "SOME_VALUE" -> "Some value". */
export function humanize(value: string): string {
  const s = value.replace(/_/g, " ").toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export const STATUS_LABELS: Record<TaskStatus, string> = {
  RECEIVED: "Request received",
  VALIDATING: "Checking your request",
  DISCOVERING: "Finding hotels",
  RANKING: "Ranking the cheapest options",
  CALLING_FOR_QUOTES: "Calling hotels for quotes",
  OFFER_SELECTED: "Best offer chosen",
  BOOKING: "Booking the room",
  VERIFYING: "Verifying the details",
  EMAIL_PENDING: "Emailing your report",
  COMPLETED: "Done",
  INVALID_REQUEST: "The request was invalid",
  OUT_OF_SCOPE_LOCATION: "City is outside Ontario",
  NO_CANDIDATES: "No suitable hotels found",
  NO_VALID_OFFER: "No usable quote received",
  BOOKING_UNCERTAIN: "Booking status uncertain",
  BOOKING_FAILED: "Booking failed",
  EMAIL_FAILED: "Email could not be sent",
  DEADLINE_EXCEEDED: "Deadline reached",
};

export function statusLabel(status: string): string {
  return STATUS_LABELS[status as TaskStatus] ?? humanize(status);
}

const OUTCOME_LABELS: Record<string, string> = {
  ENQUIRY_COMPLETED: "Enquiry completed (not a booking)",
  QUOTE_RECEIVED: "Quote received (not a booking)",
  BOOKING_CONFIRMED: "Booking confirmed by the hotel",
  NO_ELIGIBLE_HOTELS: "No eligible hotels",
  NO_VALID_QUOTE: "No valid quote",
  FOLLOW_UP_REQUIRED: "Follow-up required",
  CALL_FAILED: "Calls failed",
  BOOKING_UNCERTAIN: "Booking status uncertain",
  EMAIL_FAILED: "Email failed",
  DEADLINE_EXCEEDED: "Deadline exceeded",
};

export function outcomeLabel(outcome: TaskOutcome): string {
  return OUTCOME_LABELS[outcome] ?? humanize(outcome);
}

/** Tax status line for an offer: included, excluded or unknown. */
export function taxInclusionLabel(value: TaxInclusion | null): string {
  switch (value?.toUpperCase()) {
    case "INCLUDED":
      return "Taxes included";
    case "EXCLUDED":
      return "Taxes not included";
    default:
      return "Tax status unknown";
  }
}
