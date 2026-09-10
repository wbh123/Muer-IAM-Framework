CREATE TABLE iam_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(128) NOT NULL,
    operator_ref VARCHAR(191) NOT NULL,
    action_code VARCHAR(191) NOT NULL,
    resource_type VARCHAR(128) NOT NULL,
    resource_id VARCHAR(191) NOT NULL,
    result_code VARCHAR(64) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    before_state JSON NULL,
    after_state JSON NULL,
    metadata JSON NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_audit_event_id (event_id),
    KEY idx_iam_audit_operator_time (operator_ref, occurred_at, id),
    KEY idx_iam_audit_resource_time (resource_type, resource_id, occurred_at, id),
    KEY idx_iam_audit_action_time (action_code, occurred_at, id)
);

CREATE TABLE iam_audit_subject_link (
    id BIGINT NOT NULL AUTO_INCREMENT,
    audit_log_id BIGINT NOT NULL,
    subject_type VARCHAR(128) NOT NULL,
    subject_id VARCHAR(191) NOT NULL,
    relation_code VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_audit_subject_relation (audit_log_id, subject_type, subject_id, relation_code),
    KEY idx_iam_audit_subject_lookup (subject_type, subject_id, audit_log_id),
    CONSTRAINT fk_iam_audit_subject_log FOREIGN KEY (audit_log_id)
        REFERENCES iam_audit_log (id) ON DELETE CASCADE
);
