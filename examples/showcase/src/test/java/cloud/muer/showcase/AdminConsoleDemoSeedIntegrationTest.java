package cloud.muer.showcase;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminConsoleDemoSeedIntegrationTest {
    private static final String FOREIGN_ADMIN_PERMISSION = "iam.admin.extension.read";
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("iam_admin_seed")
            .withUsername("iam")
            .withPassword("iam-secret");
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @AfterAll
    static void stopInfrastructure() {
        REDIS.stop();
        MYSQL.stop();
    }

    @Test
    void admin_seed_requires_dev_profile_and_explicit_opt_in_then_provisions_the_demo_administrator() {
        try (var defaults = start(false, false)) {
            assertAdminSeedAbsent(defaults);
        }
        try (var devOnly = start(true, false)) {
            assertAdminSeedAbsent(devOnly);
        }
        try (var propertyOnly = start(false, true)) {
            assertAdminSeedAbsent(propertyOnly);
            propertyOnly.getBean(JdbcTemplate.class).update(
                    "INSERT INTO iam_permission (permission_code, display_name) VALUES (?, ?)",
                    FOREIGN_ADMIN_PERMISSION, "Host extension admin permission");
        }

        try (var enabled = start(true, true)) {
            var jdbc = enabled.getBean(JdbcTemplate.class);

            assertEquals(List.of(AdminDemoSeedConstants.USERNAME), jdbc.queryForList(
                    "SELECT username FROM iam_user ORDER BY id", String.class));
            assertEquals(List.of(AdminDemoSeedConstants.IDENTITY_ID), jdbc.queryForList(
                    "SELECT id FROM iam_identity WHERE user_id = ?",
                    String.class, AdminDemoSeedConstants.USER_ID));
            assertEquals(List.of("iam-admin"), jdbc.queryForList(
                    "SELECT template_key FROM iam_permission_template WHERE id = ?",
                    String.class, AdminDemoSeedConstants.TEMPLATE_ID));
            assertEquals(List.of("PUBLISHED"), jdbc.queryForList(
                    "SELECT status FROM iam_permission_template_version WHERE id = ?",
                    String.class, AdminDemoSeedConstants.TEMPLATE_VERSION_ID));
            assertEquals(List.of(FOREIGN_ADMIN_PERMISSION), jdbc.queryForList(
                    "SELECT permission_code FROM iam_permission WHERE permission_code = ?",
                    String.class, FOREIGN_ADMIN_PERMISSION));
            assertEquals(AdminDemoSeedConstants.permissionCodes(), jdbc.queryForList("""
                    SELECT p.permission_code
                    FROM iam_template_permission tp
                    JOIN iam_permission p ON p.id = tp.permission_id
                    WHERE tp.template_version_id = ?
                    ORDER BY p.permission_code
                    """, String.class, AdminDemoSeedConstants.TEMPLATE_VERSION_ID));
            assertEquals(AdminDemoSeedConstants.permissionCodes().size(),
                    jdbc.queryForObject("SELECT COUNT(*) FROM iam_template_permission WHERE template_version_id = ?",
                            Integer.class, AdminDemoSeedConstants.TEMPLATE_VERSION_ID));
            assertEquals(List.of("admin-console"), jdbc.queryForList(
                    "SELECT profile_key FROM iam_authorization_profile WHERE id = ?",
                    String.class, AdminDemoSeedConstants.PROFILE_ID));
            assertEquals(List.of("IAM_ADMIN:*:READ", "IAM_ADMIN:*:WRITE"), jdbc.queryForList("""
                    SELECT CONCAT(resource_type, ':', resource_id, ':', scope_access)
                    FROM iam_authorization_scope
                    WHERE profile_id = ?
                    ORDER BY scope_access
                    """, String.class, AdminDemoSeedConstants.PROFILE_ID));
        }
    }

    private static void assertAdminSeedAbsent(ConfigurableApplicationContext context) {
        var jdbc = context.getBean(JdbcTemplate.class);
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM iam_user", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM iam_permission_template", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM iam_authorization_profile", Long.class));
        assertTrue(AdminDemoSeedConstants.permissionCodes().size() > 0);
    }

    private static ConfigurableApplicationContext start(boolean devProfile, boolean seedEnabled) {
        var application = new SpringApplicationBuilder(MuerShowcaseApplication.class)
                .properties(
                        "server.port=0",
                        "spring.main.banner-mode=off");
        if (devProfile) application.profiles("dev");
        return application.run(
                "--spring.datasource.url=" + MYSQL.getJdbcUrl(),
                "--spring.datasource.username=" + MYSQL.getUsername(),
                "--spring.datasource.password=" + MYSQL.getPassword(),
                "--spring.data.redis.host=" + REDIS.getHost(),
                "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                "--muer.showcase.seed-admin=" + seedEnabled);
    }
}
