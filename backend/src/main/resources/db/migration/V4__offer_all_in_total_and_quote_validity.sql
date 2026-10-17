-- The all-in total exactly as the hotel stated it. comparable_total is still computed in Java:
-- the sum of the components when all are known, otherwise this total if it includes taxes.
ALTER TABLE hotel_offers ADD COLUMN all_in_total numeric(12, 2);

-- Quote validity as the hotel stated it. quote_valid_until is now computed in Java from the call
-- end and quote_validity_hours; earlier values were produced by the language model and are not
-- trustworthy (one run returned 2424-11-18), so they are cleared.
ALTER TABLE hotel_offers ADD COLUMN quote_validity_text text;
ALTER TABLE hotel_offers ADD COLUMN quote_validity_hours numeric(8, 2);
UPDATE hotel_offers SET quote_valid_until = NULL;
