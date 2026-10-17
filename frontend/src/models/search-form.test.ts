import { describe, expect, it } from "vitest";

import {
  addDays,
  defaultSearchFormValues,
  nightsBetween,
  todayIn,
  toDateTimeLocalValue,
  validateSearchForm,
  type SearchFormValues,
} from "@/models/search-form";

// 2026-11-02 03:30 UTC is still 2026-11-01 in Toronto (UTC-5) and Winnipeg (UTC-6).
const NOW = new Date("2026-11-02T03:30:00Z");

function values(overrides: Partial<SearchFormValues> = {}): SearchFormValues {
  return {
    city: "Ottawa",
    checkIn: "2026-11-20",
    checkOut: "2026-11-22",
    rooms: "1",
    adults: "2",
    children: "0",
    destinationEmail: "traveller@example.com",
    deadline: "2026-11-10T18:00",
    maximumTotalCad: "",
    authorizationMode: "ENQUIRY_ONLY",
    allowCompetingQuoteDisclosure: false,
    ...overrides,
  };
}

function errorsOf(v: SearchFormValues, options = { now: NOW }) {
  const result = validateSearchForm(v, options);
  if (result.success) throw new Error("expected validation to fail");
  return result.errors;
}

describe("date helpers", () => {
  it("computes today in the city's time zone, not UTC", () => {
    expect(todayIn("America/Toronto", NOW)).toBe("2026-11-01");
    expect(todayIn("America/Winnipeg", NOW)).toBe("2026-11-01");
    expect(todayIn("UTC", NOW)).toBe("2026-11-02");
  });

  it("adds days across month and year ends", () => {
    expect(addDays("2026-11-30", 1)).toBe("2026-12-01");
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
    expect(addDays("2028-02-28", 1)).toBe("2028-02-29");
    expect(addDays("2026-11-02", -2)).toBe("2026-10-31");
  });

  it("counts whole nights, unaffected by daylight saving", () => {
    expect(nightsBetween("2026-10-31", "2026-11-02")).toBe(2);
    expect(nightsBetween("2026-11-20", "2026-11-20")).toBe(0);
    expect(nightsBetween("2026-11-22", "2026-11-20")).toBe(-2);
  });

  it("formats a datetime-local value in local time", () => {
    const local = new Date(2026, 10, 2, 9, 5);
    expect(toDateTimeLocalValue(local)).toBe("2026-11-02T09:05");
  });
});

describe("defaultSearchFormValues", () => {
  it("defaults to tomorrow for one night, one room, two adults, enquiry only", () => {
    const defaults = defaultSearchFormValues(NOW);
    expect(defaults).toMatchObject({
      city: "",
      checkIn: "2026-11-02",
      checkOut: "2026-11-03",
      rooms: "1",
      adults: "2",
      children: "0",
      destinationEmail: "",
      maximumTotalCad: "",
      authorizationMode: "ENQUIRY_ONLY",
      allowCompetingQuoteDisclosure: false,
    });
    expect(new Date(defaults.deadline).getTime() - NOW.getTime()).toBeGreaterThan(119 * 60_000);
  });

  it("is valid once city and email are filled in", () => {
    const filled = { ...defaultSearchFormValues(NOW), city: "Ottawa", destinationEmail: "a@example.com" };
    expect(validateSearchForm(filled, { now: NOW }).success).toBe(true);
  });
});

describe("validateSearchForm", () => {
  it("builds the API request from valid values", () => {
    const result = validateSearchForm(
      values({ city: "  Ottawa ", rooms: " 2 ", adults: "3", children: "1", maximumTotalCad: "$1,234.567" }),
      { now: NOW },
    );
    expect(result).toEqual({
      success: true,
      request: {
        city: "Ottawa",
        checkIn: "2026-11-20",
        checkOut: "2026-11-22",
        rooms: 2,
        adults: 3,
        children: 1,
        destinationEmail: "traveller@example.com",
        deadline: new Date("2026-11-10T18:00").toISOString(),
        requiredAmenities: [],
        authorizationMode: "ENQUIRY_ONLY",
        maximumTotalCad: 1234.57,
        allowCompetingQuoteDisclosure: false,
      },
    });
  });

  it("sends no budget when the field is empty", () => {
    const result = validateSearchForm(values({ maximumTotalCad: "   " }), { now: NOW });
    expect(result.success && result.request.maximumTotalCad).toBeNull();
  });

  it("requires every mandatory field", () => {
    const errors = errorsOf(
      values({ city: "", checkIn: "", checkOut: "", rooms: "", adults: "", children: "", destinationEmail: "", deadline: "" }),
    );
    expect(errors).toEqual({
      city: "Choose an Ontario city",
      checkIn: "Choose a check-in date",
      checkOut: "Choose a check-out date",
      rooms: "Enter the number of rooms",
      adults: "Enter the number of adults",
      children: "Enter the number of children",
      destinationEmail: "Enter the email that should receive the result",
      deadline: "Choose a deadline",
    });
  });

  it("rejects a check-in before today in the city's time zone", () => {
    expect(errorsOf(values({ checkIn: "2026-10-31", checkOut: "2026-11-02" })).checkIn).toBe(
      "Check-in can't be in the past",
    );
    // Today in Toronto is still allowed even though it is already tomorrow in UTC.
    expect(validateSearchForm(values({ checkIn: "2026-11-01", checkOut: "2026-11-02" }), { now: NOW }).success).toBe(true);
  });

  it("requires check-out after check-in and at most 30 nights", () => {
    expect(errorsOf(values({ checkOut: "2026-11-20" })).checkOut).toBe("Check-out must be after check-in");
    expect(errorsOf(values({ checkOut: "2026-12-21" })).checkOut).toBe("Stays are limited to 30 nights");
    expect(validateSearchForm(values({ checkOut: "2026-12-20" }), { now: NOW }).success).toBe(true);
  });

  it("rejects malformed dates", () => {
    expect(errorsOf(values({ checkIn: "20/11/2026" })).checkIn).toBe("Enter a valid check-in date");
  });

  it("enforces guest and room limits with whole numbers only", () => {
    expect(errorsOf(values({ rooms: "0" })).rooms).toBe("At least 1 rooms");
    expect(errorsOf(values({ rooms: "10", adults: "10" })).rooms).toBe("At most 9 rooms");
    expect(errorsOf(values({ adults: "1.5" })).adults).toBe("Enter a whole number of adults");
    expect(errorsOf(values({ children: "-1" })).children).toBe("Enter a whole number of children");
    expect(errorsOf(values({ children: "21" })).children).toBe("At most 20 children");
  });

  it("needs at least one adult per room", () => {
    expect(errorsOf(values({ rooms: "3", adults: "2" })).adults).toBe("Each room needs at least one adult");
  });

  it("rejects an invalid email", () => {
    expect(errorsOf(values({ destinationEmail: "not-an-email" })).destinationEmail).toBe("Enter a valid email address");
  });

  it("requires a future deadline", () => {
    expect(errorsOf(values({ deadline: "2026-11-01T00:00" })).deadline).toBe("The deadline must be in the future");
    expect(errorsOf(values({ deadline: "soon" })).deadline).toBe("Enter a valid deadline");
  });

  it("rejects a non-positive or non-numeric budget", () => {
    expect(errorsOf(values({ maximumTotalCad: "0" })).maximumTotalCad).toBe("Enter a positive amount, or leave it empty");
    expect(errorsOf(values({ maximumTotalCad: "abc" })).maximumTotalCad).toBe("Enter a positive amount, or leave it empty");
  });

  it("does not allow automatic booking yet", () => {
    expect(errorsOf(values({ authorizationMode: "BOOK_IF_WITHIN_LIMITS" })).authorizationMode).toBe(
      "Booking automatically is coming soon",
    );
    expect(validateSearchForm(values({ authorizationMode: "REQUIRE_APPROVAL" }), { now: NOW }).success).toBe(true);
  });

  it("reports only the first message per field", () => {
    const errors = errorsOf(values({ checkIn: "2026-10-01", checkOut: "2026-09-01" }));
    expect(Object.keys(errors).sort()).toEqual(["checkIn", "checkOut"]);
  });
});
