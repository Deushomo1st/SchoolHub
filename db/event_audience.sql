-- Multi-audience events: widen and un-CHECK calendar_event.audience (comma lists,
-- validated by EventService). Idempotent; run per tenant schema.
ALTER TABLE calendar_event ALTER COLUMN audience TYPE VARCHAR(64);
ALTER TABLE calendar_event DROP CONSTRAINT IF EXISTS calendar_event_audience_check;
