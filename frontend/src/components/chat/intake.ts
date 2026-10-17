/**
 * The conversational intake, as pure functions: how the user's chat messages become one prompt,
 * and how the backend's 400/422 answers become the agent's next question. No React, no I/O.
 */
import type { FieldError } from "@/models/problem-detail";

/** The backend's limit on `prompt` (`PromptTaskRequest.MAX_LENGTH`). */
export const MAX_PROMPT_LENGTH = 2000;

/** Same pattern the backend uses to find the email address. */
const EMAIL_SOURCE = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}";
const hasEmail = (text: string) => new RegExp(EMAIL_SOURCE).test(text);

export function findEmails(text: string): string[] {
  return [...new Set((text.match(new RegExp(EMAIL_SOURCE, "g")) ?? []).map((e) => e.toLowerCase()))];
}

/**
 * Joins the user's messages, oldest first, into the prompt sent to `POST /api/tasks/prompt`.
 * The latest message that contains an email wins and addresses in earlier messages are dropped,
 * so a correction ("use bob@y.ca instead") reaches the backend as the only address and can't be
 * misread as a second recipient.
 */
export function buildPrompt(messages: readonly string[]): string {
  let lastWithEmail = -1;
  messages.forEach((m, i) => {
    if (hasEmail(m)) lastWithEmail = i;
  });
  return messages
    .map((m, i) => (i < lastWithEmail ? m.replace(new RegExp(EMAIL_SOURCE, "g"), "").replace(/[ \t]{2,}/g, " ") : m).trim())
    .filter(Boolean)
    .join("\n");
}

/** The agent's reply to a rejected prompt. `choices` become chips; picking one sends it as the user's reply. */
export interface FollowUp {
  text: string;
  choices?: Array<{ label: string; reply: string }>;
}

type AskedField = "city" | "checkIn" | "email";

const ORDER: AskedField[] = ["city", "checkIn", "email"];

/** Noun phrase used when several things are missing at once. */
const NEEDS: Record<AskedField, string> = {
  city: "the Ontario city you'd like to stay in",
  checkIn: "your check-in date",
  email: "the email address for the results",
};

/** Question used when exactly one thing is missing. */
const QUESTIONS: Record<AskedField, string> = {
  city: "Which Ontario city would you like to stay in?",
  checkIn: "Which day would you like to check in?",
  email: "What email address should I send the results to?",
};

/** Maps an error to a field we know how to ask for. Old string-only errors carry no field, so guess from the text. */
function askedFieldOf(error: FieldError): AskedField | null {
  const field = error.field?.toLowerCase() ?? "";
  if (field === "email" || field === "destinationemail") return "email";
  if (field === "city") return "city";
  if (field === "checkin") return "checkIn";
  if (field) return null;
  if (/e-?mail/i.test(error.message)) return "email";
  if (/\bcity\b|location|municipalit/i.test(error.message)) return "city";
  if (/check-?in/i.test(error.message)) return "checkIn";
  return null;
}

/** True for "you didn't say X" errors, as opposed to "X is wrong" errors whose own message is the better reply. */
function isMissing(message: string): boolean {
  return /^(include the|say which|.*\b(is )?required\b|.*\bmissing\b|no .* (found|given))/i.test(message.trim());
}

function sentence(message: string): string {
  const m = message.trim();
  if (!m) return m;
  const first = m.charAt(0).toUpperCase() + m.slice(1);
  return /[.?!]$/.test(first) ? first : `${first}.`;
}

function joinWithAnd(items: string[]): string {
  if (items.length <= 1) return items.join("");
  return `${items.slice(0, -1).join(", ")} and ${items[items.length - 1]}`;
}

/** A chip that names a city; the label doubles as the reply so the chat shows exactly what was picked. */
const cityChoice = (city: string) => ({ label: `Search ${city}`, reply: `Search ${city}` });

/**
 * Turns a 400's errors into one conversational reply that asks only for what is missing.
 * `latestPrompt` is the prompt that was rejected; it supplies the choices when it had two emails.
 */
export function followUpForErrors(errors: readonly FieldError[], latestPrompt: string): FollowUp {
  const missing = new Set<AskedField>();
  const other: string[] = [];
  let multipleEmails = false;

  for (const error of errors) {
    const field = askedFieldOf(error);
    if (field === "email" && /only one email/i.test(error.message)) multipleEmails = true;
    else if (field && isMissing(error.message)) missing.add(field);
    else if (error.message.trim()) other.push(sentence(error.message));
  }

  const asks = ORDER.filter((f) => missing.has(f));
  const parts: string[] = [];
  if (asks.length === 1) parts.push(`Thanks. ${QUESTIONS[asks[0]]}`);
  else if (asks.length > 1) parts.push(`Thanks. To start, I still need ${joinWithAnd(asks.map((f) => NEEDS[f]))}.`);
  parts.push(...[...new Set(other)]);

  if (multipleEmails) {
    const emails = findEmails(latestPrompt);
    parts.push("I found more than one email address. Which one should I send the results to?");
    return { text: parts.join(" "), choices: emails.map((e) => ({ label: `Use ${e}`, reply: e })) };
  }

  if (parts.length === 0) parts.push("Something in that request didn't work. Could you say it another way?");
  return { text: parts.join(" ") };
}

/** The reply to a 422: the city is outside Ontario, or its name matches several Ontario places. */
export function followUpForCity(detail: string | undefined, suggestions: readonly string[]): FollowUp {
  if (suggestions.length > 0) {
    return { text: `Which Ontario city did you mean?${detail ? ` ${sentence(detail)}` : ""}`, choices: suggestions.map(cityChoice) };
  }
  return {
    text: `I can only search hotels in Ontario.${detail ? ` ${sentence(detail)}` : ""} Which Ontario city should I search?`,
  };
}
