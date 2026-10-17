import { formatInstant, formatMoney, formatStayDate } from "@/components/format";
import type { StatableField, Task, TaskRequest } from "@/models/task";

const NOT_GIVEN = "Not given";

function plural(n: number, one: string, many: string) {
  return `${n} ${n === 1 ? one : many}`;
}

/** Nights between two `YYYY-MM-DD` dates, or null if either is missing or invalid. */
export function nightsBetween(checkIn: string | null, checkOut: string | null): number | null {
  if (!checkIn || !checkOut) return null;
  const n = Math.round((Date.parse(`${checkOut}T00:00:00Z`) - Date.parse(`${checkIn}T00:00:00Z`)) / 86_400_000);
  return Number.isFinite(n) && n > 0 ? n : null;
}

const UNSTATED = "Not specified — the agent will ask the hotel";

/** False when the user didn't give it (`request.stated`), or it is null. Without `request` (older backends) values are real. */
function isStated(r: TaskRequest | null, field: StatableField): boolean {
  if (!r) return true;
  if (r.stated?.[field] === false) return false;
  return r[field] !== null && r[field] !== undefined;
}

function guestsText(rooms: number | null, adults: number | null, children: number | null): string {
  return [
    rooms != null ? plural(rooms, "room", "rooms") : null,
    adults != null ? plural(adults, "adult", "adults") : null,
    children ? plural(children, "child", "children") : null,
  ]
    .filter(Boolean)
    .join(", ");
}

/**
 * How the backend read the prompt, as a reservation slip. Anything the user left out says so,
 * plus what the agent will ask hotels for instead (`askedFor`); a default is never shown as
 * the user's choice.
 */
export function UnderstoodCard({ task }: { task: Task }) {
  const r = task.request ?? null;
  const asked = r?.askedFor ?? {};
  const date = (field: "checkIn" | "checkOut"): string | null => (isStated(r, field) ? (r?.[field] ?? task[field]) : null);
  const count = (field: "rooms" | "adults" | "children"): number | null => (isStated(r, field) ? (r?.[field] ?? task[field]) : null);
  const askedDate = (field: "checkIn" | "checkOut") => (typeof asked[field] === "string" ? (asked[field] as string) : null);
  const askedCount = (field: "rooms" | "adults" | "children") => (typeof asked[field] === "number" ? (asked[field] as number) : null);

  const city = r?.city ?? task.city;
  const region = r?.provinceName?.trim() || r?.province?.trim() || null;
  const checkIn = date("checkIn");
  const checkOut = date("checkOut");
  const nights = nightsBetween(checkIn, checkOut);
  const askedNights = nights ? null : nightsBetween(checkIn ?? askedDate("checkIn"), checkOut ?? askedDate("checkOut"));

  const unstatedDate = (field: "checkIn" | "checkOut") => {
    const asking = askedDate(field);
    return asking ? `Not specified — the agent will ask for ${formatStayDate(asking)}` : UNSTATED;
  };
  const guests = guestsText(count("rooms"), count("adults"), count("children"));
  const askingGuests = guestsText(askedCount("rooms"), askedCount("adults"), askedCount("children"));

  const rows: Array<{ term: string; value: string; mono?: boolean; muted?: boolean }> = [
    { term: "Check-in", value: checkIn ? formatStayDate(checkIn) : unstatedDate("checkIn"), muted: !checkIn },
    { term: "Check-out", value: checkOut ? formatStayDate(checkOut) : unstatedDate("checkOut"), muted: !checkOut },
    {
      term: "Guests",
      value: guests || (askingGuests ? `Not specified — the agent will ask for ${askingGuests}` : UNSTATED),
      muted: !guests,
    },
  ];
  if (r) {
    // Only repeat a nightly figure the user actually gave; never derive one.
    const perNight = r.maxBudgetPerNight != null ? ` (${formatMoney(r.maxBudgetPerNight)}/night)` : "";
    rows.push(
      {
        term: "Budget",
        value: isStated(r, "maxBudget") ? `Up to ${formatMoney(r.maxBudget)} for the stay${perNight}` : "No limit given",
        muted: !isStated(r, "maxBudget"),
      },
      { term: "Must have", value: r.amenities && r.amenities.length > 0 ? r.amenities.join(", ") : "Nothing specific", muted: !r.amenities?.length },
    );
    if (r.notes?.trim()) rows.push({ term: "Preferences", value: r.notes.trim() });
    rows.push(
      r.emailSource === "ORGANIZER_FALLBACK"
        ? { term: "Results to", value: `${r.email ?? NOT_GIVEN} (organizer inbox)`, mono: false }
        : { term: "Results to", value: r.email ?? NOT_GIVEN, mono: r.email !== null, muted: r.email === null },
    );
  }
  if (task.deadline) rows.push({ term: "Results expected by", value: formatInstant(task.deadline) });
  const assumptions = (r?.assumptions ?? []).filter((x) => x.trim());

  return (
    <section aria-label="What I understood" className="slip mt-3 overflow-hidden rounded-lg border border-line bg-mist/50">
      <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-1 border-b border-dashed border-line px-4 pt-4 pb-3">
        <p className="font-display text-2xl leading-tight text-ink">
          {city ?? NOT_GIVEN}
          {region && <span className="text-muted">, {region}</span>}
        </p>
        <p className="font-mono text-sm text-pine">
          {nights ? plural(nights, "night", "nights") : askedNights ? `Asking for ${plural(askedNights, "night", "nights")}` : "Nights not specified"}
        </p>
      </div>
      <dl className="grid gap-x-6 px-4 py-2 text-sm sm:grid-cols-2">
        {rows.map(({ term, value, mono, muted }) => (
          <div key={term} className="flex justify-between gap-4 border-b border-line/60 py-2 last:border-0 sm:block sm:border-0 sm:py-1.5">
            <dt className="text-muted">{term}</dt>
            <dd className={`text-right break-words sm:text-left ${muted || value === NOT_GIVEN ? "text-muted italic" : "text-ink"} ${mono ? "font-mono text-[0.8rem]" : ""}`}>
              {value}
            </dd>
          </div>
        ))}
      </dl>
      {assumptions.length > 0 && (
        <div className="border-t border-dashed border-line px-4 py-3 text-sm">
          <p className="font-mono text-[0.7rem] tracking-[0.14em] text-muted uppercase">What the agent assumed</p>
          <ul className="mt-1.5 list-disc space-y-0.5 pl-5 text-ink">
            {assumptions.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
