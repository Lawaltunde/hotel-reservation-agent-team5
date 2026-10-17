"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";

import { formatInstant, formatStayDate } from "@/components/format";
import { OfferCard } from "@/components/OfferCard";
import { ProgressTimeline } from "@/components/ProgressTimeline";
import { isConfirmedBooking, ResultSummary } from "@/components/ResultSummary";
import type { ProgressEvent, Task } from "@/models/task";
import { isTerminalStatus } from "@/models/task";
import type { ApiError } from "@/services/api-client";
import { subscribeToTask, type SubscriptionMode } from "@/services/task-service";

/** Reads `?id=` and shows that task. Must be rendered inside a `<Suspense>` boundary. */
export function TaskFromSearchParams() {
  const id = useSearchParams().get("id")?.trim();
  if (!id) {
    return (
      <div role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-amber-950">
        <h1 className="text-lg font-semibold">No search selected</h1>
        <p className="mt-1 text-sm">This page needs a search ID in the link.</p>
        <NewSearchLink />
      </div>
    );
  }
  // key: start from a clean state if the id changes
  return <TaskView key={id} taskId={id} />;
}

function TaskView({ taskId }: { taskId: string }) {
  const [task, setTask] = useState<Task | null>(null);
  const [events, setEvents] = useState<ProgressEvent[]>([]);
  const [mode, setMode] = useState<SubscriptionMode>("connecting");
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(
    () =>
      subscribeToTask(taskId, {
        onTask: (t) => {
          setTask(t);
          setError(null);
        },
        onProgress: (e) => setEvents((prev) => [...prev, e]),
        onModeChange: setMode,
        onError: setError,
      }),
    [taskId],
  );

  if (!task) {
    if (error?.status === 404) {
      return (
        <div role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-amber-950">
          <h1 className="text-lg font-semibold">Search not found</h1>
          <p className="mt-1 text-sm">We couldn&apos;t find a search with ID {taskId}. The link may be wrong or expired.</p>
          <NewSearchLink />
        </div>
      );
    }
    return (
      <div role="status" className="rounded-xl border border-slate-200 bg-white p-6">
        <p className="text-sm text-slate-600">{error ? `Can't reach the server yet: ${error.message}. Retrying…` : "Loading your search…"}</p>
      </div>
    );
  }

  const status = events.length > 0 ? events[events.length - 1].status : task.status;
  const guests = [
    `${task.rooms} ${task.rooms === 1 ? "room" : "rooms"}`,
    `${task.adults} ${task.adults === 1 ? "adult" : "adults"}`,
    task.children > 0 ? `${task.children} ${task.children === 1 ? "child" : "children"}` : null,
  ]
    .filter(Boolean)
    .join(", ");
  const otherOffers = task.offers.filter((o) => o.offerId !== task.bestOffer?.offerId);

  return (
    <div className="flex flex-col gap-6">
      <header>
        <p className="text-sm text-slate-500">
          <Link href="/" className="underline-offset-2 hover:underline">
            Hotel Deal Agent
          </Link>{" "}
          / Search
        </p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">{task.city}, Ontario</h1>
        <p className="mt-1 text-slate-600">
          {formatStayDate(task.checkIn)} to {formatStayDate(task.checkOut)} · {guests}
        </p>
        <p className="mt-1 text-xs text-slate-500">
          Deadline {formatInstant(task.deadline)} · Search ID <span className="font-mono">{task.taskId}</span>
        </p>
      </header>

      {error && (
        <p role="status" className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
          Lost contact with the server ({error.message}). Still trying…
        </p>
      )}

      <ResultSummary task={{ ...task, status }} />

      <ProgressTimeline status={status} authorizationMode={task.authorizationMode} events={events} mode={mode} />

      {task.bestOffer || otherOffers.length > 0 ? (
        <section aria-labelledby="offers-heading" className="flex flex-col gap-4">
          <div>
            <h2 id="offers-heading" className="text-lg font-semibold">
              Quotes
            </h2>
            <p className="text-sm text-slate-600">
              Prices are all-in totals in Canadian dollars. A quote is not a booking; nothing is reserved unless a card says
              &quot;Booked&quot;.
            </p>
          </div>
          {task.bestOffer && <OfferCard offer={task.bestOffer} best booked={isConfirmedBooking(task)} />}
          {otherOffers.map((offer) => (
            <OfferCard key={offer.offerId} offer={offer} />
          ))}
        </section>
      ) : (
        isTerminalStatus(status) && (
          <section aria-labelledby="offers-heading" className="rounded-xl border border-dashed border-slate-300 bg-white p-5">
            <h2 id="offers-heading" className="text-lg font-semibold">
              Quotes
            </h2>
            <p className="mt-1 text-sm text-slate-600">No quotes were captured for this search.</p>
          </section>
        )
      )}

      <NewSearchLink />
    </div>
  );
}

function NewSearchLink() {
  return (
    <Link
      href="/"
      className="mt-4 inline-flex w-fit items-center rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
    >
      Start a new search
    </Link>
  );
}
