import { z } from "zod";

import { DEFAULT_ONTARIO_TIME_ZONE } from "@/models/municipality";
import type { AuthorizationMode, CreateTaskRequest } from "@/models/task";
import { AUTHORIZATION_MODES } from "@/models/task";

/**
 * Raw values of the search form, exactly as the inputs hold them (strings for text, date and
 * number inputs). {@link createSearchFormSchema} validates them and converts them into a
 * {@link CreateTaskRequest} (minus `requestId`).
 */
export interface SearchFormValues {
  city: string;
  checkIn: string;
  checkOut: string;
  rooms: string;
  adults: string;
  children: string;
  destinationEmail: string;
  /** `<input type="datetime-local">` value, in the browser's local time. */
  deadline: string;
  maximumTotalCad: string;
  authorizationMode: AuthorizationMode;
  allowCompetingQuoteDisclosure: boolean;
}

export type SearchFormField = keyof SearchFormValues;

export const SEARCH_FORM_FIELDS = [
  "city",
  "checkIn",
  "checkOut",
  "rooms",
  "adults",
  "children",
  "destinationEmail",
  "deadline",
  "maximumTotalCad",
  "authorizationMode",
  "allowCompetingQuoteDisclosure",
] as const satisfies readonly SearchFormField[];

/** Authorization modes the UI lets the user pick today. */
export const ENABLED_AUTHORIZATION_MODES: readonly AuthorizationMode[] = [
  "ENQUIRY_ONLY",
  "REQUIRE_APPROVAL",
];

export const LIMITS = {
  maxRooms: 9,
  maxAdults: 20,
  maxChildren: 20,
  maxNights: 30,
} as const;

/** Today's date (`YYYY-MM-DD`) in the given IANA time zone. */
export function todayIn(timeZone: string = DEFAULT_ONTARIO_TIME_ZONE, now: Date = new Date()): string {
  // en-CA formats dates as YYYY-MM-DD.
  return new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}

/** Adds days to a `YYYY-MM-DD` date. */
export function addDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + days));
  return date.toISOString().slice(0, 10);
}

/** Whole nights between two `YYYY-MM-DD` dates. */
export function nightsBetween(checkIn: string, checkOut: string): number {
  const toUtc = (s: string) => {
    const [y, m, d] = s.split("-").map(Number);
    return Date.UTC(y, m - 1, d);
  };
  return Math.round((toUtc(checkOut) - toUtc(checkIn)) / 86_400_000);
}

/** Formats a Date as an `<input type="datetime-local">` value in the browser's time zone. */
export function toDateTimeLocalValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

/** Default form values: tomorrow for one night, 1 room, 2 adults, deadline in two hours. */
export function defaultSearchFormValues(now: Date = new Date()): SearchFormValues {
  const today = todayIn(DEFAULT_ONTARIO_TIME_ZONE, now);
  return {
    city: "",
    checkIn: addDays(today, 1),
    checkOut: addDays(today, 2),
    rooms: "1",
    adults: "2",
    children: "0",
    destinationEmail: "",
    deadline: toDateTimeLocalValue(new Date(now.getTime() + 2 * 60 * 60 * 1000)),
    maximumTotalCad: "",
    authorizationMode: "ENQUIRY_ONLY",
    allowCompetingQuoteDisclosure: false,
  };
}

const isoDate = (label: string) =>
  z
    .string()
    .trim()
    .min(1, `Choose a ${label} date`)
    .pipe(z.iso.date({ error: `Enter a valid ${label} date` }));

const wholeNumber = (label: string, min: number, max: number) =>
  z
    .string()
    .trim()
    .min(1, `Enter the number of ${label}`)
    .refine((s) => /^\d+$/.test(s), `Enter a whole number of ${label}`)
    .transform(Number)
    .pipe(
      z
        .number()
        .min(min, `At least ${min} ${label}`)
        .max(max, `At most ${max} ${label}`),
    );

export interface SearchFormSchemaOptions {
  /** IANA time zone of the selected city; "today" is computed there. */
  timeZone?: string;
  /** Injectable clock. */
  now?: Date;
}

/**
 * Validation schema shared by the search form (inline errors) and the submit path (request
 * building). The output is a {@link CreateTaskRequest} without `requestId`.
 */
export function createSearchFormSchema(options: SearchFormSchemaOptions = {}) {
  const now = options.now ?? new Date();
  const today = todayIn(options.timeZone ?? DEFAULT_ONTARIO_TIME_ZONE, now);

  return z
    .object({
      city: z.string().trim().min(1, "Choose an Ontario city").max(120, "City name is too long"),
      checkIn: isoDate("check-in"),
      checkOut: isoDate("check-out"),
      rooms: wholeNumber("rooms", 1, LIMITS.maxRooms),
      adults: wholeNumber("adults", 1, LIMITS.maxAdults),
      children: wholeNumber("children", 0, LIMITS.maxChildren),
      destinationEmail: z
        .string()
        .trim()
        .min(1, "Enter the email that should receive the result")
        .pipe(z.email({ error: "Enter a valid email address" })),
      deadline: z
        .string()
        .trim()
        .min(1, "Choose a deadline")
        .transform((s, ctx) => {
          const date = new Date(s);
          if (Number.isNaN(date.getTime())) {
            ctx.addIssue({ code: "custom", message: "Enter a valid deadline" });
            return z.NEVER;
          }
          return date;
        }),
      maximumTotalCad: z
        .string()
        .trim()
        .transform((s, ctx) => {
          if (s === "") return null;
          const n = Number(s.replace(/[$,\s]/g, ""));
          if (!Number.isFinite(n) || n <= 0) {
            ctx.addIssue({ code: "custom", message: "Enter a positive amount, or leave it empty" });
            return z.NEVER;
          }
          return Math.round(n * 100) / 100;
        }),
      authorizationMode: z
        .enum(AUTHORIZATION_MODES)
        .refine((m) => ENABLED_AUTHORIZATION_MODES.includes(m), "Booking automatically is coming soon"),
      allowCompetingQuoteDisclosure: z.boolean(),
    })
    // Cross-field rules. Zod still runs this when another field failed, in which case that
    // field may hold its raw (unparsed) value, so every rule checks its inputs' types first.
    .superRefine((v, ctx) => {
      const isDate = (s: unknown): s is string => typeof s === "string" && /^\d{4}-\d{2}-\d{2}$/.test(s);
      if (isDate(v.checkIn) && v.checkIn < today) {
        ctx.addIssue({ code: "custom", path: ["checkIn"], message: "Check-in can't be in the past" });
      }
      if (isDate(v.checkIn) && isDate(v.checkOut)) {
        if (v.checkOut <= v.checkIn) {
          ctx.addIssue({ code: "custom", path: ["checkOut"], message: "Check-out must be after check-in" });
        } else if (nightsBetween(v.checkIn, v.checkOut) > LIMITS.maxNights) {
          ctx.addIssue({
            code: "custom",
            path: ["checkOut"],
            message: `Stays are limited to ${LIMITS.maxNights} nights`,
          });
        }
      }
      if (typeof v.adults === "number" && typeof v.rooms === "number" && v.adults < v.rooms) {
        ctx.addIssue({ code: "custom", path: ["adults"], message: "Each room needs at least one adult" });
      }
      if (v.deadline instanceof Date && v.deadline.getTime() <= now.getTime()) {
        ctx.addIssue({ code: "custom", path: ["deadline"], message: "The deadline must be in the future" });
      }
    })
    .transform(
      (v): Omit<CreateTaskRequest, "requestId"> => ({
        city: v.city,
        checkIn: v.checkIn,
        checkOut: v.checkOut,
        rooms: v.rooms,
        adults: v.adults,
        children: v.children,
        destinationEmail: v.destinationEmail,
        deadline: v.deadline.toISOString(),
        requiredAmenities: [],
        authorizationMode: v.authorizationMode,
        maximumTotalCad: v.maximumTotalCad,
        allowCompetingQuoteDisclosure: v.allowCompetingQuoteDisclosure,
      }),
    );
}

export type SearchFormErrors = Partial<Record<SearchFormField, string>>;

export type SearchFormResult =
  | { success: true; request: Omit<CreateTaskRequest, "requestId"> }
  | { success: false; errors: SearchFormErrors };

/** Validates form values; on failure returns the first message per field. */
export function validateSearchForm(
  values: SearchFormValues,
  options?: SearchFormSchemaOptions,
): SearchFormResult {
  const result = createSearchFormSchema(options).safeParse(values);
  if (result.success) return { success: true, request: result.data };

  const errors: SearchFormErrors = {};
  for (const issue of result.error.issues) {
    const field = issue.path[0];
    if (typeof field === "string" && (SEARCH_FORM_FIELDS as readonly string[]).includes(field)) {
      errors[field as SearchFormField] ??= issue.message;
    }
  }
  return { success: false, errors };
}
