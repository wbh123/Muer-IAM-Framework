package io.github.iamstarter.example;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.github.iamstarter.authentication.AuthenticationService;
import io.github.iamstarter.session.SessionRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = IamExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IamStarterConsumptionTest {
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("iam_example")
            .withUsername("iam")
            .withPassword("iam-secret");

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
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

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void seedIdentityProjection() {
        jdbc.update("DELETE FROM iam_session");
        jdbc.update("DELETE FROM iam_authorization_scope");
        jdbc.update("DELETE FROM iam_authorization_profile");
        jdbc.update("DELETE FROM iam_template_permission");
        jdbc.update("DELETE FROM iam_permission_template_version");
        jdbc.update("DELETE FROM iam_permission_template");
        jdbc.update("DELETE FROM iam_permission");
        jdbc.update("DELETE FROM iam_user");
        jdbc.update("""
                INSERT INTO iam_user (id, external_ref, username, user_type, authorization_version)
                VALUES (101, 'example-user', 'example-user', 'MEMBER', 1)
                """);
        jdbc.update("INSERT INTO iam_permission_template (id, template_key, display_name) VALUES (201, 'example', 'Example')");
        jdbc.update("INSERT INTO iam_permission_template_version (id, template_id, version_number, status) VALUES (301, 201, 1, 'PUBLISHED')");
        jdbc.update("INSERT INTO iam_permission (id, permission_code, display_name) VALUES (601, 'order.read', 'Read order')");
        jdbc.update("INSERT INTO iam_template_permission (template_version_id, permission_id) VALUES (301, 601)");
        jdbc.update("""
                INSERT INTO iam_authorization_profile
                    (id, user_id, template_version_id, profile_key, display_name, client_types, enabled, revoked_at)
                VALUES (401, 101, 301, 'default', 'Default', '[\"WEB\"]', TRUE, NULL)
                """);
        jdbc.update("""
                INSERT INTO iam_authorization_scope
                    (profile_id, resource_type, resource_id, scope_access)
                VALUES (401, 'DEPARTMENT', '501', 'READ')
                """);
    }

    @Test
    void one_starter_dependency_auto_configures_the_runtime() {
        assertNotNull(context.getBean(AuthenticationService.class));
        assertNotNull(context.getBean(SessionRepository.class));
    }

    @Test
    void login_token_and_persisted_session_work_across_real_infrastructure() throws Exception {
        var login = request("/iam/auth/login", "POST", null,
                "{\"username\":\"demo\",\"password\":\"demo-pass\",\"clientType\":\"WEB\"}");
        assertEquals(200, login.statusCode());
        JsonNode loginBody = json.readTree(login.body());
        String token = loginBody.path("accessToken").asText();
        String sessionId = loginBody.path("sessionId").asText();

        var sessions = request("/iam/sessions", "GET", token, null);

        assertEquals(200, sessions.statusCode());
        JsonNode firstSession = json.readTree(sessions.body()).path("items").get(0);
        assertEquals(sessionId, firstSession.path("sessionId").asText());
        assertEquals(101L, firstSession.path("userId").asLong());

        assertEquals(200, request("/example/orders/9001", "GET", token, null).statusCode());
        assertEquals(403, request("/example/orders/9002", "GET", token, null).statusCode());
    }

    private HttpResponse<String> request(String path, String method, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
