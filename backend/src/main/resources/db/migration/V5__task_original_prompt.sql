-- The free-text request a task was interpreted from (POST /api/tasks/prompt); NULL for form submissions.
ALTER TABLE booking_tasks ADD COLUMN original_prompt text;
