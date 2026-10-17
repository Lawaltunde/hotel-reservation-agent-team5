import { beforeEach, describe, expect, it, vi } from "vitest";

import { searchMunicipalities } from "@/services/municipality-service";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("searchMunicipalities", () => {
  it("encodes the query and limit", async () => {
    fetchMock.mockResolvedValueOnce(Response.json([{ id: "1", name: "Sault Ste. Marie", timeZone: "America/Toronto" }]));

    const result = await searchMunicipalities("Sault Ste & Marie", 5);

    expect(result).toEqual([{ id: "1", name: "Sault Ste. Marie", timeZone: "America/Toronto" }]);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/municipalities?q=Sault+Ste+%26+Marie&limit=5");
  });

  it("defaults the limit to 10", async () => {
    fetchMock.mockResolvedValueOnce(Response.json([]));
    await searchMunicipalities("ot");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/municipalities?q=ot&limit=10");
  });

  it("returns an empty list when the body is not an array", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ unexpected: true }));
    await expect(searchMunicipalities("ot")).resolves.toEqual([]);
  });
});
