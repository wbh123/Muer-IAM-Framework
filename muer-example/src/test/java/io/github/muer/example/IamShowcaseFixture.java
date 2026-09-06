package io.github.muer.example;

import org.springframework.jdbc.core.JdbcTemplate;

final class IamShowcaseFixture {
    static final long READER_501_PROFILE_ID = 401L;
    static final long APPROVER_501_PROFILE_ID = 402L;

    /**
     * Resets all IAM fixture data and seeds the operator A reader scenario.
     * This method is intentionally independent and is not composable with
     * {@link #seedOperatorB(JdbcTemplate)}.
     */
    void seedOperatorA(JdbcTemplate jdbc) {
        clearIamTables(jdbc);
        seedPermissionModel(jdbc);
        insertOperatorAIdentity(jdbc);
        insertReader501Profile(jdbc);
    }

    /**
     * Resets all IAM fixture data and seeds both of operator A's switchable
     * profiles. This is the only composable fixture because profile switching
     * requires the same principal to own both profiles.
     */
    void seedOperatorAProfiles(JdbcTemplate jdbc) {
        clearIamTables(jdbc);
        seedPermissionModel(jdbc);
        insertOperatorAIdentity(jdbc);
        insertReader501Profile(jdbc);
        jdbc.update("""
                INSERT INTO iam_authorization_profile
                    (id, user_id, template_version_id, profile_key, display_name, client_types, enabled, revoked_at)
                VALUES (?, 101, 302, 'approver-501', 'Approver 501', '[\"WEB\"]', TRUE, NULL)
                """, APPROVER_501_PROFILE_ID);
        insertScope(jdbc, APPROVER_501_PROFILE_ID, "501", "READ");
        insertScope(jdbc, APPROVER_501_PROFILE_ID, "501", "WRITE");
    }

    private static void insertOperatorAIdentity(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO iam_user (id, external_ref, username, user_type, authorization_version)
                VALUES (101, 'operator-a', 'operator-a', 'MEMBER', 1)
                """);
        jdbc.update("""
                INSERT INTO iam_identity (id, user_id, identity_key, identity_domain, credential_ref)
                VALUES ('identity-operator-a', 101, 'operator-a', 'EXAMPLE', 'demo-password')
                """);
    }

    private static void insertReader501Profile(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO iam_authorization_profile
                    (id, user_id, template_version_id, profile_key, display_name, client_types, enabled, revoked_at)
                VALUES (?, 101, 301, 'reader-501', 'Reader 501', '[\"WEB\"]', TRUE, NULL)
                """, READER_501_PROFILE_ID);
        insertScope(jdbc, READER_501_PROFILE_ID, "501", "READ");
    }

    /**
     * Resets all IAM fixture data and seeds the operator B reader scenario.
     * This method is intentionally independent and is not composable with
     * {@link #seedOperatorA(JdbcTemplate)}.
     */
    void seedOperatorB(JdbcTemplate jdbc) {
        clearIamTables(jdbc);
        seedPermissionModel(jdbc);
        jdbc.update("""
                INSERT INTO iam_user (id, external_ref, username, user_type, authorization_version)
                VALUES (102, 'operator-b', 'operator-b', 'MEMBER', 1)
                """);
        jdbc.update("""
                INSERT INTO iam_identity (id, user_id, identity_key, identity_domain, credential_ref)
                VALUES ('identity-operator-b', 102, 'operator-b', 'EXAMPLE', 'demo-password')
                """);
        jdbc.update("""
                INSERT INTO iam_authorization_profile
                    (id, user_id, template_version_id, profile_key, display_name, client_types, enabled, revoked_at)
                VALUES (403, 102, 301, 'reader-502', 'Reader 502', '[\"WEB\"]', TRUE, NULL)
                """);
        insertScope(jdbc, 403, "502", "READ");
    }

    void seedIndependentConsumer(JdbcTemplate jdbc) {
        clearIamTables(jdbc);
        jdbc.update("INSERT INTO iam_user (id, external_ref, username, user_type, authorization_version) VALUES (101, 'author-a', 'author-a', 'MEMBER', 1)");
        jdbc.update("INSERT INTO iam_user (id, external_ref, username, user_type, authorization_version) VALUES (102, 'reader-b', 'reader-b', 'MEMBER', 1)");
        jdbc.update("INSERT INTO iam_identity (id, user_id, identity_key, identity_domain, credential_ref) VALUES ('identity-author-a', 101, 'author-a', 'EXAMPLE', 'demo-pass')");
        jdbc.update("INSERT INTO iam_identity (id, user_id, identity_key, identity_domain, credential_ref) VALUES ('identity-reader-b', 102, 'reader-b', 'EXAMPLE', 'demo-pass')");
        jdbc.update("INSERT INTO iam_permission (id, permission_code, display_name) VALUES (701, 'document:read', 'Read document')");
        jdbc.update("INSERT INTO iam_permission (id, permission_code, display_name) VALUES (702, 'document:create', 'Create document')");
        jdbc.update("INSERT INTO iam_permission (id, permission_code, display_name) VALUES (703, 'document:update', 'Update document')");
        jdbc.update("INSERT INTO iam_permission (id, permission_code, display_name) VALUES (704, 'document:delete', 'Delete document')");
        jdbc.update("INSERT INTO iam_permission_template (id, template_key, display_name) VALUES (201, 'document-reader', 'Document Reader')");
        jdbc.update("INSERT INTO iam_permission_template (id, template_key, display_name) VALUES (202, 'document-admin', 'Document Administrator')");
        jdbc.update("INSERT INTO iam_permission_template_version (id, template_id, version_number, status, published_at) VALUES (301, 201, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6))");
        jdbc.update("INSERT INTO iam_permission_template_version (id, template_id, version_number, status, published_at) VALUES (302, 202, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6))");
        jdbc.update("INSERT INTO iam_template_permission (template_version_id, permission_id) VALUES (301, 701)");
        jdbc.update("INSERT INTO iam_template_permission (template_version_id, permission_id) VALUES (302, 701)");
        jdbc.update("INSERT INTO iam_template_permission (template_version_id, permission_id) VALUES (302, 702)");
        jdbc.update("INSERT INTO iam_template_permission (template_version_id, permission_id) VALUES (302, 703)");
        jdbc.update("INSERT INTO iam_template_permission (template_version_id, permission_id) VALUES (302, 704)");
        jdbc.update("INSERT INTO iam_authorization_profile (id, user_id, template_version_id, profile_key, display_name, client_types, enabled, revoked_at) VALUES (401, 101, 302, 'author-admin', 'Author Administrator', '[\"WEB\"]', TRUE, NULL)");
        jdbc.update("INSERT INTO iam_authorization_profile (id, user_id, template_version_id, profile_key, display_name, client_types, enabled, revoked_at) VALUES (402, 101, 301, 'author-reader', 'Author Reader', '[\"WEB\"]', TRUE, NULL)");
        jdbc.update("INSERT INTO iam_authorization_profile (id, user_id, template_version_id, profile_key, display_name, client_types, enabled, revoked_at) VALUES (403, 102, 301, 'reader-project-101', 'Reader Project 101', '[\"WEB\"]', TRUE, NULL)");
        jdbc.update("INSERT INTO iam_authorization_scope (profile_id, resource_type, resource_id, scope_access) VALUES (401, 'PROJECT', '101', 'WRITE')");
        jdbc.update("INSERT INTO iam_authorization_scope (profile_id, resource_type, resource_id, scope_access) VALUES (401, 'PROJECT', '101', 'READ')");
        jdbc.update("INSERT INTO iam_authorization_scope (profile_id, resource_type, resource_id, scope_access) VALUES (402, 'PROJECT', '101', 'READ')");
        jdbc.update("INSERT INTO iam_authorization_scope (profile_id, resource_type, resource_id, scope_access) VALUES (403, 'PROJECT', '101', 'READ')");
    }

    private static void clearIamTables(JdbcTemplate jdbc) {
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

    private static void seedPermissionModel(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO iam_permission_template (id, template_key, display_name)
                VALUES (201, 'showcase-reader', 'Showcase Reader')
                """);
        jdbc.update("""
                INSERT INTO iam_permission_template_version (id, template_id, version_number, status, published_at)
                VALUES (301, 201, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6))
                """);
        jdbc.update("""
                INSERT INTO iam_permission_template (id, template_key, display_name)
                VALUES (202, 'showcase-approver', 'Showcase Approver')
                """);
        jdbc.update("""
                INSERT INTO iam_permission_template_version (id, template_id, version_number, status, published_at)
                VALUES (302, 202, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6))
                """);
        jdbc.update("""
                INSERT INTO iam_permission (id, permission_code, display_name)
                VALUES (601, 'order.read', 'Read order')
                """);
        jdbc.update("""
                INSERT INTO iam_permission (id, permission_code, display_name)
                VALUES (602, 'order.approve', 'Approve order')
                """);
        jdbc.update("INSERT INTO iam_template_permission (template_version_id, permission_id) VALUES (301, 601)");
        jdbc.update("INSERT INTO iam_template_permission (template_version_id, permission_id) VALUES (302, 601)");
        jdbc.update("INSERT INTO iam_template_permission (template_version_id, permission_id) VALUES (302, 602)");
    }

    private static void insertScope(JdbcTemplate jdbc, long profileId, String departmentId, String access) {
        jdbc.update("""
                INSERT INTO iam_authorization_scope (profile_id, resource_type, resource_id, scope_access)
                VALUES (?, 'DEPARTMENT', ?, ?)
                """, profileId, departmentId, access);
    }
}
