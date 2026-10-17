import type { HealthStatus } from "@/models/health";

/**
 * All backend calls go through the same-origin `/api` prefix. Next.js rewrites
 * it to `${BACKEND_URL}/api` (see next.config.ts), so the browser never needs
 * to know where the backend lives.
 */
const API_BASE = "/api";

const DEFAULT_TIMEOUT_MS = 5000;

/** Raised for any failed backend call: network error, timeout, non-2xx or bad JSON. */
export class ApiError extends Error {
  constructor(
    message: string,
    /** HTTP status, or undefined when no response was received. */
    readonly status?: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
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
        Accept: "application/json",
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: combinedSignal,
      cache: "no-store",
    });
  } catch (error) {
    if (timeoutSignal.aborted) {
      throw new ApiError(`Request to ${path} timed out after ${timeoutMs} ms`);
    }
    throw new ApiError(
      `Request to ${path} failed: ${error instanceof Error ? error.message : String(error)}`,
    );
  }

  if (!response.ok) {
    throw new ApiError(`Request to ${path} returned HTTP ${response.status}`, response.status);
  }

  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError(`Response from ${path} was not valid JSON`, response.status);
  }
}

/** `GET /api/health`: reports whether the backend is up. */
export function getHealth(options?: RequestOptions): Promise<HealthStatus> {
  return apiRequest<HealthStatus>("/health", { ...options, method: "GET" });
}
