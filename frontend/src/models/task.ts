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

/** Body of `POST /api/tasks`. */
export interface CreateTaskRequest {
  /** Client-generated UUID; the same value is reused when a submission is retried. */
  requestId: string;
  city: string;
  /** `YYYY-MM-DD`, hotel local date. */
  checkIn: string;
  /** `YYYY-MM-DD`, hotel local date. */
  checkOut: string;
  rooms: number;
  adults: number;
  children: number;
  destinationEmail: string;
  /** ISO-8601 instant. */
  deadline: string;
  requiredAmenities: string[];
  authorizationMode: AuthorizationMode;
  maximumTotalCad: number | null;
  allowCompetingQuoteDisclosure: boolean;
}

/** `202` response of `POST /api/tasks`. */
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
   * The comparable all-in total (room + HST + MAT + mandatory fees) used to rank offers.
   * `null` when it cannot be established; such an offer is never called cheapest.
   */
  negotiatedTotal: number | null;
  /** The all-in total as the hotel stated it on the call. Optional until the backend sends it. */
  allInTotal?: number | null;
  /** True when the summed components and the stated all-in total differ by more than CA$1. */
  totalsDisagree?: boolean | null;
  currency: "CAD" | (string & {});
  hst: number | null;
  municipalAccommodationTax: number | null;
  mandatoryFees: number | null;
  /** Removable charges; never part of `negotiatedTotal`. Optional until the backend sends it. */
  optionalFees?: number | null;
  taxInclusion: TaxInclusion | null;
  cancellationTerms: string | null;
  depositTerms: string | null;
  /** The hotel's enquiry/quote reference. This is NOT a booking confirmation. */
  enquiryId: string | null;
  /** ISO-8601 instant: call end plus the period the hotel stated; `null` when no period was given. */
  quoteValidUntil: string | null;
  /** The hotel's own words about how long the quote holds, verbatim from the call. */
  quoteValidityText?: string | null;
  evidenceStatus: string | null;
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
}

/** Data of an SSE `progress` event on `GET /api/tasks/{id}/events`. */
export interface ProgressEvent {
  taskId: string;
  status: TaskStatus;
  message: string | null;
  /** ISO-8601 instant. */
  at: string;
}
