-- Exactly what the report email contained (subject, recipients, body, key fields, evidence), as JSON.
CREATE TABLE task_reports (
    task_id     uuid PRIMARY KEY REFERENCES booking_tasks (id) ON DELETE CASCADE,
    report      jsonb       NOT NULL,
    composed_at timestamptz NOT NULL
);
