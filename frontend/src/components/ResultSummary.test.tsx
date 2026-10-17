import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { isConfirmedBooking, ResultSummary } from "@/components/ResultSummary";
import type { TaskRequest } from "@/models/task";
import { makeOffer, makeTask } from "@/test/fixtures";

const BOOKING_WORDS = /\b(booked|reservation confirmed|confirmation number)\b/i;

describe("ResultSummary", () => {
  it("reports a finished search with no eligible hotels honestly", () => {
    render(<ResultSummary task={makeTask()} />);

    expect(screen.getByText("Result")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "No eligible hotels found" })).toBeInTheDocument();
    expect(screen.getByText("Try different or more flexible dates.")).toBeInTheDocument();
    expect(screen.getByText("No eligible hotels")).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(BOOKING_WORDS);
  });

  it("presents the best quote as a quote, not a booking", () => {
    const best = makeOffer();
    render(<ResultSummary task={makeTask({ outcome: "ENQUIRY_COMPLETED", bestOffer: best, offers: [best], callsPlaced: 2 })} />);

    expect(screen.getByRole("heading", { name: /^Cheapest confirmed-available: CAD\s366\.00 all-in at Rideau Inn$/ })).toBeInTheDocument();
    expect(screen.getByText(/This is a quote, not a booking\. Nothing has been reserved\./)).toBeInTheDocument();
    expect(screen.getByText("Enquiry completed (not a booking)")).toBeInTheDocument();
  });

  it("says the result went to the organizer inbox when the prompt had no email", () => {
    const best = makeOffer();
    const request: TaskRequest = {
      city: "Barrie",
      checkIn: null,
      checkOut: null,
      adults: null,
      children: null,
      rooms: null,
      maxBudget: null,
      amenities: [],
      email: "organizer@example.org",
      emailSource: "ORGANIZER_FALLBACK",
      originalPrompt: "cheapest hotel in Barrie",
    };

    const { unmount } = render(<ResultSummary task={makeTask({ outcome: "QUOTE_RECEIVED", bestOffer: best, offers: [best], request })} />);
    expect(screen.getByText(/have been emailed to the organizer inbox\.$/)).toBeInTheDocument();
    expect(screen.queryByText(/emailed to you/)).not.toBeInTheDocument();
    unmount();

    // The report's own emailSource wins when present; STATED means the user's address.
    render(<ResultSummary task={makeTask({ outcome: "NO_VALID_QUOTE", request, report: { subject: null, to: "me@example.com", sentAt: null, deliveryStatus: "ACCEPTED", bodyText: null, emailSource: "STATED" } })} />);
    expect(screen.getByText(/have been emailed to you\.$/)).toBeInTheDocument();
  });

  it("only claims a booking for BOOKING_CONFIRMED", () => {
    const best = makeOffer();
    expect(isConfirmedBooking(makeTask({ outcome: "BOOKING_CONFIRMED" }))).toBe(true);
    for (const outcome of ["ENQUIRY_COMPLETED", "QUOTE_RECEIVED", "FOLLOW_UP_REQUIRED", "BOOKING_UNCERTAIN", null]) {
      expect(isConfirmedBooking(makeTask({ outcome, bestOffer: best })), String(outcome)).toBe(false);
    }

    render(<ResultSummary task={makeTask({ outcome: "BOOKING_CONFIRMED", bestOffer: best, offers: [best] })} />);
    expect(screen.getByRole("heading", { name: "Booking confirmed by the hotel" })).toBeInTheDocument();
  });

  it("shows unknown counts as Unknown, not 0", () => {
    render(<ResultSummary task={makeTask({ status: "DISCOVERING", outcome: null, candidatesScreened: null, callsPlaced: null })} />);

    expect(screen.getByText("So far")).toBeInTheDocument();
    expect(screen.getAllByText("Unknown")).toHaveLength(2);
    expect(screen.getByText("The agent is still working.")).toBeInTheDocument();
  });

  it.each([
    ["NO_CANDIDATES", "No suitable hotels found"],
    ["NO_VALID_OFFER", "No usable quote yet"],
    ["EMAIL_FAILED", "We couldn't send your email"],
    ["DEADLINE_EXCEEDED", "The deadline passed before the search finished"],
    ["OUT_OF_SCOPE_LOCATION", "We only search Ontario"],
    ["BOOKING_UNCERTAIN", "Booking status is uncertain"],
    ["BOOKING_FAILED", "The booking did not go through"],
  ] as const)("titles %s as %s", (status, title) => {
    render(<ResultSummary task={makeTask({ status, outcome: null })} />);
    expect(screen.getByRole("heading", { name: title })).toBeInTheDocument();
  });
});
