"use client";

import { useRouter } from "next/navigation";
import { useId, useRef, useState, type FormEvent, type ReactNode } from "react";

import { CityAutocomplete } from "@/components/CityAutocomplete";
import { AUTHORIZATION_MODE_LABELS } from "@/components/format";
import type { Municipality } from "@/models/municipality";
import { DEFAULT_ONTARIO_TIME_ZONE } from "@/models/municipality";
import {
  addDays,
  defaultSearchFormValues,
  ENABLED_AUTHORIZATION_MODES,
  LIMITS,
  SEARCH_FORM_FIELDS,
  todayIn,
  validateSearchForm,
  type SearchFormErrors,
  type SearchFormField,
  type SearchFormValues,
} from "@/models/search-form";
import type { AuthorizationMode } from "@/models/task";
import { AUTHORIZATION_MODES } from "@/models/task";
import { ApiError } from "@/services/api-client";
import { createTask } from "@/services/task-service";

type Touched = Partial<Record<SearchFormField, boolean>>;

interface OutOfScope {
  detail: string;
  suggestions: string[];
}

/** Assigns backend 400 messages to fields when they name one ("checkOut: must be…"); the rest are form-level. */
function mapServerErrors(messages: string[]): { fields: SearchFormErrors; other: string[] } {
  const fields: SearchFormErrors = {};
  const other: string[] = [];
  for (const message of messages) {
    const lower = message.toLowerCase();
    const field = SEARCH_FORM_FIELDS.find((f) => {
      const name = f.toLowerCase();
      return lower.startsWith(`${name}:`) || lower.startsWith(`${name} `) || lower.startsWith(`'${name}'`);
    });
    if (field && !fields[field]) {
      fields[field] = message.replace(new RegExp(`^'?${field}'?\\s*:?\\s*`, "i"), "");
      fields[field] = fields[field].charAt(0).toUpperCase() + fields[field].slice(1);
    } else {
      other.push(message);
    }
  }
  return { fields, other };
}

export function SearchForm() {
  const router = useRouter();
  const formId = useId();
  const [values, setValues] = useState<SearchFormValues>(() => defaultSearchFormValues());
  const [timeZone, setTimeZone] = useState(DEFAULT_ONTARIO_TIME_ZONE);
  const [errors, setErrors] = useState<SearchFormErrors>({});
  const [touched, setTouched] = useState<Touched>({});
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [serverErrors, setServerErrors] = useState<SearchFormErrors>({});
  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [outOfScope, setOutOfScope] = useState<OutOfScope | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const errorSummaryRef = useRef<HTMLDivElement>(null);

  // One requestId per distinct submission; a retry of the same payload reuses it so the
  // backend can de-duplicate.
  const pendingRequest = useRef<{ id: string; key: string } | null>(null);

  const fieldId = (field: SearchFormField) => `${formId}-${field}`;

  function revalidate(next: SearchFormValues, tz = timeZone) {
    const result = validateSearchForm(next, { timeZone: tz });
    setErrors(result.success ? {} : result.errors);
    return result;
  }

  function update<K extends SearchFormField>(field: K, value: SearchFormValues[K]) {
    const next = { ...values, [field]: value };
    // Keep check-out after check-in when check-in moves past it.
    if (field === "checkIn" && typeof value === "string" && value && next.checkOut <= value) {
      next.checkOut = addDays(value, 1);
    }
    setValues(next);
    setServerErrors((prev) => {
      if (!prev[field]) return prev;
      const copy = { ...prev };
      delete copy[field];
      return copy;
    });
    if (field === "city") setOutOfScope(null);
    revalidate(next);
  }

  function touch(field: SearchFormField) {
    setTouched((t) => (t[field] ? t : { ...t, [field]: true }));
    revalidate(values);
  }

  function selectCity(m: Municipality) {
    const tz = m.timeZone || DEFAULT_ONTARIO_TIME_ZONE;
    const next = { ...values, city: m.name };
    setTimeZone(tz);
    setValues(next);
    setOutOfScope(null);
    setTouched((t) => ({ ...t, city: true }));
    revalidate(next, tz);
  }

  function errorFor(field: SearchFormField): string | undefined {
    if (serverErrors[field]) return serverErrors[field];
    if (touched[field] || submitAttempted) return errors[field];
    return undefined;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    setSubmitAttempted(true);
    setFormErrors([]);
    setOutOfScope(null);

    const result = revalidate(values);
    if (!result.success) {
      const first = SEARCH_FORM_FIELDS.find((f) => result.errors[f]);
      if (first) document.getElementById(fieldId(first))?.focus();
      return;
    }

    const key = JSON.stringify(result.request);
    if (!pendingRequest.current || pendingRequest.current.key !== key) {
      pendingRequest.current = { id: crypto.randomUUID(), key };
    }

    setSubmitting(true);
    try {
      const { taskId } = await createTask({ ...result.request, requestId: pendingRequest.current.id });
      router.push(`/task?id=${encodeURIComponent(taskId)}`);
      // keep the button disabled while navigating
    } catch (error) {
      setSubmitting(false);
      if (error instanceof ApiError && error.status === 422) {
        setOutOfScope({
          detail: error.problem?.detail ?? "",
          suggestions: error.suggestions,
        });
        document.getElementById(fieldId("city"))?.focus();
        return;
      }
      if (error instanceof ApiError && error.status === 400) {
        const mapped = mapServerErrors(error.errors);
        setServerErrors(mapped.fields);
        const other = mapped.other.length > 0 ? mapped.other : Object.keys(mapped.fields).length ? [] : [error.message];
        setFormErrors(other);
      } else if (error instanceof ApiError && error.isNetworkError) {
        setFormErrors(["We couldn't reach the server. Check your connection and try again."]);
      } else if (error instanceof ApiError) {
        setFormErrors([`Something went wrong on our side (${error.status ?? "no status"}): ${error.message}. Please try again.`]);
      } else {
        setFormErrors(["Something went wrong. Please try again."]);
      }
      requestAnimationFrame(() => errorSummaryRef.current?.focus());
    }
  }

  const today = todayIn(timeZone);
  const visibleErrorCount = SEARCH_FORM_FIELDS.filter((f) => errorFor(f)).length;

  return (
    <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-8" aria-describedby={`${formId}-intro`}>
      <p id={`${formId}-intro`} className="sr-only">
        All fields are required unless marked optional.
      </p>

      {(formErrors.length > 0 || (submitAttempted && visibleErrorCount > 0)) && (
        <div
          ref={errorSummaryRef}
          tabIndex={-1}
          role="alert"
          className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-900"
        >
          {formErrors.length > 0 ? (
            <ul className="list-disc space-y-1 pl-5">
              {formErrors.map((m) => (
                <li key={m}>{m}</li>
              ))}
            </ul>
          ) : (
            <p>
              Please fix {visibleErrorCount === 1 ? "the highlighted field" : `the ${visibleErrorCount} highlighted fields`}.
            </p>
          )}
        </div>
      )}

      <Fieldset legend="Where and when">
        <div className="sm:col-span-2">
          <Field id={fieldId("city")} label="City in Ontario" error={errorFor("city")} hint="Ontario only. Start typing and choose from the list.">
            {(describedBy, invalid) => (
              <CityAutocomplete
                id={fieldId("city")}
                value={values.city}
                onChange={(v) => update("city", v)}
                onSelect={selectCity}
                onBlur={() => touch("city")}
                invalid={invalid || outOfScope !== null}
                describedBy={[describedBy, outOfScope ? `${formId}-oos` : ""].filter(Boolean).join(" ")}
              />
            )}
          </Field>
          {outOfScope && (
            <div id={`${formId}-oos`} role="alert" className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
              <p className="font-medium">We only search Ontario.</p>
              <p className="mt-1">
                {outOfScope.detail || `We couldn't match "${values.city}" to a single Ontario city.`}
              </p>
              {outOfScope.suggestions.length > 0 && (
                <div className="mt-3">
                  <p>Did you mean:</p>
                  <ul className="mt-2 flex flex-wrap gap-2">
                    {outOfScope.suggestions.map((s) => (
                      <li key={s}>
                        <button
                          type="button"
                          onClick={() => {
                            update("city", s);
                            setTouched((t) => ({ ...t, city: true }));
                            document.getElementById(fieldId("city"))?.focus();
                          }}
                          className="rounded-full border border-amber-300 bg-white px-3 py-1 font-medium hover:bg-amber-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-600"
                        >
                          {s}
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
        <Field id={fieldId("checkIn")} label="Check-in" error={errorFor("checkIn")}>
          {(describedBy, invalid) => (
            <input
              id={fieldId("checkIn")}
              type="date"
              min={today}
              value={values.checkIn}
              onChange={(e) => update("checkIn", e.target.value)}
              onBlur={() => touch("checkIn")}
              aria-invalid={invalid || undefined}
              aria-describedby={describedBy}
              className="field-input"
            />
          )}
        </Field>
        <Field id={fieldId("checkOut")} label="Check-out" error={errorFor("checkOut")}>
          {(describedBy, invalid) => (
            <input
              id={fieldId("checkOut")}
              type="date"
              min={values.checkIn ? addDays(values.checkIn, 1) : today}
              value={values.checkOut}
              onChange={(e) => update("checkOut", e.target.value)}
              onBlur={() => touch("checkOut")}
              aria-invalid={invalid || undefined}
              aria-describedby={describedBy}
              className="field-input"
            />
          )}
        </Field>
      </Fieldset>

      <Fieldset legend="Guests" columns={3}>
        {(
          [
            ["rooms", "Rooms", 1, LIMITS.maxRooms],
            ["adults", "Adults", 1, LIMITS.maxAdults],
            ["children", "Children", 0, LIMITS.maxChildren],
          ] as const
        ).map(([field, label, min, max]) => (
          <Field key={field} id={fieldId(field)} label={label} error={errorFor(field)}>
            {(describedBy, invalid) => (
              <input
                id={fieldId(field)}
                type="number"
                inputMode="numeric"
                min={min}
                max={max}
                step={1}
                value={values[field]}
                onChange={(e) => update(field, e.target.value)}
                onBlur={() => touch(field)}
                aria-invalid={invalid || undefined}
                aria-describedby={describedBy}
                className="field-input"
              />
            )}
          </Field>
        ))}
      </Fieldset>

      <Fieldset legend="Where to send the result">
        <Field id={fieldId("destinationEmail")} label="Email" error={errorFor("destinationEmail")} hint="We email the result and the call transcript here.">
          {(describedBy, invalid) => (
            <input
              id={fieldId("destinationEmail")}
              type="email"
              autoComplete="email"
              value={values.destinationEmail}
              onChange={(e) => update("destinationEmail", e.target.value)}
              onBlur={() => touch("destinationEmail")}
              aria-invalid={invalid || undefined}
              aria-describedby={describedBy}
              className="field-input"
            />
          )}
        </Field>
        <Field id={fieldId("deadline")} label="Deadline" error={errorFor("deadline")} hint="Your local time. The agent reports back by then.">
          {(describedBy, invalid) => (
            <input
              id={fieldId("deadline")}
              type="datetime-local"
              value={values.deadline}
              onChange={(e) => update("deadline", e.target.value)}
              onBlur={() => touch("deadline")}
              aria-invalid={invalid || undefined}
              aria-describedby={describedBy}
              className="field-input"
            />
          )}
        </Field>
      </Fieldset>

      <Fieldset legend="Budget and permissions" columns={1}>
        <Field
          id={fieldId("maximumTotalCad")}
          label="Maximum total (CAD)"
          optional
          error={errorFor("maximumTotalCad")}
          hint="For the whole stay, including taxes and mandatory fees. Leave empty for no limit."
        >
          {(describedBy, invalid) => (
            <div className="relative sm:max-w-xs">
              <span aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-slate-500">
                $
              </span>
              <input
                id={fieldId("maximumTotalCad")}
                type="text"
                inputMode="decimal"
                placeholder="No limit"
                value={values.maximumTotalCad}
                onChange={(e) => update("maximumTotalCad", e.target.value)}
                onBlur={() => touch("maximumTotalCad")}
                aria-invalid={invalid || undefined}
                aria-describedby={describedBy}
                className="field-input pl-7"
              />
            </div>
          )}
        </Field>

        <fieldset aria-describedby={errorFor("authorizationMode") ? `${fieldId("authorizationMode")}-error` : undefined}>
          <legend className="text-sm font-medium text-slate-800">What may the agent do?</legend>
          <div className="mt-2 flex flex-col gap-2">
            {AUTHORIZATION_MODES.map((mode: AuthorizationMode) => {
              const enabled = ENABLED_AUTHORIZATION_MODES.includes(mode);
              const id = `${fieldId("authorizationMode")}-${mode}`;
              return (
                <label
                  key={mode}
                  htmlFor={id}
                  className={`flex items-start gap-3 rounded-lg border p-3 text-sm ${
                    values.authorizationMode === mode ? "border-sky-400 bg-sky-50" : "border-slate-200 bg-white"
                  } ${enabled ? "cursor-pointer" : "cursor-not-allowed opacity-60"}`}
                >
                  <input
                    id={id}
                    type="radio"
                    name={fieldId("authorizationMode")}
                    value={mode}
                    checked={values.authorizationMode === mode}
                    disabled={!enabled}
                    onChange={() => update("authorizationMode", mode)}
                    className="mt-0.5 h-4 w-4 accent-sky-600"
                  />
                  <span>
                    {AUTHORIZATION_MODE_LABELS[mode]}
                    {!enabled && <span className="ml-2 rounded bg-slate-200 px-1.5 py-0.5 text-xs text-slate-700">Coming soon</span>}
                    {mode === "ENQUIRY_ONLY" && <span className="block text-xs text-slate-500">Recommended. Nothing is reserved.</span>}
                  </span>
                </label>
              );
            })}
          </div>
          {errorFor("authorizationMode") && (
            <p id={`${fieldId("authorizationMode")}-error`} className="mt-1 text-sm text-rose-700">
              {errorFor("authorizationMode")}
            </p>
          )}
        </fieldset>

        <label htmlFor={fieldId("allowCompetingQuoteDisclosure")} className="flex cursor-pointer items-start gap-3 text-sm">
          <input
            id={fieldId("allowCompetingQuoteDisclosure")}
            type="checkbox"
            checked={values.allowCompetingQuoteDisclosure}
            onChange={(e) => update("allowCompetingQuoteDisclosure", e.target.checked)}
            className="mt-0.5 h-4 w-4 accent-sky-600"
          />
          <span>
            Let the agent mention competing quotes when negotiating
            <span className="block text-xs text-slate-500">
              It only ever cites real quotes it received, never invented ones.
            </span>
          </span>
        </label>
      </Fieldset>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-xs text-slate-500">
          The agent phones hotels for quotes. It does not book unless you allow it.
        </p>
        <button
          type="submit"
          disabled={submitting}
          aria-disabled={submitting}
          className="inline-flex items-center justify-center gap-2 rounded-lg bg-sky-700 px-5 py-2.5 font-medium text-white shadow-sm hover:bg-sky-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700 disabled:cursor-wait disabled:opacity-70"
        >
          {submitting ? "Starting…" : "Find my hotel"}
        </button>
      </div>
    </form>
  );
}

function Fieldset({ legend, columns = 2, children }: { legend: string; columns?: 1 | 2 | 3; children: ReactNode }) {
  const grid = columns === 3 ? "sm:grid-cols-3" : columns === 2 ? "sm:grid-cols-2" : "";
  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="mb-3 text-base font-semibold text-slate-900">{legend}</legend>
      <div className={`grid grid-cols-1 gap-4 ${grid}`}>{children}</div>
    </fieldset>
  );
}

interface FieldProps {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  optional?: boolean;
  /** Render prop receiving the aria-describedby value and whether the field is invalid. */
  children: (describedBy: string | undefined, invalid: boolean) => ReactNode;
}

function Field({ id, label, hint, error, optional, children }: FieldProps) {
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-slate-800">
        {label}
        {optional && <span className="ml-1 font-normal text-slate-500">(optional)</span>}
      </label>
      {children(describedBy, Boolean(error))}
      {hint && (
        <p id={hintId} className="text-xs text-slate-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-sm text-rose-700">
          {error}
        </p>
      )}
    </div>
  );
}
