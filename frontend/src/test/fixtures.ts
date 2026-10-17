import type { Offer, Task } from "@/models/task";

/** A finished enquiry task with no offers; override what a test cares about. */
export function makeTask(overrides: Partial<Task> = {}): Task {
  return {
    taskId: "11111111-1111-4111-8111-111111111111",
    status: "COMPLETED",
    outcome: "NO_ELIGIBLE_HOTELS",
    city: "Ottawa",
    checkIn: "2026-11-20",
    checkOut: "2026-11-22",
    rooms: 1,
    adults: 2,
    children: 0,
    authorizationMode: "ENQUIRY_ONLY",
    deadline: "2026-11-10T18:00:00Z",
    candidatesScreened: 0,
    callsPlaced: 0,
    bestOffer: null,
    offers: [],
    createdAt: "2026-11-02T12:00:00Z",
    updatedAt: "2026-11-02T12:00:05Z",
    ...overrides,
  };
}

/** A complete CAD quote with an enquiry reference; override what a test cares about. */
export function makeOffer(overrides: Partial<Offer> = {}): Offer {
  return {
    offerId: "offer-1",
    hotelId: "hotel-1",
    hotelName: "Rideau Inn",
    hotelAddress: "1 Main St, Ottawa, ON",
    roomType: "Queen room",
    initialQuotedTotal: 400,
    negotiatedTotal: 366,
    currency: "CAD",
    hst: 39,
    municipalAccommodationTax: 12,
    mandatoryFees: 15,
    taxInclusion: "EXCLUDED",
    cancellationTerms: "Free cancellation until 48h before",
    depositTerms: "No deposit",
    enquiryId: "ENQ-123",
    quoteValidUntil: null,
    evidenceStatus: "REFERENCE_ID_GIVEN",
    ...overrides,
  };
}
