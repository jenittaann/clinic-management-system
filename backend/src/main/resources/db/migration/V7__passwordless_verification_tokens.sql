CREATE TABLE passwordless_verification_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(32) NOT NULL,
    selected_role VARCHAR(32) NOT NULL,
    hospital_public_id VARCHAR(36) NOT NULL,
    remember_me BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_passwordless_token_email_created
    ON passwordless_verification_tokens(email, created_at);
CREATE INDEX idx_passwordless_token_expiry
    ON passwordless_verification_tokens(expires_at);
