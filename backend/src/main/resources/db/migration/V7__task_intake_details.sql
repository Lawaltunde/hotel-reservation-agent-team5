-- What the user actually stated in a prompt (POST /api/tasks/prompt). NULL stated_fields = form submission.
ALTER TABLE booking_tasks ADD COLUMN stated_fields     text[];
ALTER TABLE booking_tasks ADD COLUMN email_source      text;
ALTER TABLE booking_tasks ADD COLUMN notes             text;
ALTER TABLE booking_tasks ADD COLUMN maximum_per_night numeric(12, 2);
ALTER TABLE booking_tasks ADD COLUMN assumptions       text[];
