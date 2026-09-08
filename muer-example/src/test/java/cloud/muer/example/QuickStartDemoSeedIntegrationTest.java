package cloud.muer.example;

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

class QuickStartDemoSeedIntegrationTest {
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("iam_quickstart_seed")
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
    void demo_seed_requires_dev_profile_and_explicit_opt_in_then_creates_the_quickstart_scenario() {
        try (var defaults = start(false, false)) {
            assertDemoSeedAbsent(defaults);
        }
        try (var devOnly = start(true, false)) {
            assertDemoSeedAbsent(devOnly);
        }
        try (var propertyOnly = start(false, true)) {
            assertDemoSeedAbsent(propertyOnly);
        }

        try (var enabled = start(true, true)) {
            var jdbc = enabled.getBean(JdbcTemplate.class);
            var documents = enabled.getBean(DocumentCatalog.class);

            assertEquals(List.of("alice"), jdbc.queryForList(
                    "SELECT username FROM iam_user ORDER BY id", String.class));
            assertEquals(List.of("alice-editor-project-101", "alice-reader-project-101"), jdbc.queryForList("""
                    SELECT profile_key FROM iam_authorization_profile ORDER BY profile_key
                    """, String.class));
            assertEquals(List.of("document:read", "document:update"), jdbc.queryForList("""
                    SELECT permission_code FROM iam_permission ORDER BY permission_code
                    """, String.class));
            assertEquals(List.of("PROJECT:101:READ", "PROJECT:101:READ", "PROJECT:101:WRITE"),
                    jdbc.queryForList("""
                            SELECT CONCAT(resource_type, ':', resource_id, ':', scope_access)
                            FROM iam_authorization_scope
                            ORDER BY scope_access, profile_id
                            """, String.class));
            assertEquals("101", documents.find("1001").orElseThrow().projectId());
            assertEquals("202", documents.find("2001").orElseThrow().projectId());
        }
    }

    private static void assertDemoSeedAbsent(ConfigurableApplicationContext context) {
        var jdbc = context.getBean(JdbcTemplate.class);
        var documents = context.getBean(DocumentCatalog.class);
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM iam_user", Long.class));
        assertTrue(documents.find("1001").isEmpty());
        assertTrue(documents.find("2001").isEmpty());
    }

    private static ConfigurableApplicationContext start(boolean devProfile, boolean seedEnabled) {
        var application = new SpringApplicationBuilder(IamExampleApplication.class)
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
                "--muer.example.seed-demo=" + seedEnabled);
    }
}
