/**
 * Types for the booking-task API (`/api/tasks`). Field names match the backend JSON exactly.
 * Unknown values are `null`, never 0 or an empty string.
 */

/** Every status a task can be in (README §6). */
export const TASK_STATUSES = [
  "RECEIVED",
  "VALIDATING",
  "DISCOVERING",
  "RANKING",
  "CALLING_FOR_QUOTES",
  "OFFER_SELECTED",
  "BOOKING",
  "VERIFYING",
  "EMAIL_PENDING",
  "COMPLETED",
  "INVALID_REQUEST",
  "OUT_OF_SCOPE_LOCATION",
  "NO_CANDIDATES",
  "NO_VALID_OFFER",
  "BOOKING_UNCERTAIN",
  "BOOKING_FAILED",
  "EMAIL_FAILED",
  "DEADLINE_EXCEEDED",
] as const;

export type TaskStatus = (typeof TASK_STATUSES)[number];

/** The happy path, in order. BOOKING only happens when the user authorized a booking. */
export const MAIN_PATH_STATUSES = [
  "RECEIVED",
  "VALIDATING",
  "DISCOVERING",
  "RANKING",
  "CALLING_FOR_QUOTES",
  "OFFER_SELECTED",
  "BOOKING",
  "VERIFYING",
  "EMAIL_PENDING",
  "COMPLETED",
] as const satisfies readonly TaskStatus[];

/** Statuses after which the backend sends no further progress; the client stops listening. */
export const TERMINAL_STATUSES = [
  "COMPLETED",
  "EMAIL_FAILED",
  "DEADLINE_EXCEEDED",
  "INVALID_REQUEST",
  "OUT_OF_SCOPE_LOCATION",
] as const satisfies readonly TaskStatus[];

/** Statuses that mean something went wrong or no usable result exists (may still be followed by an email step). */
export const PROBLEM_STATUSES = [
  "INVALID_REQUEST",
  "OUT_OF_SCOPE_LOCATION",
  "NO_CANDIDATES",
  "NO_VALID_OFFER",
  "BOOKING_UNCERTAIN",
  "BOOKING_FAILED",
  "EMAIL_FAILED",
  "DEADLINE_EXCEEDED",
] as const satisfies readonly TaskStatus[];

export function isTerminalStatus(status: string): boolean {
  return (TERMINAL_STATUSES as readonly string[]).includes(status);
}

export function isProblemStatus(status: string): boolean {
  return (PROBLEM_STATUSES as readonly string[]).includes(status);
}

export const AUTHORIZATION_MODES = [
  "ENQUIRY_ONLY",
  "REQUIRE_APPROVAL",
  "BOOK_IF_WITHIN_LIMITS",
] as const;

export type AuthorizationMode = (typeof AUTHORIZATION_MODES)[number];

/** Task outcomes (README §9). Kept open for values the backend may add. */
export type TaskOutcome =
  | "ENQUIRY_COMPLETED"
  | "QUOTE_RECEIVED"
  | "BOOKING_CONFIRMED"
  | "NO_ELIGIBLE_HOTELS"
  | "NO_VALID_QUOTE"
  | "FOLLOW_UP_REQUIRED"
  | "CALL_FAILED"
  | "BOOKING_UNCERTAIN"
  | "EMAIL_FAILED"
  | "DEADLINE_EXCEEDED"
  | (string & {});

/** Whether the quoted total includes taxes. Kept open for values the backend may add. */
export type TaxInclusion = "INCLUDED" | "EXCLUDED" | "UNKNOWN" | (string & {});

/** Body of `POST /api/tasks/prompt`: the user's one free-text request. */
export interface CreatePromptTaskRequest {
  prompt: string;
}

/** `202` response of `POST /api/tasks/prompt`. */
export interface CreateTaskResponse {
  taskId: string;
  status: TaskStatus;
}

/** A quote captured from one hotel call. Amounts are CAD; `null` means unknown. */
export interface Offer {
  offerId: string;
  hotelId: string | null;
  hotelName: string | null;
  hotelAddress: string | null;
  roomType: string | null;
  initialQuotedTotal: number | null;
  /** Room charge for the whole stay before taxes and fees. Optional until the backend sends it. */
  roomSubtotal?: number | null;
  /**
   * The comparable all-in total (room + sales taxes + levies + mandatory fees) used to rank offers.
   * `null` when it cannot be established; such an offer is never called cheapest.
   */
  negotiatedTotal: number | null;
  /** The all-in total as the hotel stated it on the call. Optional until the backend sends it. */
  allInTotal?: number | null;
  /** True when the summed components and the stated all-in total differ by more than CA$1. */
  totalsDisagree?: boolean | null;
  currency: "CAD" | (string & {});
  /** Sales tax for the stay (GST/PST/QST/HST by province). The backend may send this or `hst`. */
  salesTax?: number | null;
  /** The backend's name for the sales tax, e.g. "GST 5% + QST 9.975%"; "Sales tax" when absent. */
  salesTaxLabel?: string | null;
  /** Accommodation levies for the stay. The backend may send this or `municipalAccommodationTax`. */
  accommodationLevy?: number | null;
  /** e.g. "Municipal accommodation tax 4%"; "Accommodation levy" when absent. */
  levyLabel?: string | null;
  hst?: number | null;
  municipalAccommodationTax?: number | null;
  mandatoryFees: number | null;
  /** Removable charges; never part of `negotiatedTotal`. Optional until the backend sends it. */
  optionalFees?: number | null;
  taxInclusion: TaxInclusion | null;
  cancellationTerms: string | null;
  depositTerms: string | null;
  /** The price offers are ranked by: the all-in total, or an estimate when `estimated` is true. */
  price?: number | null;
  /** True when `price` is an estimate (sales tax added to a pre-tax rate), not a figure the hotel stated. */
  estimated?: boolean | null;
  priceBasis?: "SUM_OF_STATED_PARTS" | "STATED_ALL_IN" | "ESTIMATED_HST" | (string & {}) | null;
  /** The hotel's enquiry/quote reference. This is NOT a booking confirmation. */
  enquiryId: string | null;
  /** ISO-8601 instant: call end plus the period the hotel stated; `null` when no period was given. */
  quoteValidUntil: string | null;
  /** The hotel's own words about how long the quote holds, verbatim from the call. */
  quoteValidityText?: string | null;
  evidenceStatus: string | null;
}

/**
 * What the backend extracted from the prompt (`TaskRequestResponse`). Only the city is required;
 * a missing email falls back to the organizer inbox and missing dates or guests are marked unstated.
 */
export interface TaskRequest {
  city: string | null;
  /** Two-letter province or territory code, e.g. `NS`, `QC`. */
  province?: string | null;
  /** e.g. "Nova Scotia". Preferred over `province` for display. */
  provinceName?: string | null;
  /** `YYYY-MM-DD`, hotel local date; null when the user didn't say. */
  checkIn: string | null;
  checkOut: string | null;
  adults: number | null;
  children: number | null;
  rooms: number | null;
  /** Maximum all-in for the whole stay, in CAD (a nightly budget is converted server-side). */
  maxBudget: number | null;
  /** The nightly amount, only when the user said "per night". */
  maxBudgetPerNight?: number | null;
  amenities: string[] | null;
  /** Free-text preferences, e.g. "quiet room, near downtown". */
  notes?: string | null;
  /** Where results go. With `ORGANIZER_FALLBACK` this is the organizer inbox, not the user's. */
  email: string | null;
  emailSource?: "STATED" | "ORGANIZER_FALLBACK" | (string & {}) | null;
  /** The prompt exactly as the user sent it. */
  originalPrompt: string | null;
  /** Whether the user gave each field. Unstated fields are null; never show a default as their choice. */
  stated?: Partial<Record<StatableField, boolean>> | null;
  /** What the voice agent actually asks hotels for; null means it asks neutrally. */
  askedFor?: Partial<Record<"checkIn" | "checkOut" | "rooms" | "adults" | "children", string | number | null>> | null;
  /** Human-readable notes on what the agent assumed, e.g. "No dates given: asking hotels for tonight, one night". */
  assumptions?: string[] | null;
}

export type StatableField = "checkIn" | "checkOut" | "adults" | "children" | "rooms" | "maxBudget" | "email";

/** Key facts of the result email, shown prominently above the body. Any of them may be unknown. */
export interface ReportFields {
  hotelName?: string | null;
  hotelAddress?: string | null;
  hotelPhone?: string | null;
  /** The hotel's reference number, only if one was given. Never a booking. */
  reservationNumber?: string | null;
  checkIn?: string | null;
  checkOut?: string | null;
  price?: number | null;
  currency?: string | null;
  /** e.g. "all-in, taxes included (quoted, not booked)". */
  priceNote?: string | null;
  recordingUrl?: string | null;
  conversationUrl?: string | null;
}

/** The result email exactly as sent (`report` on `GET /api/tasks/{id}`). */
export interface TaskReport {
  subject: string | null;
  from?: string | null;
  to: string | null;
  cc?: string[] | null;
  /** Same meaning as `request.emailSource`; preferred when present. */
  emailSource?: "STATED" | "ORGANIZER_FALLBACK" | (string & {}) | null;
  /** ISO-8601 instant; null if the mail provider hasn't accepted it. */
  sentAt: string | null;
  deliveryStatus: "PENDING" | "ACCEPTED" | "DELIVERED" | "DELAYED" | "BOUNCED" | "FAILED" | (string & {}) | null;
  /** Exactly what was sent. */
  bodyText: string | null;
  /** Rendered only inside a sandboxed iframe, never injected into the page. */
  bodyHtml?: string | null;
  fields?: ReportFields | null;
  tokensSpent?: number | null;
  tokens?: { openAiPromptTokens?: number | null; openAiCompletionTokens?: number | null; elevenLabsCallSeconds?: number | null } | null;
  /** One per call, shown collapsed. */
  transcripts?: ReportTranscript[] | null;
  /**
   * Files attached to the email, in attachment order (the chosen hotel is call-1): a recording and a
   * transcript per call. `[]` when nothing was attached; optional only for older backends.
   */
  attachments?: ReportAttachment[] | null;
}

/** One email attachment. The bytes aren't stored, so the UI lists it without a download. */
export interface ReportAttachment {
  fileName: string;
  contentType: string;
  sizeBytes: number;
}

/** A call transcript. Field names are tolerant until the backend confirms them. */
export interface ReportTranscript {
  hotelName?: string | null;
  conversationId?: string | null;
  conversationUrl?: string | null;
  recordingUrl?: string | null;
  startedAt?: string | null;
  text?: string | null;
}

/** `GET /api/tasks/{id}`. */
export interface Task {
  taskId: string;
  status: TaskStatus;
  outcome: TaskOutcome | null;
  city: string;
  checkIn: string;
  checkOut: string;
  rooms: number;
  adults: number;
  children: number;
  authorizationMode: AuthorizationMode;
  deadline: string;
  candidatesScreened: number | null;
  callsPlaced: number | null;
  bestOffer: Offer | null;
  offers: Offer[];
  createdAt: string;
  updatedAt: string;
  /** How the prompt was interpreted. Optional until the backend sends it. */
  request?: TaskRequest | null;
  /** The result email, once composed. Optional until the backend sends it. */
  report?: TaskReport | null;
}

/** Data of an SSE `progress` event on `GET /api/tasks/{id}/events`. */
export interface ProgressEvent {
  taskId: string;
  status: TaskStatus;
  message: string | null;
  /** ISO-8601 instant. */
  at: string;
}
