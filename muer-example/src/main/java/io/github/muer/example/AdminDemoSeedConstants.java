package io.github.iamstarter.example;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared identifiers and permission codes for the explicit opt-in
 * IAM Admin Console development demo. The demo account never exists in
 * production: both the seed and the authenticator entry are guarded by
 * {@code dev} profile + {@code iam.example.seed-admin=true}.
 */
final class AdminDemoSeedConstants {
    static final long USER_ID = 110L;
    static final String USERNAME = "admin-demo";
    static final String PASSWORD = "demo-pass";
    static final String DISPLAY_NAME = "IAM Admin Demo";
    static final String EXTERNAL_REF = "iam-admin-demo";
    static final String IDENTITY_ID = "identity-admin-demo";
    static final String IDENTITY_DOMAIN = "EXAMPLE";
    static final long PROFILE_ID = 405L;
    static final long TEMPLATE_ID = 250L;
    static final long TEMPLATE_VERSION_ID = 350L;

    static final long PERMISSION_ID_BASE = 900L;

    /** Canonical IAM Admin Console permission codes (must match the Java sources). */
    static final Map<String, String> ADMIN_PERMISSIONS = orderedPermissions();

    private AdminDemoSeedConstants() {
    }

    private static Map<String, String> orderedPermissions() {
        var codes = new LinkedHashMap<String, String>();
        codes.put("iam.admin.overview.read", "运营概览读取");
        codes.put("iam.admin.user.read", "用户读取");
        codes.put("iam.admin.user.write", "用户写入");
        codes.put("iam.admin.identity.read", "Identity 读取");
        codes.put("iam.admin.identity.write", "Identity 写入");
        codes.put("iam.admin.permission.read", "Permission 读取");
        codes.put("iam.admin.template.read", "Permission Template 读取");
        codes.put("iam.admin.template.write", "Permission Template 写入");
        codes.put("iam.admin.profile.read", "Profile 读取");
        codes.put("iam.admin.profile.write", "Profile 写入");
        codes.put("iam.admin.scope.read", "Scope 读取");
        codes.put("iam.admin.scope.write", "Scope 写入");
        codes.put("iam.admin.session.read", "Session 读取");
        codes.put("iam.admin.session.revoke", "Session 撤销");
        codes.put("iam.admin.session.revoke-user", "用户全部 Session 撤销");
        codes.put("iam.admin.audit.read", "Audit 读取");
        codes.put("iam.admin.diagnostics", "授权诊断页面");
        codes.put("iam.admin.authorization-version.increment", "授权版本递增");
        return Map.copyOf(codes);
    }

    static List<String> permissionCodes() {
        return List.copyOf(Set.copyOf(ADMIN_PERMISSIONS.keySet())).stream().sorted().toList();
    }
}
