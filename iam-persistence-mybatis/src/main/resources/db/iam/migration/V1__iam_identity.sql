CREATE TABLE iam_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    external_ref VARCHAR(128) NULL,
    username VARCHAR(191) NOT NULL,
    user_type VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    authorization_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_user_external_ref (external_ref),
    UNIQUE KEY uk_iam_user_username (username)
);

CREATE TABLE iam_identity (
    id VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    identity_key VARCHAR(191) NOT NULL,
    identity_domain VARCHAR(64) NOT NULL,
    credential_ref VARCHAR(255) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_identity_domain_key (identity_domain, identity_key),
    KEY idx_iam_identity_user_enabled (user_id, enabled),
    CONSTRAINT fk_iam_identity_user FOREIGN KEY (user_id) REFERENCES iam_user (id)
);
