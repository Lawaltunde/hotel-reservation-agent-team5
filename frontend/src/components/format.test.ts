import { describe, expect, it } from "vitest";

import {
  formatInstant,
  formatMoney,
  formatStayDate,
  formatTime,
  humanize,
  outcomeLabel,
  statusLabel,
  STATUS_LABELS,
  taxInclusionLabel,
  UNKNOWN,
} from "@/components/format";
import { TASK_STATUSES } from "@/models/task";

describe("formatMoney", () => {
  it("formats CAD with the currency code", () => {
    expect(formatMoney(1234.5)).toMatch(/^CAD\s1,234\.50$/);
    expect(formatMoney(0)).toMatch(/^CAD\s0\.00$/);
  });

  it("prints Unknown, never 0, for missing amounts", () => {
    expect(formatMoney(null)).toBe(UNKNOWN);
    expect(formatMoney(undefined)).toBe(UNKNOWN);
    expect(formatMoney(Number.NaN)).toBe(UNKNOWN);
  });

  it("falls back to a plain format for an invalid currency code", () => {
    expect(formatMoney(10, "NOT A CODE")).toBe("NOT A CODE 10.00");
  });
});

describe("dates", () => {
  it("formats stay dates as calendar dates regardless of time zone", () => {
    expect(formatStayDate("2026-11-20")).toMatch(/Fri.*Nov.*20.*2026/);
    expect(formatStayDate(null)).toBe(UNKNOWN);
    expect(formatStayDate("garbage")).toBe("garbage");
  });

  it("handles missing or invalid instants", () => {
    expect(formatInstant(null)).toBe(UNKNOWN);
    expect(formatInstant("not a date")).toBe("not a date");
    expect(formatInstant("2026-11-10T18:00:00Z")).toMatch(/2026/);
    expect(formatTime(null)).toBe("");
    expect(formatTime("nope")).toBe("");
  });
});

describe("labels", () => {
  it("has a label for every status", () => {
    expect(Object.keys(STATUS_LABELS).sort()).toEqual([...TASK_STATUSES].sort());
    expect(statusLabel("NO_CANDIDATES")).toBe("No suitable hotels found");
    expect(statusLabel("SOMETHING_NEW")).toBe("Something new");
  });

  it("never words a quote or enquiry as a booking", () => {
    expect(outcomeLabel("ENQUIRY_COMPLETED")).toBe("Enquiry completed (not a booking)");
    expect(outcomeLabel("QUOTE_RECEIVED")).toBe("Quote received (not a booking)");
    expect(outcomeLabel("NO_ELIGIBLE_HOTELS")).toBe("No eligible hotels");
    expect(outcomeLabel("BRAND_NEW")).toBe("Brand new");
  });

  it("describes tax inclusion, defaulting to unknown", () => {
    expect(taxInclusionLabel("INCLUDED")).toBe("Taxes included");
    expect(taxInclusionLabel("excluded")).toBe("Taxes not included");
    expect(taxInclusionLabel("UNKNOWN")).toBe("Tax status unknown");
    expect(taxInclusionLabel(null)).toBe("Tax status unknown");
  });

  it("humanizes enum names", () => {
    expect(humanize("REFERENCE_ID_GIVEN")).toBe("Reference id given");
  });
});
