CREATE TABLE user_preference_channels (
    preference_id UUID NOT NULL,
    channel notification_channel NOT NULL,

    CONSTRAINT fk_user_preferences 
        FOREIGN KEY (preference_id) 
        REFERENCES user_preferences (id) 
        ON DELETE CASCADE,
        
    PRIMARY KEY (preference_id, channel)
);

ALTER TABLE user_preferences DROP COLUMN enabled_channels;

CREATE INDEX idx_user_preference_channels_channel ON user_preference_channels(channel);