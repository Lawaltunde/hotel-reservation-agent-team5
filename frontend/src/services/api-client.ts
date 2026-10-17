import type { HealthStatus } from "@/models/health";
import type { ProblemDetail } from "@/models/problem-detail";
import { isProblemDetail } from "@/models/problem-detail";

/**
 * All backend calls go through the same-origin `/api` prefix. In production the Spring Boot
 * app serves both the UI and `/api`; under `npm run dev`, Next.js proxies `/api` to
 * `BACKEND_URL` (see next.config.ts).
 */
export const API_BASE = "/api";

const DEFAULT_TIMEOUT_MS = 10_000;

/**
 * Raised for any failed backend call: network error, timeout, non-2xx or bad JSON.
 * For error responses, `problem` holds the parsed RFC 9457 body when there is one.
 */
export class ApiError extends Error {
  constructor(
    message: string,
    /** HTTP status, or undefined when no response was received. */
    readonly status?: number,
    /** Parsed problem details from the response body, if any. */
    readonly problem?: ProblemDetail,
  ) {
    super(message);
    this.name = "ApiError";
  }

  /** True when no HTTP response arrived (offline, refused, timed out). */
  get isNetworkError(): boolean {
    return this.status === undefined;
  }

  /** Validation messages from a 400 response. */
  get errors(): string[] {
    return stringArray(this.problem?.errors);
  }

  /** Suggested Ontario municipalities from a 422 response. */
  get suggestions(): string[] {
    return stringArray(this.problem?.suggestions);
  }
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((v): v is string => typeof v === "string") : [];
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  /** JSON-serialisable request body. */
  body?: unknown;
  /** Abort the request after this many milliseconds. */
  timeoutMs?: number;
}

/**
 * Typed fetch wrapper. Sends and receives JSON and turns every failure into an
 * {@link ApiError} so callers handle a single error type.
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, timeoutMs = DEFAULT_TIMEOUT_MS, headers, signal, ...init } = options;

  const timeoutSignal = AbortSignal.timeout(timeoutMs);
  const combinedSignal = signal ? AbortSignal.any([signal, timeoutSignal]) : timeoutSignal;

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers: {
        Accept: "application/json, application/problem+json",
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: combinedSignal,
      cache: "no-store",
    });
  } catch (error) {
    if (signal?.aborted) throw error; // caller cancelled: let them see the AbortError
    if (timeoutSignal.aborted) {
      throw new ApiError(`Request to ${path} timed out after ${timeoutMs} ms`);
    }
    throw new ApiError(
      `Request to ${path} failed: ${error instanceof Error ? error.message : String(error)}`,
    );
  }

  const text = await response.text().catch(() => "");

  if (!response.ok) {
    const problem = parseProblem(text);
    const message =
      problem?.detail ?? problem?.title ?? `Request to ${path} returned HTTP ${response.status}`;
    throw new ApiError(message, response.status, problem);
  }

  if (text === "") return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new ApiError(`Response from ${path} was not valid JSON`, response.status);
  }
}

function parseProblem(text: string): ProblemDetail | undefined {
  if (!text) return undefined;
  try {
    const parsed: unknown = JSON.parse(text);
    return isProblemDetail(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

/** True when the error came from an aborted request (component unmounted, newer search). */
export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

/** `GET /api/health`: reports whether the backend is up. */
export function getHealth(options?: RequestOptions): Promise<HealthStatus> {
  return apiRequest<HealthStatus>("/health", { ...options, method: "GET" });
}
