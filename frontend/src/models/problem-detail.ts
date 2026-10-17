/**
 * RFC 9457 problem details, as returned by the backend for every error.
 * 400 adds `errors`; 422 (place outside Canada or ambiguous) adds `suggestions`.
 */
/** One entry of a 400 `errors` array. `field` names the request field, e.g. `city` or `email`. */
export interface FieldError {
  field: string | null;
  message: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** 400: what is missing or invalid, e.g. `{field: "email", message: "..."}`. Plain strings are also accepted. */
  errors?: Array<FieldError | string>;
  /** 422: Canadian cities the user may have meant. */
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
