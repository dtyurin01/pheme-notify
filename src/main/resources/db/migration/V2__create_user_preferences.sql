CREATE TABLE user_preferences (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    VARCHAR(64) NOT NULL UNIQUE,
    enabled_channels notification_channel[] NOT NULL DEFAULT '{EMAIL}',

    locale         VARCHAR(10)  NOT NULL DEFAULT 'en',
    timezone       VARCHAR(32)  NOT NULL DEFAULT 'UTC',

    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_preferences_locale ON user_preferences(locale);


