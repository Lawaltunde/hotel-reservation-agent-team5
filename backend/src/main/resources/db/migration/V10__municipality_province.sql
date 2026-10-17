-- Searches cover all of Canada. Seeded and earlier geocoded rows are Ontario.
ALTER TABLE municipalities ADD COLUMN province text NOT NULL DEFAULT 'ON';
