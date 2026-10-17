"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";

/** Within this many pixels of the bottom counts as "following" the conversation. */
const FOLLOW_THRESHOLD_PX = 96;

const prefersReducedMotion = () => typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/**
 * The scrolling conversation. It follows new content only while the user is at the bottom; if they
 * have scrolled up to read, it leaves them there and offers a "Jump to latest" button instead.
 * Changing `followKey` (the user just sent something) always brings the view to the bottom.
 */
export function ChatLog({ children, followKey, label }: { children: ReactNode; followKey: unknown; label: string }) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLOListElement>(null);
  const following = useRef(true);
  const [showJump, setShowJump] = useState(false);

  const scrollToBottom = (smooth: boolean) => {
    const el = scrollerRef.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior: smooth && !prefersReducedMotion() ? "smooth" : "auto" });
  };

  // Content grows as messages arrive and cards expand; follow it only if the user is following.
  useEffect(() => {
    const content = contentRef.current;
    if (!content) return;
    const observer = new ResizeObserver(() => {
      if (following.current) scrollToBottom(false);
      else setShowJump(true);
    });
    observer.observe(content);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    following.current = true;
    scrollToBottom(true);
  }, [followKey]);

  function handleScroll() {
    const el = scrollerRef.current;
    if (!el) return;
    following.current = el.scrollHeight - el.scrollTop - el.clientHeight < FOLLOW_THRESHOLD_PX;
    if (following.current) setShowJump(false);
  }

  return (
    <div className="relative min-h-0 flex-1">
      {/* A focusable region so keyboard users can scroll the conversation. */}
      <div
        ref={scrollerRef}
        onScroll={handleScroll}
        tabIndex={0}
        role="region"
        aria-label="Conversation"
        className="h-full overflow-y-auto overscroll-contain focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-lake"
      >
        <ol
          ref={contentRef}
          role="log"
          aria-live="polite"
          aria-relevant="additions"
          aria-label={label}
          className="mx-auto flex max-w-3xl flex-col gap-4 px-4 py-6 sm:px-6"
        >
          {children}
        </ol>
      </div>
      {showJump && (
        <button
          type="button"
          onClick={() => {
            following.current = true;
            setShowJump(false);
            scrollToBottom(true);
          }}
          className="focus-ring absolute bottom-3 left-1/2 -translate-x-1/2 rounded-full border border-line bg-paper px-4 py-1.5 text-sm font-medium text-ink shadow-md"
        >
          Jump to latest <span aria-hidden="true">↓</span>
        </button>
      )}
    </div>
  );
}
