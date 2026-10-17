"use client";

import { useId, useState } from "react";

import { formatBytes, formatInstant, formatMoney, formatStayDate, humanize } from "@/components/format";
import type { TaskReport } from "@/models/task";

/**
 * The HTML body runs in an iframe with an empty `sandbox` (no scripts, forms, popups or same-origin
 * access) and a CSP that blocks every network request, so remote images and tracking pixels never
 * load. It is never injected into this page.
 */
function sandboxedDocument(bodyHtml: string): string {
  return `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src data:"><style>body{margin:16px;font:15px/1.55 system-ui,sans-serif;color:#14222c;background:#fff}img{max-width:100%;height:auto}</style></head><body>${bodyHtml}</body></html>`;
}

/** Only http(s) links are offered; anything else (javascript:, data:) is dropped. */
function safeHttpUrl(value: string | null | undefined): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === "https:" || url.protocol === "http:" ? url.href : null;
  } catch {
    return null;
  }
}

/** Delivery status from the mail provider: PENDING, ACCEPTED, DELIVERED, DELAYED, BOUNCED or FAILED. */
function deliveryTone(status: string | null): { label: string; cls: string } {
  const s = status?.toUpperCase() ?? "";
  if (!s) return { label: "Status unknown", cls: "border-line text-muted" };
  if (/FAIL|BOUNCE/.test(s)) return { label: humanize(s), cls: "border-alarm/40 bg-alarm-soft text-alarm" };
  if (/PEND|DELAY/.test(s)) return { label: humanize(s), cls: "border-lamp-line bg-lamp-soft text-lamp" };
  return { label: humanize(s), cls: "border-pine/30 bg-pine-soft text-pine" };
}

const isoDate = (value: string | null | undefined) => (value && /^\d{4}-\d{2}-\d{2}$/.test(value) ? formatStayDate(value) : (value ?? null));

/** The result email exactly as sent (a stored snapshot): envelope, key facts, links, body, transcripts. */
export function EmailPreview({ report }: { report: TaskReport }) {
  const id = useId();
  const hasHtml = Boolean(report.bodyHtml?.trim());
  const [view, setView] = useState<"html" | "text">(hasHtml ? "html" : "text");
  const fields = report.fields ?? {};
  const recording = safeHttpUrl(fields.recordingUrl);
  const conversation = safeHttpUrl(fields.conversationUrl);
  const transcripts = (report.transcripts ?? [])
    .map((t, i) => ({
      key: t.conversationId || `t${i}`,
      title: t.hotelName || `Call ${i + 1}`,
      at: t.startedAt ?? null,
      text: t.text ?? "",
      recording: safeHttpUrl(t.recordingUrl),
      conversation: safeHttpUrl(t.conversationUrl),
    }))
    .filter((t) => t.text.trim());
  const attachments = (report.attachments ?? [])
    .map((a) =>
      typeof a === "string"
        ? { name: a, size: null }
        : { name: (a.fileName ?? a.name ?? "").trim(), size: formatBytes(a.sizeBytes ?? a.size) },
    )
    .filter((a) => a.name);
  const delivery = deliveryTone(report.deliveryStatus);
  const cc = (report.cc ?? []).filter(Boolean);
  const stay = [isoDate(fields.checkIn), isoDate(fields.checkOut)].filter(Boolean).join(" to ");
  const t = report.tokens;
  const tokenDetail = [
    t?.openAiPromptTokens != null ? `${t.openAiPromptTokens.toLocaleString("en-CA")} prompt` : null,
    t?.openAiCompletionTokens != null ? `${t.openAiCompletionTokens.toLocaleString("en-CA")} completion` : null,
    t?.elevenLabsCallSeconds != null ? `${t.elevenLabsCallSeconds.toLocaleString("en-CA")} s of calls` : null,
  ]
    .filter(Boolean)
    .join(" · ");

  const facts: Array<{ term: string; value: string; href?: string; mono?: boolean; note?: string }> = [];
  if (fields.hotelName) facts.push({ term: "Hotel", value: fields.hotelName });
  if (fields.hotelAddress) facts.push({ term: "Address", value: fields.hotelAddress });
  if (fields.hotelPhone) facts.push({ term: "Phone", value: fields.hotelPhone, href: `tel:${fields.hotelPhone.replace(/[^\d+]/g, "")}`, mono: true });
  // priceNote is the email's full price text ("366.00 CAD all-in, availability confirmed, not booked").
  if (fields.priceNote?.trim()) facts.push({ term: "Price", value: fields.priceNote.trim() });
  else if (fields.price != null) facts.push({ term: "Price", value: formatMoney(fields.price, fields.currency || "CAD"), mono: true });
  if (stay) facts.push({ term: "Stay", value: stay });
  if (fields.reservationNumber) facts.push({ term: "Hotel reference (not a booking)", value: fields.reservationNumber, mono: true });
  else if (fields.hotelName) facts.push({ term: "Hotel reference", value: "None provided (no reservation made, as instructed)" });

  return (
    <article aria-labelledby={`${id}-subject`} className="slip mt-3 overflow-hidden rounded-lg border border-line bg-paper">
      <header className="border-b border-line px-4 pt-4 pb-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="font-mono text-[0.7rem] tracking-[0.14em] text-muted uppercase">The email the agent sent</p>
          <span className={`rounded-full border px-2 py-0.5 text-xs font-medium ${delivery.cls}`}>{delivery.label}</span>
        </div>
        <h3 id={`${id}-subject`} className="mt-2 font-display text-xl leading-snug text-ink">
          {report.subject || "(No subject)"}
        </h3>
        <dl className="mt-3 grid grid-cols-[3.5rem_1fr] gap-x-3 gap-y-1 text-sm">
          {report.from && <Envelope term="From" value={report.from} />}
          <Envelope term="To" value={report.to || "Unknown"} />
          {cc.length > 0 && <Envelope term="CC" value={cc.join(", ")} />}
          <Envelope term="Sent" value={report.sentAt ? formatInstant(report.sentAt) : "Not sent yet"} />
        </dl>
      </header>

      {(facts.length > 0 || recording || conversation) && (
        <section aria-label="Key details" className="border-b border-line bg-mist/50 px-4 py-3">
          {facts.length > 0 && (
            <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
              {facts.map((f) => (
                <div key={f.term} className="min-w-0">
                  <dt className="text-muted">{f.term}</dt>
                  <dd className={`break-words text-ink ${f.mono ? "font-mono" : ""}`}>
                    {f.href ? (
                      <a href={f.href} className="focus-ring rounded-sm underline decoration-line underline-offset-4 hover:decoration-lake">
                        {f.value}
                      </a>
                    ) : (
                      f.value
                    )}
                    {f.note && <span className="block font-sans text-xs text-muted">{f.note}</span>}
                  </dd>
                </div>
              ))}
            </dl>
          )}
          {(recording || conversation) && (
            <div className="mt-3 flex flex-wrap gap-2">
              {recording && (
                <a
                  href={recording}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="focus-ring inline-flex items-center gap-2 rounded-full bg-pine px-4 py-2 text-sm font-semibold text-paper hover:brightness-110"
                >
                  <svg aria-hidden="true" viewBox="0 0 16 16" className="h-3.5 w-3.5" fill="currentColor">
                    <path d="M4 2.8v10.4a.6.6 0 0 0 .9.5l8.3-5.2a.6.6 0 0 0 0-1L4.9 2.3a.6.6 0 0 0-.9.5Z" />
                  </svg>
                  Play call recording
                  <span className="sr-only">(opens in a new tab)</span>
                </a>
              )}
              {conversation && (
                <a
                  href={conversation}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="focus-ring inline-flex items-center gap-2 rounded-full border border-line bg-paper px-4 py-2 text-sm font-semibold text-ink hover:border-lake"
                >
                  Open the call conversation
                  <span className="sr-only">(opens in a new tab)</span>
                </a>
              )}
            </div>
          )}
        </section>
      )}

      {attachments.length > 0 && (
        <section aria-labelledby={`${id}-attachments`} className="border-b border-line px-4 py-3">
          <h4 id={`${id}-attachments`} className="font-mono text-[0.7rem] tracking-[0.14em] text-muted uppercase">
            Attachments ({attachments.length})
          </h4>
          <ul className="mt-2 flex flex-wrap gap-2">
            {attachments.map((a, i) => (
              <li
                key={`${a.name}-${i}`}
                className="inline-flex max-w-full items-center gap-2 rounded-md border border-line bg-mist/40 px-2.5 py-1.5 text-sm text-ink"
              >
                <svg aria-hidden="true" viewBox="0 0 16 16" className="h-3.5 w-3.5 shrink-0 text-muted" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M10.5 4.5 5.4 9.6a1.5 1.5 0 0 0 2.1 2.1l5.4-5.4a3 3 0 0 0-4.2-4.2L3.3 7.5a4.5 4.5 0 0 0 6.4 6.4l4-4" />
                </svg>
                <span className="min-w-0 truncate" title={a.name}>
                  {a.name}
                </span>
                {a.size && <span className="shrink-0 font-mono text-xs text-muted">{a.size}</span>}
              </li>
            ))}
          </ul>
        </section>
      )}

      <section aria-label="Email body" className="px-4 py-3">
        {hasHtml && (
          <div role="group" aria-label="Email view" className="mb-3 inline-flex rounded-md border border-line p-0.5 text-xs">
            {(["html", "text"] as const).map((v) => (
              <button
                key={v}
                type="button"
                aria-pressed={view === v}
                onClick={() => setView(v)}
                className={`focus-ring rounded px-2.5 py-1 font-medium ${view === v ? "bg-ink text-paper" : "text-muted hover:text-ink"}`}
              >
                {v === "html" ? "As sent" : "Plain text"}
              </button>
            ))}
          </div>
        )}
        {view === "html" && hasHtml ? (
          <>
            <iframe
              title={`Email body: ${report.subject ?? "result email"}`}
              sandbox=""
              referrerPolicy="no-referrer"
              srcDoc={sandboxedDocument(report.bodyHtml ?? "")}
              className="h-[28rem] w-full rounded-md border border-line bg-white"
            />
            <p className="mt-2 text-xs text-muted">Images and links are turned off in this preview.</p>
          </>
        ) : report.bodyText?.trim() ? (
          // Scrolls, so it must be focusable for keyboard users.
          <pre
            tabIndex={0}
            aria-label="Email body, plain text"
            className="focus-ring max-h-[28rem] overflow-auto rounded-md border border-line bg-mist/40 p-4 font-sans text-sm leading-relaxed break-words whitespace-pre-wrap text-ink">
            {report.bodyText}
          </pre>
        ) : (
          <p className="text-sm text-muted italic">The email body isn&apos;t available.</p>
        )}
      </section>

      {transcripts.length > 0 && (
        <section aria-label="Call transcripts" className="border-t border-line px-4 py-3">
          <h4 className="font-mono text-[0.7rem] tracking-[0.14em] text-muted uppercase">Call transcripts</h4>
          <div className="mt-2 flex flex-col gap-2">
            {transcripts.map((t) => (
              <details key={t.key} className="group rounded-md border border-line bg-mist/40">
                <summary className="focus-ring flex cursor-pointer list-none items-center justify-between gap-3 rounded-md px-3 py-2 text-sm font-medium text-ink">
                  <span>
                    {t.title}
                    {t.at && <span className="ml-2 font-mono text-xs font-normal text-muted">{formatInstant(t.at)}</span>}
                  </span>
                  <span aria-hidden="true" className="text-muted transition group-open:rotate-90">›</span>
                </summary>
                {(t.recording || t.conversation) && (
                  <p className="flex flex-wrap gap-4 border-t border-line px-3 py-2 text-sm">
                    {t.recording && (
                      <a href={t.recording} target="_blank" rel="noopener noreferrer" className="focus-ring rounded-sm text-lake underline underline-offset-4">
                        Recording<span className="sr-only"> of the call with {t.title} (opens in a new tab)</span>
                      </a>
                    )}
                    {t.conversation && (
                      <a href={t.conversation} target="_blank" rel="noopener noreferrer" className="focus-ring rounded-sm text-lake underline underline-offset-4">
                        Conversation<span className="sr-only"> with {t.title} (opens in a new tab)</span>
                      </a>
                    )}
                  </p>
                )}
                <pre
                  tabIndex={0}
                  aria-label={`Transcript: ${t.title}`}
                  className="focus-ring max-h-80 overflow-auto border-t border-line px-3 py-2 font-sans text-sm leading-relaxed break-words whitespace-pre-wrap text-ink"
                >
                  {t.text}
                </pre>
              </details>
            ))}
          </div>
        </section>
      )}

      {typeof report.tokensSpent === "number" && (
        <p className="border-t border-line px-4 py-2 font-mono text-xs text-muted">
          AI tokens used for this search: {report.tokensSpent.toLocaleString("en-CA")}
          {tokenDetail && ` (${tokenDetail})`}
        </p>
      )}
    </article>
  );
}

function Envelope({ term, value }: { term: string; value: string }) {
  return (
    <>
      <dt className="text-muted">{term}</dt>
      <dd className="min-w-0 break-words text-ink">{value}</dd>
    </>
  );
}
