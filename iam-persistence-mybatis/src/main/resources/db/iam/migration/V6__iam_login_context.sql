ALTER TABLE iam_login_event
    ADD COLUMN ip_address VARCHAR(64) NULL AFTER client_instance,
    ADD COLUMN user_agent VARCHAR(1024) NULL AFTER ip_address,
    ADD COLUMN device_type VARCHAR(64) NULL AFTER user_agent,
    ADD COLUMN os_name VARCHAR(128) NULL AFTER device_type,
    ADD COLUMN browser_name VARCHAR(128) NULL AFTER os_name,
    ADD COLUMN app_version VARCHAR(64) NULL AFTER browser_name;

ALTER TABLE iam_session
    ADD COLUMN ip_address VARCHAR(64) NULL AFTER client_instance,
    ADD COLUMN user_agent VARCHAR(1024) NULL AFTER ip_address,
    ADD COLUMN logout_at TIMESTAMP(6) NULL AFTER revoked_at;
