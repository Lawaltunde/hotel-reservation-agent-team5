"use client";

import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";

import type { Municipality } from "@/models/municipality";
import { isAbortError } from "@/services/api-client";
import { searchMunicipalities } from "@/services/municipality-service";

const DEBOUNCE_MS = 250;
const MIN_QUERY_LENGTH = 2;

interface CityAutocompleteProps {
  id: string;
  value: string;
  /** Called on every keystroke with the typed text. */
  onChange: (value: string) => void;
  /** Called when the user picks a suggestion. */
  onSelect: (municipality: Municipality) => void;
  onBlur?: () => void;
  invalid?: boolean;
  describedBy?: string;
}

/**
 * Ontario city input with suggestions from `/api/municipalities`.
 * Implements the WAI-ARIA combobox pattern: arrow keys move, Enter picks, Escape closes.
 */
export function CityAutocomplete({ id, value, onChange, onSelect, onBlur, invalid, describedBy }: CityAutocompleteProps) {
  const listboxId = useId();
  const statusId = useId();
  const [suggestions, setSuggestions] = useState<Municipality[]>([]);
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [loading, setLoading] = useState(false);
  const [lookupFailed, setLookupFailed] = useState(false);

  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const requestRef = useRef<AbortController | null>(null);

  useEffect(
    () => () => {
      if (timerRef.current) clearTimeout(timerRef.current);
      requestRef.current?.abort();
    },
    [],
  );

  function cancelPending() {
    if (timerRef.current) clearTimeout(timerRef.current);
    requestRef.current?.abort();
    requestRef.current = null;
  }

  function scheduleSearch(query: string) {
    cancelPending();
    const q = query.trim();
    if (q.length < MIN_QUERY_LENGTH) {
      setSuggestions([]);
      setOpen(false);
      setLoading(false);
      return;
    }
    setLoading(true);
    timerRef.current = setTimeout(async () => {
      const controller = new AbortController();
      requestRef.current = controller;
      try {
        const results = await searchMunicipalities(q, 10, { signal: controller.signal });
        if (controller.signal.aborted) return;
        setSuggestions(results);
        setActiveIndex(-1);
        setOpen(true);
        setLookupFailed(false);
      } catch (error) {
        if (isAbortError(error) || controller.signal.aborted) return;
        setSuggestions([]);
        setOpen(false);
        setLookupFailed(true);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }, DEBOUNCE_MS);
  }

  function choose(municipality: Municipality) {
    cancelPending();
    setLoading(false);
    setOpen(false);
    setActiveIndex(-1);
    onSelect(municipality);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    switch (event.key) {
      case "ArrowDown":
        if (suggestions.length === 0) return;
        event.preventDefault();
        setOpen(true);
        setActiveIndex((i) => (i + 1) % suggestions.length);
        break;
      case "ArrowUp":
        if (suggestions.length === 0) return;
        event.preventDefault();
        setOpen(true);
        setActiveIndex((i) => (i <= 0 ? suggestions.length - 1 : i - 1));
        break;
      case "Enter":
        if (open && activeIndex >= 0 && suggestions[activeIndex]) {
          event.preventDefault(); // pick the city instead of submitting the form
          choose(suggestions[activeIndex]);
        }
        break;
      case "Escape":
        if (open) {
          event.preventDefault();
          setOpen(false);
          setActiveIndex(-1);
        }
        break;
    }
  }

  const showList = open && value.trim().length >= MIN_QUERY_LENGTH;
  const activeId = activeIndex >= 0 ? `${listboxId}-opt-${activeIndex}` : undefined;
  const statusText = loading
    ? "Searching Ontario cities…"
    : lookupFailed
      ? "City suggestions are unavailable right now. You can still type the city name."
      : showList
        ? suggestions.length === 0
          ? "No Ontario city matches that name."
          : `${suggestions.length} ${suggestions.length === 1 ? "city" : "cities"} found. Use the arrow keys to choose.`
        : "";

  return (
    <div className="relative">
      <input
        id={id}
        type="text"
        role="combobox"
        autoComplete="off"
        spellCheck={false}
        aria-autocomplete="list"
        aria-expanded={showList}
        aria-controls={listboxId}
        aria-activedescendant={showList ? activeId : undefined}
        aria-invalid={invalid || undefined}
        aria-describedby={[describedBy, statusId].filter(Boolean).join(" ")}
        placeholder="e.g. Kingston"
        value={value}
        onChange={(e) => {
          onChange(e.target.value);
          scheduleSearch(e.target.value);
        }}
        onKeyDown={handleKeyDown}
        onFocus={() => {
          if (suggestions.length > 0) setOpen(true);
        }}
        onBlur={() => {
          setOpen(false);
          onBlur?.();
        }}
        className="field-input"
      />
      <p id={statusId} aria-live="polite" className={lookupFailed ? "mt-1 text-xs text-slate-500" : "sr-only"}>
        {statusText}
      </p>
      <ul
        id={listboxId}
        role="listbox"
        aria-label="Ontario cities"
        hidden={!showList}
        className="absolute z-20 mt-1 max-h-64 w-full overflow-auto rounded-lg border border-slate-200 bg-white py-1 shadow-lg"
      >
        {suggestions.length === 0 && !loading ? (
          <li className="px-3 py-2 text-sm text-slate-500">No Ontario city matches that name.</li>
        ) : (
          suggestions.map((m, index) => (
            <li
              key={`${m.id}`}
              id={`${listboxId}-opt-${index}`}
              role="option"
              aria-selected={index === activeIndex}
              // mousedown, not click, so the input's blur doesn't close the list first
              onMouseDown={(e) => {
                e.preventDefault();
                choose(m);
              }}
              onMouseEnter={() => setActiveIndex(index)}
              className={`cursor-pointer px-3 py-2 text-sm ${
                index === activeIndex ? "bg-sky-50 text-sky-900" : "text-slate-800"
              }`}
            >
              {m.name}
              <span className="text-slate-500">, ON</span>
            </li>
          ))
        )}
      </ul>
    </div>
  );
}
