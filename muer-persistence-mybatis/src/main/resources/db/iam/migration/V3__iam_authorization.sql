CREATE TABLE iam_authorization_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    template_version_id BIGINT NOT NULL,
    profile_key VARCHAR(191) NOT NULL,
    display_name VARCHAR(191) NOT NULL,
    client_types JSON NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    default_profile BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from TIMESTAMP(6) NULL,
    valid_until TIMESTAMP(6) NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_profile_user_key (user_id, profile_key),
    KEY idx_iam_profile_lookup (user_id, enabled, revoked_at),
    KEY idx_iam_profile_template (template_version_id),
    CONSTRAINT fk_iam_profile_user FOREIGN KEY (user_id) REFERENCES iam_user (id),
    CONSTRAINT fk_iam_profile_template_version FOREIGN KEY (template_version_id)
        REFERENCES iam_permission_template_version (id)
);

CREATE TABLE iam_authorization_scope (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    resource_type VARCHAR(128) NOT NULL,
    resource_id VARCHAR(191) NOT NULL,
    scope_access VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_scope_profile_resource (profile_id, resource_type, resource_id, scope_access),
    KEY idx_iam_scope_lookup (resource_type, resource_id, scope_access, profile_id),
    CONSTRAINT fk_iam_scope_profile FOREIGN KEY (profile_id)
        REFERENCES iam_authorization_profile (id) ON DELETE CASCADE
);
