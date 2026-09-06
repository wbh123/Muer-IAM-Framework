package io.github.muer.example;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resets the dedicated QuickStart database to anonymous demonstration data.
 * The component is unavailable unless both the dev profile and explicit opt-in are active.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "muer.example", name = "seed-demo", havingValue = "true")
final class QuickStartDemoSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final DocumentCatalog documents;

    QuickStartDemoSeeder(JdbcTemplate jdbc, DocumentCatalog documents) {
        this.jdbc = jdbc;
        this.documents = documents;
    }

    @Override
    public void run(ApplicationArguments args) {
        clearIamProjection();
        seedAlice();
        seedDocumentPermissions();
        seedProfilesAndScopes();
        documents.seedQuickStartDocuments();
    }

    private void clearIamProjection() {
        jdbc.update("DELETE FROM iam_audit_subject_link");
        jdbc.update("DELETE FROM iam_audit_log");
        jdbc.update("DELETE FROM iam_login_event");
        jdbc.update("DELETE FROM iam_session");
        jdbc.update("DELETE FROM iam_authorization_scope");
        jdbc.update("DELETE FROM iam_authorization_profile");
        jdbc.update("DELETE FROM iam_template_permission");
        jdbc.update("DELETE FROM iam_permission_template_version");
        jdbc.update("DELETE FROM iam_permission_template");
        jdbc.update("DELETE FROM iam_permission");
        jdbc.update("DELETE FROM iam_identity");
        jdbc.update("DELETE FROM iam_user");
    }

    private void seedAlice() {
        jdbc.update("""
                INSERT INTO iam_user (id, external_ref, username, user_type, authorization_version)
                VALUES (101, 'quickstart-alice', 'alice', 'MEMBER', 1)
                """);
        jdbc.update("""
                INSERT INTO iam_identity (id, user_id, identity_key, identity_domain, credential_ref)
                VALUES ('identity-alice', 101, 'alice', 'EXAMPLE', NULL)
                """);
    }

    private void seedDocumentPermissions() {
        jdbc.update("""
                INSERT INTO iam_permission (id, permission_code, display_name)
                VALUES (701, 'document:read', 'Read document'),
                       (702, 'document:update', 'Update document')
                """);
        jdbc.update("""
                INSERT INTO iam_permission_template (id, template_key, display_name)
                VALUES (201, 'quickstart-document-reader', 'QuickStart Document Reader'),
                       (202, 'quickstart-document-editor', 'QuickStart Document Editor')
                """);
        jdbc.update("""
                INSERT INTO iam_permission_template_version
                    (id, template_id, version_number, status, published_at)
                VALUES (301, 201, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6)),
                       (302, 202, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6))
                """);
        jdbc.update("""
                INSERT INTO iam_template_permission (template_version_id, permission_id)
                VALUES (301, 701), (302, 701), (302, 702)
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
