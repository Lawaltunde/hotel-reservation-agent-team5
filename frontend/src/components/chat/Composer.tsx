"use client";

import { useId, useLayoutEffect, type FormEvent, type KeyboardEvent, type RefObject } from "react";

import { MAX_PROMPT_LENGTH } from "@/components/chat/intake";

const MAX_HEIGHT_PX = 168;

interface ComposerProps {
  value: string;
  onChange: (value: string) => void;
  onSend: (value: string) => void;
  /** True while a request is in flight. */
  disabled: boolean;
  inputRef: RefObject<HTMLTextAreaElement | null>;
}

/** Message box: Enter sends, Shift+Enter adds a line, and it grows with the text up to a limit. */
export function Composer({ value, onChange, onSend, disabled, inputRef }: ComposerProps) {
  const id = useId();

  useLayoutEffect(() => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, MAX_HEIGHT_PX)}px`;
  }, [value, inputRef]);

  function send() {
    if (disabled || !value.trim()) return;
    onSend(value);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    // isComposing: Enter confirms an IME candidate (Japanese, Chinese...) and must not send.
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      send();
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    send();
  }

  return (
    <form onSubmit={handleSubmit} className="mx-auto w-full max-w-3xl px-4 pb-4 sm:px-6">
      <div className="flex items-end gap-2 rounded-2xl border border-line bg-paper p-2 shadow-[0_12px_32px_-20px_rgb(20_34_44/0.45)] transition-colors focus-within:border-lake">
        <label htmlFor={`${id}-input`} className="sr-only">
          Message the hotel agent
        </label>
        <textarea
          id={`${id}-input`}
          ref={inputRef}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          rows={1}
          maxLength={MAX_PROMPT_LENGTH}
          aria-describedby={`${id}-hint`}
          placeholder="City, check-in date, guests, your email…"
          className="max-h-42 min-h-11 flex-1 resize-none bg-transparent px-2 py-2.5 leading-6 text-ink placeholder:text-muted/80 focus:outline-none disabled:opacity-60"
        />
        <button
          type="submit"
          disabled={disabled || !value.trim()}
          className="focus-ring flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-pine text-paper transition hover:brightness-110 disabled:opacity-40"
        >
          <span className="sr-only">Send</span>
          <svg aria-hidden="true" viewBox="0 0 20 20" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M10 16V4M4.5 9.5 10 4l5.5 5.5" />
          </svg>
        </button>
      </div>
      <p id={`${id}-hint`} className="mt-2 hidden px-2 text-xs text-muted sm:block">
        Enter to send · Shift+Enter for a new line
      </p>
    </form>
  );
}
