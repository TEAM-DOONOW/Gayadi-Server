CREATE TABLE fcm_device_tokens (
    token VARCHAR(4096) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fcm_device_tokens_user ON fcm_device_tokens (user_id);
