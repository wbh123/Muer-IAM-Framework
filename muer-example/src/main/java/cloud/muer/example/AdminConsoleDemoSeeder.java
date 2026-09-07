package cloud.muer.example;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import static cloud.muer.example.AdminDemoSeedConstants.ADMIN_PERMISSIONS;
import static cloud.muer.example.AdminDemoSeedConstants.PERMISSION_ID_BASE;

/**
 * Creates the explicit opt-in Muer Admin Console demo administrator for the
 * example application (admin-demo / demo-pass). It runs only when the dev
 * profile AND {@code muer.example.seed-admin=true} are active; production and
 * ordinary dev never create the account. No default admin/password is ever
 * auto-provisioned.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "muer.example", name = "seed-admin", havingValue = "true")
final class AdminConsoleDemoSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    AdminConsoleDemoSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        clearDemoRows();
        seedUserAndIdentity();
        seedAdminPermissions();
        seedTemplateAndVersion();
        seedProfileAndScopes();
    }

    private void clearDemoRows() {
        jdbc.update("DELETE FROM iam_authorization_scope WHERE profile_id = ?",
                AdminDemoSeedConstants.PROFILE_ID);
        jdbc.update("DELETE FROM iam_authorization_profile WHERE id = ?",
                AdminDemoSeedConstants.PROFILE_ID);
        jdbc.update("DELETE FROM iam_template_permission WHERE template_version_id = ?",
                AdminDemoSeedConstants.TEMPLATE_VERSION_ID);
        jdbc.update("DELETE FROM iam_permission_template_version WHERE id = ?",
                AdminDemoSeedConstants.TEMPLATE_VERSION_ID);
        jdbc.update("DELETE FROM iam_permission_template WHERE id = ?",
                AdminDemoSeedConstants.TEMPLATE_ID);
        deleteCanonicalDemoPermissions();
        jdbc.update("DELETE FROM iam_identity WHERE id = ?", AdminDemoSeedConstants.IDENTITY_ID);
        jdbc.update("DELETE FROM iam_user WHERE id = ?", AdminDemoSeedConstants.USER_ID);
    }

    private void deleteCanonicalDemoPermissions() {
        var codes = canonicalPermissionCodes();
        jdbc.update("DELETE FROM iam_permission WHERE permission_code IN (" + placeholders(codes.size()) + ")",
                codes.toArray());
    }

    private void seedUserAndIdentity() {
        jdbc.update("""
                INSERT INTO iam_user (id, external_ref, username, user_type, authorization_version)
                VALUES (?, ?, ?, 'IAM_ADMIN', 1)
                """, AdminDemoSeedConstants.USER_ID, AdminDemoSeedConstants.EXTERNAL_REF,
                AdminDemoSeedConstants.USERNAME);
        jdbc.update("""
                INSERT INTO iam_identity (id, user_id, identity_key, identity_domain, credential_ref)
                VALUES (?, ?, ?, ?, NULL)
                """, AdminDemoSeedConstants.IDENTITY_ID, AdminDemoSeedConstants.USER_ID,
                AdminDemoSeedConstants.USERNAME, AdminDemoSeedConstants.IDENTITY_DOMAIN);
    }

    private void seedAdminPermissions() {
        var statement = new StringJoiner(", ", "INSERT INTO iam_permission (id, permission_code, display_name) VALUES ", "");
        var params = new ArrayList<Object>();
        long id = PERMISSION_ID_BASE;
        for (var entry : ADMIN_PERMISSIONS.entrySet()) {
            statement.add("(?, ?, ?)");
            params.add(id++);
            params.add(entry.getKey());
            params.add(entry.getValue());
        }
        jdbc.update(statement.toString(), params.toArray());
    }

    private void seedTemplateAndVersion() {
        jdbc.update("""
                INSERT INTO iam_permission_template (id, template_key, display_name, description)
                VALUES (?, 'iam-admin', 'Muer Admin Console', 'Muer Admin Console template (dev demo)')
                """, AdminDemoSeedConstants.TEMPLATE_ID);
        jdbc.update("""
                INSERT INTO iam_permission_template_version
                    (id, template_id, version_number, status, published_at)
                VALUES (?, ?, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6))
                """, AdminDemoSeedConstants.TEMPLATE_VERSION_ID, AdminDemoSeedConstants.TEMPLATE_ID);
        linkCanonicalPermissionsToTemplate();
    }

    private void linkCanonicalPermissionsToTemplate() {
        var codes = canonicalPermissionCodes();
        var params = new ArrayList<Object>();
        params.add(AdminDemoSeedConstants.TEMPLATE_VERSION_ID);
        params.addAll(codes);
        jdbc.update("""
                INSERT INTO iam_template_permission (template_version_id, permission_id)
                SELECT ?, id FROM iam_permission WHERE permission_code IN (%s)
                """.formatted(placeholders(codes.size())), params.toArray());
    }

    private static List<String> canonicalPermissionCodes() {
        return List.copyOf(ADMIN_PERMISSIONS.keySet());
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private void seedProfileAndScopes() {
        jdbc.update("""
                INSERT INTO iam_authorization_profile
                    (id, user_id, template_version_id, profile_key, display_name,
                     client_types, enabled, default_profile, revoked_at)
                VALUES (?, ?, ?, 'admin-console', 'Muer Admin Console (dev demo)',
                        '["WEB"]', TRUE, TRUE, NULL)
                """, AdminDemoSeedConstants.PROFILE_ID, AdminDemoSeedConstants.USER_ID,
                AdminDemoSeedConstants.TEMPLATE_VERSION_ID);
        jdbc.update("""
                INSERT INTO iam_authorization_scope (profile_id, resource_type, resource_id, scope_access)
                VALUES (?, 'IAM_ADMIN', '*', 'READ'),
                       (?, 'IAM_ADMIN', '*', 'WRITE')
                """, AdminDemoSeedConstants.PROFILE_ID, AdminDemoSeedConstants.PROFILE_ID);
    }
}
