ALTER TABLE outbox_events
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN locked_by VARCHAR(255) NULL,
    ADD COLUMN locked_until TIMESTAMPTZ NULL,
    ADD COLUMN last_error TEXT NULL;

DROP INDEX idx_outbox_events_pending;

CREATE INDEX idx_outbox_events_publishable
    ON outbox_events (next_attempt_at, created_at, id)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_events_expired_lease
    ON outbox_events (locked_until)
    WHERE status = 'PROCESSING';
