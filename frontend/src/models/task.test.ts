import { describe, expect, it } from "vitest";

import { isProblemDetail } from "@/models/problem-detail";
import {
  isProblemStatus,
  isTerminalStatus,
  MAIN_PATH_STATUSES,
  TASK_STATUSES,
  TERMINAL_STATUSES,
} from "@/models/task";

describe("task statuses", () => {
  it("mirror the backend TaskStatus enum", () => {
    expect(TASK_STATUSES).toHaveLength(18);
    expect(new Set(TASK_STATUSES).size).toBe(TASK_STATUSES.length);
  });

  it("treat exactly the backend's terminal statuses as terminal", () => {
    // Backend TaskStatus.isTerminal(): no outgoing transitions.
    expect([...TERMINAL_STATUSES].sort()).toEqual(
      ["COMPLETED", "DEADLINE_EXCEEDED", "EMAIL_FAILED", "INVALID_REQUEST", "OUT_OF_SCOPE_LOCATION"].sort(),
    );
    expect(TASK_STATUSES.filter(isTerminalStatus)).toEqual(
      TASK_STATUSES.filter((s) => (TERMINAL_STATUSES as readonly string[]).includes(s)),
    );
  });

  it("keep listening after failure states that still send an email", () => {
    for (const status of ["NO_CANDIDATES", "NO_VALID_OFFER", "BOOKING_FAILED", "BOOKING_UNCERTAIN"]) {
      expect(isTerminalStatus(status), status).toBe(false);
      expect(isProblemStatus(status), status).toBe(true);
    }
  });

  it("do not treat the happy path as a problem", () => {
    expect(MAIN_PATH_STATUSES.filter(isProblemStatus)).toEqual([]);
    expect(isTerminalStatus("SOMETHING_NEW")).toBe(false);
    expect(isProblemStatus("SOMETHING_NEW")).toBe(false);
  });
});

describe("isProblemDetail", () => {
  it("accepts RFC 9457 bodies and their extensions", () => {
    expect(isProblemDetail({ title: "Invalid request" })).toBe(true);
    expect(isProblemDetail({ detail: "x" })).toBe(true);
    expect(isProblemDetail({ status: 422 })).toBe(true);
    expect(isProblemDetail({ errors: [] })).toBe(true);
    expect(isProblemDetail({ suggestions: ["Ottawa"] })).toBe(true);
  });

  it("rejects anything else", () => {
    expect(isProblemDetail(null)).toBe(false);
    expect(isProblemDetail("Bad Request")).toBe(false);
    expect(isProblemDetail([{ title: "x" }])).toBe(false);
    expect(isProblemDetail({ status: "422" })).toBe(false);
    expect(isProblemDetail({ message: "oops" })).toBe(false);
  });
});
