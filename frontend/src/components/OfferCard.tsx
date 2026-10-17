import { formatMoney, formatQuoteValidity, humanize, taxInclusionLabel, UNKNOWN } from "@/components/format";
import type { Offer } from "@/models/task";

interface OfferCardProps {
  offer: Offer;
  /** Highlights the cheapest eligible offer. */
  best?: boolean;
  /** True only when the task outcome is a hotel-confirmed booking for this offer. */
  booked?: boolean;
}

/**
 * One hotel quote. Per the honesty rule, a quote is always labelled as a quote: the enquiry ID
 * is a hotel reference, not a booking, and unknown amounts read "Unknown", never 0.
 *
 * The headline is the comparable all-in total (`negotiatedTotal`). When that is unknown the card
 * says so instead of promoting a partial figure such as a pre-tax room rate.
 */
export function OfferCard({ offer, best = false, booked = false }: OfferCardProps) {
  const currency = offer.currency || "CAD";
  const total = offer.negotiatedTotal;
  const saving =
    offer.initialQuotedTotal !== null && total !== null ? offer.initialQuotedTotal - total : null;
  const validity = formatQuoteValidity(offer);
  const headingId = `offer-${offer.offerId}`;
  const totalId = `${headingId}-total`;

  return (
    <article
      aria-labelledby={headingId}
      aria-describedby={totalId}
      className={`rounded-xl border bg-white p-5 ${best ? "border-emerald-300 ring-1 ring-emerald-200" : "border-slate-200"}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            {best && <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-800">Best offer</span>}
            {booked ? (
              <span className="rounded-full bg-sky-100 px-2 py-0.5 text-xs font-medium text-sky-800">Booked</span>
            ) : (
              <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">Quote, not booked</span>
            )}
          </div>
          <h3 id={headingId} className="mt-2 text-base font-semibold text-slate-900">
            {offer.hotelName ?? "Unnamed hotel"}
          </h3>
          <p className="text-sm text-slate-600">{offer.hotelAddress ?? "Address unknown"}</p>
          {offer.roomType && <p className="mt-1 text-sm text-slate-600">{offer.roomType}</p>}
        </div>
        <div id={totalId} className="text-right">
          <p className="text-xs text-slate-500">All-in total, {currency}</p>
          {total !== null ? (
            <>
              <p className="text-2xl font-semibold tabular-nums text-slate-900">{formatMoney(total, currency)}</p>
              <p className="text-xs text-slate-600">Room, HST, municipal tax and mandatory fees</p>
            </>
          ) : (
            <>
              <p className="text-2xl font-semibold text-slate-500 italic">{UNKNOWN}</p>
              <p className="max-w-56 text-xs text-slate-600">Not every tax or fee was stated, so this quote can&apos;t be ranked.</p>
            </>
          )}
          {saving !== null && saving > 0 && (
            <p className="mt-1 text-xs font-medium text-emerald-700">
              {formatMoney(saving, currency)} below the first quote of {formatMoney(offer.initialQuotedTotal, currency)}
            </p>
          )}
        </div>
      </div>

      {currency !== "CAD" && (
        <p role="note" className="mt-3 rounded-md bg-amber-50 p-2 text-xs text-amber-900">
          Quoted in {currency}, not CAD, so it is excluded from cheapest-price comparisons.
        </p>
      )}

      {offer.totalsDisagree && (
        <p role="note" className="mt-3 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          <span className="font-medium">The figures don&apos;t quite add up.</span> The hotel stated an all-in total of{" "}
          {formatMoney(offer.allInTotal, currency)}, but the room rate, taxes and fees it gave sum to{" "}
          {formatMoney(total, currency)}. We used the summed figure; confirm the total with the hotel before relying on it.
        </p>
      )}

      {validity && <p className="mt-3 text-sm text-slate-700">{validity}</p>}

      <dl className="mt-4 grid grid-cols-1 gap-x-6 gap-y-2 border-t border-slate-100 pt-4 text-sm sm:grid-cols-2">
        <Row term="First quoted total" value={formatMoney(offer.initialQuotedTotal, currency)} />
        {offer.allInTotal !== undefined && <Row term="All-in total as stated by the hotel" value={formatMoney(offer.allInTotal, currency)} />}
        <Row term="Taxes in stated total" value={taxInclusionLabel(offer.taxInclusion)} />
        <Row term="HST" value={formatMoney(offer.hst, currency)} />
        <Row term="Municipal accommodation tax" value={formatMoney(offer.municipalAccommodationTax, currency)} />
        <Row term="Mandatory fees" value={formatMoney(offer.mandatoryFees, currency)} />
        <Row term="Cancellation" value={offer.cancellationTerms ?? UNKNOWN} />
        <Row term="Deposit" value={offer.depositTerms ?? UNKNOWN} />
        <Row term="Hotel reference (not a booking)" value={offer.enquiryId ?? "None given"} mono={offer.enquiryId !== null} />
        <Row term="Evidence" value={offer.evidenceStatus ? humanize(offer.evidenceStatus) : UNKNOWN} />
      </dl>
    </article>
  );
}

function Row({ term, value, mono = false }: { term: string; value: string; mono?: boolean }) {
  return (
    <div className="flex justify-between gap-4 sm:block">
      <dt className="text-slate-500">{term}</dt>
      <dd className={`text-right text-slate-900 sm:text-left ${value === UNKNOWN ? "text-slate-500 italic" : ""} ${mono ? "font-mono" : ""}`}>
        {value}
      </dd>
    </div>
  );
}
