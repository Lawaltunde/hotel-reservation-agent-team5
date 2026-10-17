import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { CityAutocomplete } from "@/components/CityAutocomplete";
import type { Municipality } from "@/models/municipality";
import { ApiError } from "@/services/api-client";
import { searchMunicipalities } from "@/services/municipality-service";

vi.mock("@/services/municipality-service", () => ({ searchMunicipalities: vi.fn() }));
const searchMock = vi.mocked(searchMunicipalities);

const CITIES: Municipality[] = [
  { id: "1", name: "Kingston", timeZone: "America/Toronto" },
  { id: "2", name: "Kitchener", timeZone: "America/Toronto" },
];

function Harness({ onSelect = vi.fn() }: { onSelect?: (m: Municipality) => void }) {
  const [value, setValue] = useState("");
  return (
    <>
      <label htmlFor="city">City</label>
      <CityAutocomplete
        id="city"
        value={value}
        onChange={setValue}
        onSelect={(m) => {
          setValue(m.name);
          onSelect(m);
        }}
      />
    </>
  );
}

beforeEach(() => {
  searchMock.mockReset();
});

describe("CityAutocomplete", () => {
  it("does not search for fewer than two characters", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.type(screen.getByRole("combobox", { name: "City" }), "k");
    await new Promise((r) => setTimeout(r, 300));

    expect(searchMock).not.toHaveBeenCalled();
    expect(screen.getByRole("combobox")).toHaveAttribute("aria-expanded", "false");
  });

  it("debounces typing into one search and lists the results", async () => {
    const user = userEvent.setup();
    searchMock.mockResolvedValue(CITIES);
    render(<Harness />);

    await user.type(screen.getByRole("combobox", { name: "City" }), "ki");

    expect(await screen.findAllByRole("option")).toHaveLength(2);
    expect(searchMock).toHaveBeenCalledTimes(1);
    expect(searchMock).toHaveBeenCalledWith("ki", 10, expect.objectContaining({ signal: expect.any(AbortSignal) }));
    expect(screen.getByRole("combobox")).toHaveAttribute("aria-expanded", "true");
  });

  it("picks a city with the keyboard", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    searchMock.mockResolvedValue(CITIES);
    render(<Harness onSelect={onSelect} />);

    const input = screen.getByRole("combobox", { name: "City" });
    await user.type(input, "ki");
    await screen.findAllByRole("option");
    await user.keyboard("{ArrowDown}{ArrowDown}");
    expect(screen.getByRole("option", { name: /Kitchener/ })).toHaveAttribute("aria-selected", "true");
    await user.keyboard("{Enter}");

    expect(onSelect).toHaveBeenCalledWith(CITIES[1]);
    expect(input).toHaveValue("Kitchener");
    expect(input).toHaveAttribute("aria-expanded", "false");
  });

  it("closes the list on Escape", async () => {
    const user = userEvent.setup();
    searchMock.mockResolvedValue(CITIES);
    render(<Harness />);

    await user.type(screen.getByRole("combobox", { name: "City" }), "ki");
    await screen.findAllByRole("option");
    await user.keyboard("{Escape}");

    expect(screen.getByRole("combobox")).toHaveAttribute("aria-expanded", "false");
  });

  it("says when nothing in Ontario matches", async () => {
    const user = userEvent.setup();
    searchMock.mockResolvedValue([]);
    render(<Harness />);

    await user.type(screen.getByRole("combobox", { name: "City" }), "Vancouver");

    expect(await screen.findAllByText("No Ontario city matches that name.")).not.toHaveLength(0);
    expect(screen.queryByRole("option")).not.toBeInTheDocument();
  });

  it("lets the user keep typing when the lookup fails", async () => {
    const user = userEvent.setup();
    searchMock.mockRejectedValue(new ApiError("Request to /municipalities failed: offline"));
    render(<Harness />);

    const input = screen.getByRole("combobox", { name: "City" });
    await user.type(input, "Ottawa");

    expect(await screen.findByText(/City suggestions are unavailable right now/)).toBeInTheDocument();
    expect(input).toHaveValue("Ottawa");
  });
});
