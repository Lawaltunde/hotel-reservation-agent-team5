-- The all-in total exactly as the hotel stated it. comparable_total is still computed in Java:
-- the sum of the components when all are known, otherwise this total if it includes taxes.
ALTER TABLE hotel_offers ADD COLUMN all_in_total numeric(12, 2);
