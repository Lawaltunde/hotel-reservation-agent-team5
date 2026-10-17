import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError, apiRequest, getHealth, isAbortError } from "@/services/api-client";

const fetchMock = vi.fn<typeof fetch>();

function respond(status: number, body: string, contentType = "application/json") {
  fetchMock.mockResolvedValueOnce(new Response(body === "" ? null : body, { status, headers: { "Content-Type": contentType } }));
}

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    expect(error).toBeInstanceOf(ApiError);
    return error as ApiError;
  }
  throw new Error("expected the request to fail");
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  fetchMock.mockReset();
});

describe("apiRequest", () => {
  it("prefixes /api and sends JSON only when there is a body", async () => {
    respond(200, '{"ok":true}');
    respond(200, '{"ok":true}');

    await apiRequest("/things", { method: "POST", body: { a: 1 } });
    await apiRequest("/things");

    const [postUrl, post] = fetchMock.mock.calls[0];
    expect(postUrl).toBe("/api/things");
    expect(post?.body).toBe('{"a":1}');
    expect(post?.headers).toMatchObject({
      Accept: "application/json, application/problem+json",
      "Content-Type": "application/json",
    });
    expect(post?.cache).toBe("no-store");

    const [, get] = fetchMock.mock.calls[1];
    expect(get?.body).toBeUndefined();
    expect(get?.headers).not.toHaveProperty("Content-Type");
  });

  it("parses JSON and returns undefined for an empty body", async () => {
    respond(200, '{"taskId":"t1"}');
    await expect(apiRequest("/x")).resolves.toEqual({ taskId: "t1" });

    respond(202, "");
    await expect(apiRequest("/x")).resolves.toBeUndefined();
  });

  it("turns a 400 problem into an ApiError with the validation messages", async () => {
    respond(
      400,
      JSON.stringify({ status: 400, title: "Invalid request", detail: "checkIn is in the past", errors: ["checkIn is in the past", 7] }),
      "application/problem+json",
    );

    const error = await failure(apiRequest("/tasks", { method: "POST", body: {} }));
    expect(error.status).toBe(400);
    expect(error.message).toBe("checkIn is in the past");
    expect(error.errors).toEqual(["checkIn is in the past"]);
    expect(error.suggestions).toEqual([]);
    expect(error.isNetworkError).toBe(false);
  });

  it("exposes 422 suggestions", async () => {
    respond(
      422,
      JSON.stringify({ status: 422, title: "Ambiguous location", suggestions: ["Springwater", "Springfield"] }),
      "application/problem+json",
    );

    const error = await failure(apiRequest("/tasks"));
    expect(error.status).toBe(422);
    expect(error.message).toBe("Ambiguous location");
    expect(error.suggestions).toEqual(["Springwater", "Springfield"]);
  });

  it("falls back to the HTTP status when the error body is not a problem", async () => {
    respond(502, "<html>Bad Gateway</html>", "text/html");

    const error = await failure(apiRequest("/tasks"));
    expect(error.status).toBe(502);
    expect(error.problem).toBeUndefined();
    expect(error.message).toBe("Request to /tasks returned HTTP 502");
  });

  it("reports a malformed success body", async () => {
    respond(200, "{not json");
    const error = await failure(apiRequest("/x"));
    expect(error.status).toBe(200);
    expect(error.message).toBe("Response from /x was not valid JSON");
  });

  it("reports network failures without a status", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));

    const error = await failure(apiRequest("/health"));
    expect(error.status).toBeUndefined();
    expect(error.isNetworkError).toBe(true);
    expect(error.message).toBe("Request to /health failed: Failed to fetch");
  });

  it("reports a timeout as an ApiError", async () => {
    fetchMock.mockImplementationOnce(
      (_url, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
        }),
    );

    const error = await failure(apiRequest("/slow", { timeoutMs: 20 }));
    expect(error.isNetworkError).toBe(true);
    expect(error.message).toBe("Request to /slow timed out after 20 ms");
  });

  it("lets a caller's abort through as an AbortError", async () => {
    fetchMock.mockImplementationOnce(
      (_url, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
        }),
    );
    const controller = new AbortController();
    const request = apiRequest("/x", { signal: controller.signal });
    controller.abort();

    const error = await request.catch((e: unknown) => e);
    expect(error).not.toBeInstanceOf(ApiError);
    expect(isAbortError(error)).toBe(true);
  });
});

describe("getHealth", () => {
  it("GETs /api/health", async () => {
    respond(200, '{"status":"UP","service":"hotel-reservation-agent","time":"2026-10-17T12:00:00Z"}');

    await expect(getHealth()).resolves.toMatchObject({ status: "UP" });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/health");
    expect(fetchMock.mock.calls[0][1]?.method).toBe("GET");
  });
});

describe("isAbortError", () => {
  it("is true only for AbortError DOMExceptions", () => {
    expect(isAbortError(new DOMException("x", "AbortError"))).toBe(true);
    expect(isAbortError(new DOMException("x", "TimeoutError"))).toBe(false);
    expect(isAbortError(new Error("AbortError"))).toBe(false);
  });
});
