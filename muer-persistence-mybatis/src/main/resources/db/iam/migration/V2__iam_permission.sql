CREATE TABLE iam_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    permission_code VARCHAR(191) NOT NULL,
    display_name VARCHAR(191) NOT NULL,
    description VARCHAR(500) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_permission_code (permission_code)
);

CREATE TABLE iam_permission_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_key VARCHAR(191) NOT NULL,
    display_name VARCHAR(191) NOT NULL,
    description VARCHAR(500) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_permission_template_key (template_key)
);

CREATE TABLE iam_permission_template_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_template_version (template_id, version_number),
    KEY idx_iam_template_version_status (template_id, status),
    CONSTRAINT fk_iam_template_version_template FOREIGN KEY (template_id)
        REFERENCES iam_permission_template (id)
);

CREATE TABLE iam_template_permission (
    template_version_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (template_version_id, permission_id),
    KEY idx_iam_template_permission_permission (permission_id),
    CONSTRAINT fk_iam_template_permission_version FOREIGN KEY (template_version_id)
        REFERENCES iam_permission_template_version (id),
    CONSTRAINT fk_iam_template_permission_permission FOREIGN KEY (permission_id)
        REFERENCES iam_permission (id)
);
