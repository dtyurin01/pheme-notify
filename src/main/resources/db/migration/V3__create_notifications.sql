
CREATE TABLE notifications (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           VARCHAR(64)  NOT NULL,
    channel           notification_channel  NOT NULL,
    event_type        VARCHAR(64)           NOT NULL,
    idempotency_key   VARCHAR(128)          NOT NULL UNIQUE,
    status            notification_status   NOT NULL DEFAULT 'PENDING',
    provider_response TEXT,
    error_message     TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at           TIMESTAMPTZ
);

CREATE INDEX idx_notifications_user_id    ON notifications(user_id);
CREATE INDEX idx_notifications_status     ON notifications(status);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);

CREATE INDEX idx_notifications_created_at_desc ON notifications(created_at DESC);

CREATE INDEX idx_notifications_status_partial
    ON notifications(status)
    WHERE status IN ('PENDING', 'FAILED');