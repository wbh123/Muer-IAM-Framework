package com.example.muerquickstart.security;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DEV-ONLY, OPT-IN authorization seeding so the tutorial "just works".
 *
 * <p>{@code PermissionDefinitionProvider} only <em>registers</em> permissions;
 * it never creates Templates, Template Versions, Profiles or Scopes. Those are
 * administrative rows. For the tutorial this seeder creates a fixed demo
 * projection (Alice Reader / Alice Editor) straight into the IAM projection
 * tables of the local database.</p>
 *
 * <p><strong>Never use this in production.</strong> It is active only when BOTH
 * the {@code dev} profile AND {@code muer.quickstart.seed-demo=true} are set.
 * In a managed environment the same rows are created through the Admin Console
 * or Management API instead.</p>
 *
 * <p>Seed (matches {@code DemoAccountService} and the tutorial):</p>
 * <ul>
 *   <li>Template 201 "Document Reader", Version 301 = [document:read]</li>
 *   <li>Template 202 "Document Editor", Version 302 = [document:read, document:update]</li>
 *   <li>Profile 401 (Alice Reader)  -> Version 301, scope PROJECT/101/READ</li>
 *   <li>Profile 402 (Alice Editor)  -> Version 302, scope PROJECT/101/READ + WRITE</li>
 * </ul>
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "muer.quickstart", name = "seed-demo", havingValue = "true")
final class QuickStartAuthorizationSeeder {

    private final JdbcTemplate jdbc;

    QuickStartAuthorizationSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void seedAfterPermissionRegistration() {
        clearProjection();
        seedTemplatesAndVersions();
        seedProfilesAndScopes();
    }

    private void clearProjection() {
        // Re-running the app resets the demo projection to a known state. Only
        // these local demo tables are touched; other IAM rows are left alone.
        jdbc.update("DELETE FROM iam_authorization_scope");
        jdbc.update("DELETE FROM iam_authorization_profile");
        jdbc.update("DELETE FROM iam_template_permission");
        jdbc.update("DELETE FROM iam_permission_template_version");
        jdbc.update("DELETE FROM iam_permission_template");
        jdbc.update("DELETE FROM iam_session");
        jdbc.update("DELETE FROM iam_login_event");
        jdbc.update("DELETE FROM iam_audit_log");
        jdbc.update("DELETE FROM iam_audit_subject_link");
    }

    private void seedTemplatesAndVersions() {
        jdbc.update("""
                INSERT INTO iam_permission_template (id, template_key, display_name)
                VALUES (201, 'quickstart-document-reader', 'Document Reader'),
                       (202, 'quickstart-document-editor', 'Document Editor')
                """);
        jdbc.update("""
                INSERT INTO iam_permission_template_version
                    (id, template_id, version_number, status, published_at)
                VALUES (301, 201, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6)),
                       (302, 202, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6))
                """);
        jdbc.update("""
                INSERT INTO iam_template_permission (template_version_id, permission_id)
                SELECT 301, id FROM iam_permission WHERE permission_code = 'document:read'
                UNION ALL SELECT 302, id FROM iam_permission WHERE permission_code = 'document:read'
                UNION ALL SELECT 302, id FROM iam_permission WHERE permission_code = 'document:update'
                """);
    }

    private void seedProfilesAndScopes() {
        jdbc.update("""
                INSERT INTO iam_authorization_profile
                    (id, user_id, template_version_id, profile_key, display_name,
                     client_types, enabled, default_profile, revoked_at)
                VALUES (401, 101, 301, 'alice-reader-project-101', 'Alice Reader - Project 101',
                        '["WEB"]', TRUE, TRUE, NULL),
                       (402, 101, 302, 'alice-editor-project-101', 'Alice Editor - Project 101',
                        '["WEB"]', TRUE, FALSE, NULL)
                """);
        jdbc.update("""
                INSERT INTO iam_authorization_scope
                    (profile_id, resource_type, resource_id, scope_access)
                VALUES (401, 'PROJECT', '101', 'READ'),
                       (402, 'PROJECT', '101', 'READ'),
                       (402, 'PROJECT', '101', 'WRITE')
                """);
    }
}
