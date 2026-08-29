CREATE TABLE iam_login_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(128) NOT NULL,
    user_id BIGINT NULL,
    identity_key VARCHAR(191) NULL,
    identity_domain VARCHAR(64) NULL,
    client_type VARCHAR(64) NULL,
    client_instance VARCHAR(191) NULL,
    session_id VARCHAR(128) NULL,
    result_code VARCHAR(64) NOT NULL,
    reason_code VARCHAR(128) NULL,
    request_id VARCHAR(128) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_login_event_event (event_id),
    KEY idx_iam_login_event_user_time (user_id, occurred_at, id),
    KEY idx_iam_login_event_identity_time (identity_domain, identity_key, occurred_at, id)
);

CREATE TABLE iam_session (
    session_id VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    client_type VARCHAR(64) NOT NULL,
    client_instance VARCHAR(191) NULL,
    login_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    revoke_reason VARCHAR(128) NULL,
    PRIMARY KEY (session_id),
    KEY idx_iam_session_user_active (user_id, revoked_at, expires_at, login_at),
    CONSTRAINT fk_iam_session_user FOREIGN KEY (user_id) REFERENCES iam_user (id)
);
