-- Emailed links to call audio. Only the SHA-256 of the token is stored; the token itself is in the email.
CREATE TABLE call_recording_links (
    token_hash      text PRIMARY KEY,
    call_id         uuid        NOT NULL REFERENCES phone_calls (id) ON DELETE CASCADE,
    conversation_id text        NOT NULL,
    expires_at      timestamptz NOT NULL,
    created_at      timestamptz NOT NULL
);
CREATE INDEX idx_call_recording_links_call ON call_recording_links (call_id);
