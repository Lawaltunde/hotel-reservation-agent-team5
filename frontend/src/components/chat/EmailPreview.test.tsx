import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { EmailPreview } from "@/components/chat/EmailPreview";
import type { TaskReport } from "@/models/task";

function makeReport(overrides: Partial<TaskReport> = {}): TaskReport {
  return {
    subject: "Team # 5 - Hotel Reservation Confirmed!",
    to: "me@example.com",
    sentAt: "2026-10-17T21:20:00Z",
    deliveryStatus: "ACCEPTED",
    bodyText: "Exactly what was sent.",
    ...overrides,
  };
}

describe("EmailPreview attachments", () => {
  it("lists each attachment's name and size, in order", () => {
    render(
      <EmailPreview
        report={makeReport({
          attachments: [
            { fileName: "call-1-lakeview-inn.mp3", contentType: "audio/mpeg", sizeBytes: 1_998_188 },
            { fileName: "call-1-lakeview-inn-transcript.txt", contentType: "text/plain", sizeBytes: 812 },
            { fileName: "call-2-budget-motel.mp3", contentType: "audio/mpeg", sizeBytes: 3_565_158 },
          ],
        })}
      />,
    );

    const section = screen.getByRole("region", { name: "Attachments (3)" });
    const items = within(section).getAllByRole("listitem");
    expect(items.map((li) => li.textContent)).toEqual([
      "call-1-lakeview-inn.mp31.9 MB",
      "call-1-lakeview-inn-transcript.txt812 B",
      "call-2-budget-motel.mp33.4 MB",
    ]);
    // Names only: nothing to download, since the bytes aren't stored.
    expect(within(section).queryByRole("link")).not.toBeInTheDocument();
  });

  it("shows no attachments section when there are none", () => {
    render(<EmailPreview report={makeReport({ attachments: [] })} />);
    expect(screen.queryByRole("region", { name: /Attachments/ })).not.toBeInTheDocument();
  });
});
