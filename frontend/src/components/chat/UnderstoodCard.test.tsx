import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { UnderstoodCard } from "@/components/chat/UnderstoodCard";
import type { TaskRequest } from "@/models/task";
import { makeTask } from "@/test/fixtures";

const request: TaskRequest = {
  city: "Halifax",
  checkIn: "2026-11-20",
  checkOut: "2026-11-22",
  adults: 2,
  children: 0,
  rooms: 1,
  maxBudget: 500,
  amenities: [],
  email: "me@example.com",
  originalPrompt: "Halifax, Nov 20-22, 2 adults",
};

describe("UnderstoodCard", () => {
  it("shows the city with its province name, falling back to the code", () => {
    const { unmount } = render(<UnderstoodCard task={makeTask({ request: { ...request, province: "NS", provinceName: "Nova Scotia" } })} />);
    expect(screen.getByText("Halifax").closest("p")).toHaveTextContent(/^Halifax, Nova Scotia$/);
    unmount();

    render(<UnderstoodCard task={makeTask({ request: { ...request, province: "NS" } })} />);
    expect(screen.getByText("Halifax").closest("p")).toHaveTextContent(/^Halifax, NS$/);
  });

  it("shows just the city when no province is given", () => {
    render(<UnderstoodCard task={makeTask({ request })} />);
    expect(screen.getByText("Halifax").closest("p")).toHaveTextContent(/^Halifax$/);
  });
});
