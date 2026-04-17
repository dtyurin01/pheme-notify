
CREATE TABLE failed_notifications (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id        VARCHAR(64) NOT NULL,
    channel        notification_channel NOT NULL,
    event_type     VARCHAR(64) NOT NULL,

    event_payload  JSONB       NOT NULL,
    error_message  TEXT,
    retry_count    INT         NOT NULL DEFAULT 0,
    status         notification_status NOT NULL DEFAULT 'PENDING',

    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_retry_at  TIMESTAMPTZ,
    next_retry_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_failed_notifications_scheduler
    ON failed_notifications(next_retry_at)
    WHERE retry_count < 3 AND status = 'PENDING';

CREATE INDEX idx_failed_notifications_user_id ON failed_notifications(user_id);

CREATE INDEX idx_failed_notifications_created_at  ON failed_notifications(created_at);
