import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { OfferCard } from "@/components/OfferCard";
import { makeOffer } from "@/test/fixtures";

function row(term: string) {
  const dt = screen.getByText(term, { selector: "dt" });
  return dt.nextElementSibling as HTMLElement;
}

describe("OfferCard", () => {
  it("labels a quote as not booked and shows the saving", () => {
    render(<OfferCard offer={makeOffer()} best />);

    const card = screen.getByRole("article", { name: "Rideau Inn" });
    expect(within(card).getByText("Best offer")).toBeInTheDocument();
    expect(within(card).getByText("Quote, not booked")).toBeInTheDocument();
    expect(within(card).queryByText("Booked")).not.toBeInTheDocument();
    expect(within(card).getByText("Negotiated total")).toBeInTheDocument();
    expect(within(card).getByText(/^CAD\s366\.00$/)).toBeInTheDocument();
    expect(within(card).getByText(/CAD\s34\.00 below the first quote of CAD\s400\.00/)).toBeInTheDocument();
    expect(row("Hotel reference (not a booking)")).toHaveTextContent("ENQ-123");
    expect(row("Evidence")).toHaveTextContent("Reference id given");
  });

  it("shows Booked only when told the booking is confirmed", () => {
    render(<OfferCard offer={makeOffer()} booked />);
    expect(screen.getByText("Booked")).toBeInTheDocument();
    expect(screen.queryByText("Quote, not booked")).not.toBeInTheDocument();
  });

  it("prints unknown values as Unknown, never as 0", () => {
    render(
      <OfferCard
        offer={makeOffer({
          hotelName: null,
          hotelAddress: null,
          negotiatedTotal: null,
          initialQuotedTotal: null,
          hst: null,
          municipalAccommodationTax: null,
          mandatoryFees: null,
          taxInclusion: null,
          cancellationTerms: null,
          depositTerms: null,
          enquiryId: null,
          evidenceStatus: null,
        })}
      />,
    );

    expect(screen.getByRole("heading", { name: "Unnamed hotel" })).toBeInTheDocument();
    expect(screen.getByText("Address unknown")).toBeInTheDocument();
    expect(screen.getByText("Quoted total")).toBeInTheDocument();
    expect(screen.getByText("Tax status unknown")).toBeInTheDocument();
    for (const term of ["First quoted total", "Mandatory fees", "Cancellation", "Deposit", "Quote valid until", "Evidence"]) {
      expect(row(term), term).toHaveTextContent(/^Unknown$/);
    }
    expect(row("Hotel reference (not a booking)")).toHaveTextContent("None given");
    expect(document.body).not.toHaveTextContent(/\b0\.00\b/);
  });

  it("does not show a saving when the price went up", () => {
    render(<OfferCard offer={makeOffer({ initialQuotedTotal: 300, negotiatedTotal: 366 })} />);
    expect(screen.queryByText(/below the first quote/)).not.toBeInTheDocument();
  });

  it("flags a non-CAD quote as excluded from comparisons", () => {
    render(<OfferCard offer={makeOffer({ currency: "USD" })} />);
    expect(screen.getByRole("note")).toHaveTextContent("Quoted in USD, not CAD");
  });
});
