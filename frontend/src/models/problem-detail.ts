/**
 * RFC 9457 problem details, as returned by the backend for every error.
 * 400 adds `errors`; 422 (city outside Ontario or ambiguous) adds `suggestions`.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** 400: validation messages. */
  errors?: string[];
  /** 422: Ontario municipalities the user may have meant. */
  suggestions?: string[];
  [extension: string]: unknown;
}

export function isProblemDetail(value: unknown): value is ProblemDetail {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v.title === "string" ||
    typeof v.detail === "string" ||
    typeof v.status === "number" ||
    Array.isArray(v.errors) ||
    Array.isArray(v.suggestions)
  );
}
