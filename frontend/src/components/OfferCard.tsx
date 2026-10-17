import { ESTIMATE_NOTE, formatMoney, formatQuoteValidity, humanize, rankingPrice, taxInclusionLabel, UNKNOWN } from "@/components/format";
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
 * The headline is the ranking price: the all-in total, or a clearly marked estimate ("≈ … incl.
 * HST") when the hotel only gave a pre-tax rate. When neither exists the card says so instead of
 * promoting a partial figure.
 */
export function OfferCard({ offer, best = false, booked = false }: OfferCardProps) {
  const currency = offer.currency || "CAD";
  const { amount: total, estimated } = rankingPrice(offer);
  const validity = formatQuoteValidity(offer);
  const headingId = `offer-${offer.offerId}`;
  const totalId = `${headingId}-total`;

  return (
    <article
      aria-labelledby={headingId}
      aria-describedby={totalId}
      className={`rounded-lg border bg-paper p-5 ${best ? "border-pine/50 ring-1 ring-pine/30" : "border-line"}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            {best && <span className="rounded-full bg-pine-soft px-2 py-0.5 text-xs font-medium text-pine">Best offer</span>}
            {booked ? (
              <span className="rounded-full bg-lake-soft px-2 py-0.5 text-xs font-medium text-lake">Booked</span>
            ) : (
              <span className="rounded-full bg-line/60 px-2 py-0.5 text-xs font-medium text-ink">Quote, not booked</span>
            )}
          </div>
          <h3 id={headingId} className="mt-2 font-display text-xl text-ink">
            {offer.hotelName ?? "Unnamed hotel"}
          </h3>
          <p className="text-sm text-muted">{offer.hotelAddress ?? "Address unknown"}</p>
          {offer.roomType && <p className="mt-1 text-sm text-muted">{offer.roomType}</p>}
        </div>
        <div id={totalId} className="text-right">
          <p className="text-xs text-muted">{estimated ? `Estimated price, ${currency}` : `All-in total, ${currency}`}</p>
          {total !== null && estimated ? (
            <>
              <p className="font-mono text-2xl font-semibold tabular-nums text-ink">
                <span aria-hidden="true">≈ </span>
                <span className="sr-only">About </span>
                {formatMoney(total, currency)}
              </p>
              <p className="max-w-60 text-xs text-lamp">{ESTIMATE_NOTE}</p>
            </>
          ) : total !== null ? (
            <>
              <p className="font-mono text-2xl font-semibold tabular-nums text-ink">{formatMoney(total, currency)}</p>
              <p className="text-xs text-muted">Room, HST, municipal tax and mandatory fees</p>
            </>
          ) : (
            <>
              <p className="text-2xl font-semibold text-muted italic">{UNKNOWN}</p>
              <p className="max-w-56 text-xs text-muted">Not every tax or fee was stated, so this quote can&apos;t be ranked.</p>
            </>
          )}
        </div>
      </div>

      {currency !== "CAD" && (
        <p role="note" className="mt-3 rounded-md bg-lamp-soft p-2 text-xs text-ink">
          Quoted in {currency}, not CAD, so it is excluded from cheapest-price comparisons.
        </p>
      )}

      {offer.totalsDisagree && (
        <p role="note" className="mt-3 rounded-md border border-lamp-line bg-lamp-soft p-3 text-sm text-ink">
          <span className="font-medium">The figures don&apos;t quite add up.</span> The hotel stated an all-in total of{" "}
          {formatMoney(offer.allInTotal, currency)}, but the room rate, taxes and fees it gave sum to{" "}
          {formatMoney(offer.negotiatedTotal, currency)}. We used the summed figure; confirm the total with the hotel before relying on it.
        </p>
      )}

      {validity && <p className="mt-3 text-sm text-ink">{validity}</p>}

      <dl className="mt-4 grid grid-cols-1 gap-x-6 gap-y-2 border-t border-line/60 pt-4 text-sm sm:grid-cols-2">
        <Row term="First quoted total" value={formatMoney(offer.initialQuotedTotal, currency)} />
        {offer.allInTotal !== undefined && <Row term="All-in total as stated by the hotel" value={formatMoney(offer.allInTotal, currency)} />}
        <Row term="Taxes in stated total" value={taxInclusionLabel(offer.taxInclusion)} />
        {offer.roomSubtotal !== undefined && <Row term="Room, before taxes and fees" value={formatMoney(offer.roomSubtotal, currency)} />}
        <Row term={offer.salesTaxLabel?.trim() || "HST"} value={formatMoney(offer.salesTax ?? offer.hst, currency)} />
        <Row
          term={offer.levyLabel?.trim() || "Municipal accommodation tax"}
          value={formatMoney(offer.accommodationLevy ?? offer.municipalAccommodationTax, currency)}
        />
        <Row term="Mandatory fees" value={formatMoney(offer.mandatoryFees, currency)} />
        {offer.optionalFees !== undefined && (
          <Row term="Optional fees (not in all-in total)" value={formatMoney(offer.optionalFees, currency)} />
        )}
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
      <dt className="text-muted">{term}</dt>
      <dd className={`text-right text-ink sm:text-left ${value === UNKNOWN ? "text-muted italic" : ""} ${mono ? "font-mono" : ""}`}>
        {value}
      </dd>
    </div>
  );
}
