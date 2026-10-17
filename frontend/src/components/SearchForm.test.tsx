import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SearchForm } from "@/components/SearchForm";
import { ApiError } from "@/services/api-client";
import { searchMunicipalities } from "@/services/municipality-service";
import { createTask } from "@/services/task-service";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
vi.mock("@/services/task-service", () => ({ createTask: vi.fn() }));
vi.mock("@/services/municipality-service", () => ({ searchMunicipalities: vi.fn() }));

const createTaskMock = vi.mocked(createTask);
const searchMock = vi.mocked(searchMunicipalities);

beforeEach(() => {
  push.mockReset();
  createTaskMock.mockReset();
  searchMock.mockReset();
  searchMock.mockResolvedValue([]);
});

async function fillRequired(user: ReturnType<typeof userEvent.setup>, city = "Ottawa") {
  await user.type(screen.getByRole("combobox", { name: "City in Ontario" }), city);
  await user.type(screen.getByLabelText("Email"), "traveller@example.com");
}

describe("SearchForm", () => {
  it("submits a valid enquiry and opens the task page", async () => {
    const user = userEvent.setup();
    createTaskMock.mockResolvedValue({ taskId: "task-1", status: "VALIDATING" });
    render(<SearchForm />);

    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "Find my hotel" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/task?id=task-1"));
    const request = createTaskMock.mock.calls[0][0];
    expect(request).toMatchObject({
      city: "Ottawa",
      rooms: 1,
      adults: 2,
      children: 0,
      destinationEmail: "traveller@example.com",
      authorizationMode: "ENQUIRY_ONLY",
      maximumTotalCad: null,
      requiredAmenities: [],
    });
    expect(request.requestId).toMatch(/^[0-9a-f-]{36}$/);
    expect(screen.getByRole("button", { name: "Starting…" })).toBeDisabled();
  });

  it("blocks submission and focuses the first invalid field", async () => {
    const user = userEvent.setup();
    render(<SearchForm />);

    await user.click(screen.getByRole("button", { name: "Find my hotel" }));

    expect(createTaskMock).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent("Please fix the 2 highlighted fields.");
    expect(screen.getByText("Choose an Ontario city")).toBeInTheDocument();
    expect(screen.getByText("Enter the email that should receive the result")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "City in Ontario" })).toHaveFocus();
  });

  it("does not offer automatic booking yet", () => {
    render(<SearchForm />);
    expect(screen.getByRole("radio", { name: /Book automatically/ })).toBeDisabled();
    expect(screen.getByRole("radio", { name: /Enquiry only/ })).toBeChecked();
  });

  it("asks which city was meant for an ambiguous 422 and offers suggestions", async () => {
    const user = userEvent.setup();
    createTaskMock.mockRejectedValue(
      new ApiError("'Springfield' matches several Ontario municipalities", 422, {
        status: 422,
        title: "Ambiguous location",
        detail: "'Springfield' matches several Ontario municipalities",
        suggestions: ["Springwater", "Springfield"],
      }),
    );
    render(<SearchForm />);

    await fillRequired(user, "Springfield");
    await user.click(screen.getByRole("button", { name: "Find my hotel" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Which Ontario city did you mean?");
    expect(alert).toHaveTextContent("'Springfield' matches several Ontario municipalities");
    expect(push).not.toHaveBeenCalled();

    await user.click(within(alert).getByRole("button", { name: "Springwater" }));
    expect(screen.getByRole("combobox", { name: "City in Ontario" })).toHaveValue("Springwater");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows a Vancouver 422 without suggestions", async () => {
    const user = userEvent.setup();
    createTaskMock.mockRejectedValue(
      new ApiError("'Vancouver' is not a supported Ontario municipality", 422, {
        status: 422,
        title: "Location out of scope",
        detail: "'Vancouver' is not a supported Ontario municipality",
        suggestions: [],
      }),
    );
    render(<SearchForm />);

    await fillRequired(user, "Vancouver");
    await user.click(screen.getByRole("button", { name: "Find my hotel" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("We only search Ontario.");
    expect(alert).toHaveTextContent("'Vancouver' is not a supported Ontario municipality");
    expect(within(alert).queryByRole("button")).not.toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "City in Ontario" })).toHaveAttribute("aria-invalid", "true");
  });

  it("maps 400 messages that name a field onto that field", async () => {
    const user = userEvent.setup();
    createTaskMock.mockRejectedValue(
      new ApiError("Invalid request", 400, {
        status: 400,
        title: "Invalid request",
        errors: ["checkIn is in the past", "something else went wrong"],
      }),
    );
    render(<SearchForm />);

    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "Find my hotel" }));

    expect(await screen.findByText("Is in the past")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("something else went wrong");
  });

  it("reports a network failure and reuses the requestId on retry", async () => {
    const user = userEvent.setup();
    createTaskMock.mockRejectedValueOnce(new ApiError("Request to /tasks failed: offline"));
    createTaskMock.mockResolvedValueOnce({ taskId: "task-2", status: "VALIDATING" });
    render(<SearchForm />);

    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "Find my hotel" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("We couldn't reach the server.");

    await user.click(screen.getByRole("button", { name: "Find my hotel" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/task?id=task-2"));
    expect(createTaskMock.mock.calls[1][0].requestId).toBe(createTaskMock.mock.calls[0][0].requestId);
  });

  it("uses the chosen city's time zone and name from the autocomplete", async () => {
    const user = userEvent.setup();
    searchMock.mockResolvedValue([{ id: "k", name: "Kenora", timeZone: "America/Winnipeg" }]);
    createTaskMock.mockResolvedValue({ taskId: "task-3", status: "VALIDATING" });
    render(<SearchForm />);

    await user.type(screen.getByRole("combobox", { name: "City in Ontario" }), "ken");
    await user.click(await screen.findByRole("option", { name: /Kenora/ }));
    await user.type(screen.getByLabelText("Email"), "a@example.com");
    await user.click(screen.getByRole("button", { name: "Find my hotel" }));

    await waitFor(() => expect(createTaskMock).toHaveBeenCalled());
    expect(createTaskMock.mock.calls[0][0].city).toBe("Kenora");
  });
});
