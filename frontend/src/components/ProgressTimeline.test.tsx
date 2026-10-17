import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ProgressTimeline } from "@/components/ProgressTimeline";
import type { ProgressEvent, TaskStatus } from "@/models/task";

function event(status: TaskStatus, message: string | null = null): ProgressEvent {
  return { taskId: "t", status, message, at: "2026-11-02T12:00:00Z" };
}

function steps() {
  return within(screen.getAllByRole("list")[0]).getAllByRole("listitem");
}

describe("ProgressTimeline", () => {
  it("hides the booking step for enquiry-only tasks", () => {
    render(<ProgressTimeline status="DISCOVERING" authorizationMode="ENQUIRY_ONLY" events={[]} mode="live" />);

    expect(screen.queryByText(/Booking the room/)).not.toBeInTheDocument();
    expect(steps()).toHaveLength(9);
  });

  it("shows the booking step when the user allowed booking", () => {
    render(<ProgressTimeline status="DISCOVERING" authorizationMode="REQUIRE_APPROVAL" events={[]} mode="live" />);
    expect(screen.getByText(/Booking the room/)).toBeInTheDocument();
  });

  it("marks done, current and upcoming steps", () => {
    render(
      <ProgressTimeline
        status="RANKING"
        authorizationMode="ENQUIRY_ONLY"
        events={[event("DISCOVERING", "Searching the Ontario hotel index"), event("RANKING")]}
        mode="live"
      />,
    );

    const current = steps().find((li) => li.getAttribute("aria-current") === "step");
    expect(current).toHaveTextContent("Ranking the cheapest options (in progress)");
    expect(screen.getByText(/Finding hotels/)).toHaveTextContent("(done)");
    expect(screen.getByText(/Emailing your report/)).toHaveTextContent("(not started)");
    expect(screen.getByText("Live updates")).toBeInTheDocument();
  });

  it("adds the problem as the current step", () => {
    render(
      <ProgressTimeline
        status="NO_CANDIDATES"
        authorizationMode="ENQUIRY_ONLY"
        events={[event("DISCOVERING"), event("NO_CANDIDATES", "No callable hotels found")]}
        mode="live"
      />,
    );

    const current = steps().filter((li) => li.getAttribute("aria-current") === "step");
    expect(current).toHaveLength(1);
    expect(current[0]).toHaveTextContent("No suitable hotels found");
  });

  it("marks every step done once completed", () => {
    render(
      <ProgressTimeline
        status="COMPLETED"
        authorizationMode="ENQUIRY_ONLY"
        events={[event("NO_CANDIDATES"), event("EMAIL_PENDING"), event("COMPLETED")]}
        mode="closed"
      />,
    );

    expect(steps().every((li) => li.textContent?.includes("(done)"))).toBe(true);
    expect(screen.getByText("Finished")).toBeInTheDocument();
  });

  it("logs every event, using the status label when there is no message", () => {
    render(
      <ProgressTimeline
        status="EMAIL_PENDING"
        authorizationMode="ENQUIRY_ONLY"
        events={[event("NO_CANDIDATES", "No callable hotels found"), event("EMAIL_PENDING")]}
        mode="polling"
      />,
    );

    const log = screen.getByRole("log", { name: "Agent activity" });
    expect(within(log).getAllByRole("listitem").map((li) => li.textContent)).toEqual([
      expect.stringContaining("No callable hotels found"),
      expect.stringContaining("Emailing your report"),
    ]);
    expect(screen.getByText("Checking every few seconds")).toBeInTheDocument();
  });

  it("shows a placeholder before the first event", () => {
    render(<ProgressTimeline status={null} authorizationMode={null} events={[]} mode="connecting" />);
    expect(screen.getByText("Waiting for the first update…")).toBeInTheDocument();
    expect(screen.getByText("Connecting…")).toBeInTheDocument();
  });
});
