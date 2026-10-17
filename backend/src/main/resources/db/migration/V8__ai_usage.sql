-- Model tokens and voice-call seconds spent per task, reported as evidence in the report email.
CREATE TABLE ai_usage (
    id                uuid PRIMARY KEY,
    task_id           uuid        NOT NULL REFERENCES booking_tasks (id) ON DELETE CASCADE,
    purpose           text        NOT NULL,
    prompt_tokens     bigint      NOT NULL DEFAULT 0,
    completion_tokens bigint      NOT NULL DEFAULT 0,
    call_seconds      bigint      NOT NULL DEFAULT 0,
    created_at        timestamptz NOT NULL
);
CREATE INDEX idx_ai_usage_task ON ai_usage (task_id);
