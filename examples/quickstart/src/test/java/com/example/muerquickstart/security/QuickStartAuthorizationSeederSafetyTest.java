package com.example.muerquickstart.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuickStartAuthorizationSeederSafetyTest {
    private JdbcTemplate jdbc;

    @BeforeEach
    void createSchema() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:seeder;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        for (String table : new String[]{"iam_audit_subject_link", "iam_audit_log", "iam_login_event", "iam_session",
                "iam_authorization_scope", "iam_authorization_profile", "iam_template_permission",
                "iam_permission_template_version", "iam_permission_template", "iam_permission"}) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
        jdbc.execute("CREATE TABLE iam_permission(id BIGINT AUTO_INCREMENT PRIMARY KEY, permission_code VARCHAR(191) UNIQUE)");
        jdbc.execute("CREATE TABLE iam_permission_template(id BIGINT PRIMARY KEY, template_key VARCHAR(191) UNIQUE, display_name VARCHAR(255))");
        jdbc.execute("CREATE TABLE iam_permission_template_version(id BIGINT PRIMARY KEY, template_id BIGINT, version_number INT, status VARCHAR(32), published_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE iam_template_permission(template_version_id BIGINT, permission_id BIGINT)");
        jdbc.execute("CREATE TABLE iam_authorization_profile(id BIGINT PRIMARY KEY, user_id BIGINT, template_version_id BIGINT, profile_key VARCHAR(191) UNIQUE, display_name VARCHAR(255), client_types VARCHAR(255), enabled BOOLEAN, default_profile BOOLEAN, revoked_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE iam_authorization_scope(profile_id BIGINT, resource_type VARCHAR(64), resource_id VARCHAR(191), scope_access VARCHAR(16))");
        jdbc.execute("CREATE TABLE iam_session(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE iam_login_event(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE iam_audit_log(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE iam_audit_subject_link(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO iam_permission(permission_code) VALUES ('document:read'), ('document:update')");
    }

    @Test
    void seeding_preserves_unrelated_authorization_and_operational_rows() {
        insertUnrelatedRows();

        new QuickStartAuthorizationSeeder(jdbc).seedAfterPermissionRegistration();

        assertEquals(1, count("iam_permission_template", "id=900"));
        assertEquals(1, count("iam_authorization_profile", "id=901"));
        assertEquals(1, count("iam_authorization_scope", "profile_id=901"));
        assertEquals(1, count("iam_session", "id=1"));
        assertEquals(1, count("iam_login_event", "id=1"));
        assertEquals(1, count("iam_audit_log", "id=1"));
        assertEquals(1, count("iam_audit_subject_link", "id=1"));
        assertEquals(2, count("iam_permission_template", "id IN (201,202)"));
        assertEquals(2, count("iam_authorization_profile", "id IN (401,402)"));
    }

    @Test
    void conflicting_demo_business_key_fails_before_existing_demo_rows_are_deleted() {
        new QuickStartAuthorizationSeeder(jdbc).seedAfterPermissionRegistration();
        jdbc.update("DELETE FROM iam_permission_template WHERE id=201");
        jdbc.update("INSERT INTO iam_permission_template(id, template_key, display_name) VALUES (900, 'quickstart-document-reader', 'Unrelated')");

        assertThrows(IllegalStateException.class,
                () -> new QuickStartAuthorizationSeeder(jdbc).seedAfterPermissionRegistration());

        assertEquals(1, count("iam_authorization_profile", "id=401"));
        assertEquals(1, count("iam_permission_template", "id=900"));
    }

    private void insertUnrelatedRows() {
        jdbc.update("INSERT INTO iam_permission_template VALUES (900, 'unrelated', 'Unrelated')");
        jdbc.update("INSERT INTO iam_permission_template_version VALUES (901, 900, 1, 'PUBLISHED', CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO iam_authorization_profile VALUES (901, 700, 901, 'unrelated-profile', 'Unrelated', '[\"WEB\"]', TRUE, FALSE, NULL)");
        jdbc.update("INSERT INTO iam_authorization_scope VALUES (901, 'PROJECT', 'other', 'READ')");
        jdbc.update("INSERT INTO iam_session VALUES (1)"); jdbc.update("INSERT INTO iam_login_event VALUES (1)");
        jdbc.update("INSERT INTO iam_audit_log VALUES (1)"); jdbc.update("INSERT INTO iam_audit_subject_link VALUES (1)");
    }

    private int count(String table, String where) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Integer.class);
    }
}
