import { formatInstant, formatMoney, humanize, taxInclusionLabel, UNKNOWN } from "@/components/format";
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
 */
export function OfferCard({ offer, best = false, booked = false }: OfferCardProps) {
  const currency = offer.currency || "CAD";
  const total = offer.negotiatedTotal ?? offer.initialQuotedTotal;
  const saving =
    offer.initialQuotedTotal !== null && offer.negotiatedTotal !== null
      ? offer.initialQuotedTotal - offer.negotiatedTotal
      : null;
  const headingId = `offer-${offer.offerId}`;

  return (
    <article
      aria-labelledby={headingId}
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
        <div className="text-right">
          <p className="text-xs text-slate-500">{offer.negotiatedTotal !== null ? "Negotiated total" : "Quoted total"}</p>
          <p className="text-2xl font-semibold tabular-nums text-slate-900">{formatMoney(total, currency)}</p>
          <p className="text-xs text-slate-600">{taxInclusionLabel(offer.taxInclusion)}</p>
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

      <dl className="mt-4 grid grid-cols-1 gap-x-6 gap-y-2 border-t border-slate-100 pt-4 text-sm sm:grid-cols-2">
        <Row term="First quoted total" value={formatMoney(offer.initialQuotedTotal, currency)} />
        <Row term="HST" value={formatMoney(offer.hst, currency)} />
        <Row term="Municipal accommodation tax" value={formatMoney(offer.municipalAccommodationTax, currency)} />
        <Row term="Mandatory fees" value={formatMoney(offer.mandatoryFees, currency)} />
        <Row term="Cancellation" value={offer.cancellationTerms ?? UNKNOWN} />
        <Row term="Deposit" value={offer.depositTerms ?? UNKNOWN} />
        <Row term="Hotel reference (not a booking)" value={offer.enquiryId ?? "None given"} mono={offer.enquiryId !== null} />
        <Row term="Quote valid until" value={formatInstant(offer.quoteValidUntil)} />
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
