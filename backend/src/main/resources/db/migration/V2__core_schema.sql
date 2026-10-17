-- Hotel Deal Agent: core schema (README section 10). postgis and unaccent are enabled in V1.

-- ---------- Geography ----------
CREATE TABLE municipalities (
    id               uuid PRIMARY KEY,
    name             text        NOT NULL,
    name_key         text        NOT NULL,              -- lower(unaccent(name)) for lookups
    alias_keys       text[]      NOT NULL DEFAULT '{}',
    csd_code         text UNIQUE,                        -- Statistics Canada census subdivision
    centre           geography(Point, 4326) NOT NULL,
    boundary         geometry(MultiPolygon, 4326),
    time_zone        text        NOT NULL DEFAULT 'America/Toronto',
    mat_rate         numeric(6, 4),                      -- municipal accommodation tax as a fraction
    mat_rate_source  text,
    mat_effective_on date
);
CREATE INDEX idx_municipalities_name_key ON municipalities (name_key text_pattern_ops);
CREATE INDEX idx_municipalities_alias_keys ON municipalities USING gin (alias_keys);
CREATE INDEX idx_municipalities_boundary ON municipalities USING gist (boundary);

-- ---------- Ontario hotel index (open data) ----------
CREATE TABLE hotels (
    id               uuid PRIMARY KEY,
    canonical_key    text        NOT NULL UNIQUE,        -- stable key, e.g. 'osm:node/123'
    name             text        NOT NULL,
    address          text,
    municipality_id  uuid REFERENCES municipalities (id),
    location         geography(Point, 4326) NOT NULL,
    property_type    text        NOT NULL,
    stars            integer,
    brand            text,
    brand_wikidata   text,
    price_tier       text        NOT NULL DEFAULT 'UNKNOWN',
    phones           text[]      NOT NULL DEFAULT '{}',  -- E.164, best first
    phone_confidence double precision NOT NULL DEFAULT 0,
    website          text,
    operational      boolean     NOT NULL DEFAULT true,
    last_seen_at     timestamptz NOT NULL
);
CREATE INDEX idx_hotels_location ON hotels USING gist (location);
CREATE INDEX idx_hotels_municipality ON hotels (municipality_id);

CREATE TABLE hotel_source_records (
    id           uuid PRIMARY KEY,
    hotel_id     uuid        NOT NULL REFERENCES hotels (id) ON DELETE CASCADE,
    source       text        NOT NULL,
    source_id    text        NOT NULL,
    licence      text        NOT NULL,
    tags         jsonb       NOT NULL DEFAULT '{}',
    retrieved_at timestamptz NOT NULL,
    UNIQUE (source, source_id)
);

-- Google Places data is kept apart from open data and expires (Google terms).
CREATE TABLE hotel_enrichments (
    id                uuid PRIMARY KEY,
    hotel_id          uuid        NOT NULL REFERENCES hotels (id) ON DELETE CASCADE,
    provider          text        NOT NULL,
    provider_place_id text,
    verified_phone    text,
    business_status   text,
    formatted_address text,
    expires_at        timestamptz NOT NULL,
    UNIQUE (hotel_id, provider)
);

-- ---------- Tasks ----------
CREATE TABLE booking_tasks (
    id                               uuid PRIMARY KEY,
    request_id                       text        NOT NULL UNIQUE,
    municipality_id                  uuid        NOT NULL REFERENCES municipalities (id),
    check_in                         date        NOT NULL,
    check_out                        date        NOT NULL,
    rooms                            integer     NOT NULL,
    adults                           integer     NOT NULL,
    children                         integer     NOT NULL,
    destination_email                text        NOT NULL,
    authorization_mode               text        NOT NULL,
    maximum_total                    numeric(12, 2),
    required_amenities               text[]      NOT NULL DEFAULT '{}',
    allow_competing_quote_disclosure boolean     NOT NULL DEFAULT false,
    deadline                         timestamptz NOT NULL,
    status                           text        NOT NULL,
    outcome                          text,
    created_at                       timestamptz NOT NULL,
    updated_at                       timestamptz NOT NULL,
    version                          bigint      NOT NULL DEFAULT 0,
    CHECK (check_out > check_in)
);
CREATE INDEX idx_booking_tasks_status ON booking_tasks (status);

CREATE TABLE hotel_candidates (
    id                  uuid PRIMARY KEY,
    task_id             uuid NOT NULL REFERENCES booking_tasks (id) ON DELETE CASCADE,
    hotel_id            uuid NOT NULL REFERENCES hotels (id),
    price_total         numeric(12, 2),
    price_tax_inclusion text,
    price_source        text,
    price_observed_at   timestamptz,
    distance_km         double precision NOT NULL,
    call_priority       integer,
    exclusion_reason    text,
    UNIQUE (task_id, hotel_id)
);

-- ---------- Calls ----------
CREATE TABLE phone_calls (
    id               uuid PRIMARY KEY,
    task_id          uuid NOT NULL REFERENCES booking_tasks (id) ON DELETE CASCADE,
    hotel_id         uuid NOT NULL REFERENCES hotels (id),
    dialled_number   text NOT NULL,
    provider_call_id text UNIQUE,
    conversation_id  text UNIQUE,
    status           text NOT NULL,
    outcome          text,
    started_at       timestamptz,
    ended_at         timestamptz,
    failure_reason   text
);
CREATE INDEX idx_phone_calls_task ON phone_calls (task_id);

CREATE TABLE transcripts (
    id           uuid PRIMARY KEY,
    call_id      uuid        NOT NULL UNIQUE REFERENCES phone_calls (id) ON DELETE CASCADE,
    body         text        NOT NULL,
    retain_until timestamptz NOT NULL
);

-- ---------- Offers and reservations ----------
CREATE TABLE hotel_offers (
    id                     uuid PRIMARY KEY,
    task_id                uuid        NOT NULL REFERENCES booking_tasks (id) ON DELETE CASCADE,
    hotel_id               uuid        NOT NULL REFERENCES hotels (id),
    call_id                uuid        NOT NULL REFERENCES phone_calls (id),
    availability_confirmed boolean     NOT NULL,
    check_in               date,
    check_out              date,
    room_type              text,
    guest_count            integer     NOT NULL,
    initial_quoted_total   numeric(12, 2),
    room_subtotal          numeric(12, 2),
    hst                    numeric(12, 2),
    mat                    numeric(12, 2),
    mandatory_fees         numeric(12, 2),
    optional_fees          numeric(12, 2),
    tax_inclusion          text        NOT NULL,
    comparable_total       numeric(12, 2),       -- computed in Java, stored for history queries
    cancellation_terms     text,
    deposit_terms          text,
    enquiry_id             text,                 -- never a confirmation number
    quote_valid_until      timestamptz,
    holds_room             boolean     NOT NULL,
    evidence_status        text        NOT NULL,
    received_at            timestamptz NOT NULL
);
CREATE INDEX idx_hotel_offers_task ON hotel_offers (task_id);
CREATE INDEX idx_hotel_offers_hotel ON hotel_offers (hotel_id);

-- At most one reservation per task: the last line of defence against double booking.
CREATE TABLE reservations (
    id                  uuid PRIMARY KEY,
    task_id             uuid        NOT NULL UNIQUE REFERENCES booking_tasks (id),
    hotel_id            uuid        NOT NULL REFERENCES hotels (id),
    offer_id            uuid        NOT NULL REFERENCES hotel_offers (id),
    confirmation_number text,
    total               numeric(12, 2) NOT NULL,
    state               text        NOT NULL,
    evidence            text        NOT NULL,
    updated_at          timestamptz NOT NULL
);

-- ---------- Email and webhooks ----------
CREATE TABLE email_deliveries (
    id                  uuid PRIMARY KEY,
    task_id             uuid NOT NULL REFERENCES booking_tasks (id) ON DELETE CASCADE,
    recipient           text NOT NULL,
    provider_message_id text UNIQUE,
    status              text NOT NULL,
    sent_at             timestamptz,
    delivered_at        timestamptz,
    bounced_at          timestamptz
);

CREATE TABLE webhook_events (
    id                uuid PRIMARY KEY,
    provider          text        NOT NULL,
    external_event_id text        NOT NULL,
    event_type        text,
    payload           text        NOT NULL,
    received_at       timestamptz NOT NULL,
    processed_at      timestamptz,
    status            text        NOT NULL,
    UNIQUE (provider, external_event_id)
);

-- ---------- Quote history: our own price prior ----------
CREATE VIEW quote_history AS
SELECT o.hotel_id,
       o.check_in,
       o.check_out,
       o.guest_count,
       o.room_type,
       o.comparable_total,
       o.comparable_total / NULLIF(o.check_out - o.check_in, 0) AS nightly_total,
       o.received_at
FROM hotel_offers o
WHERE o.comparable_total IS NOT NULL
  AND o.availability_confirmed;
