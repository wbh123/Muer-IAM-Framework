package cloud.muer.acceptance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = ConsumerAcceptanceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsumerAcceptanceIntegrationTest {
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("iam_acceptance")
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
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void seedProjection() {
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

        jdbc.update("INSERT INTO iam_user (id, external_ref, username, user_type, authorization_version) "
                + "VALUES (701, 'acceptance-user', 'acceptance-user', 'MEMBER', 1)");
        jdbc.update("INSERT INTO iam_identity (id, user_id, identity_key, identity_domain, credential_ref) "
                + "VALUES ('acceptance-identity', 701, 'acceptance-user', 'ACCEPTANCE', 'acceptance-pass')");
        jdbc.update("INSERT INTO iam_permission (id, permission_code, display_name) "
                + "VALUES (701, 'acceptance:read', 'Acceptance read')");
        jdbc.update("INSERT INTO iam_permission_template (id, template_key, display_name) "
                + "VALUES (701, 'acceptance-template', 'Acceptance template')");
        jdbc.update("INSERT INTO iam_permission_template_version "
                + "(id, template_id, version_number, status, published_at) "
                + "VALUES (701, 701, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6))");
        jdbc.update("INSERT INTO iam_template_permission (template_version_id, permission_id) VALUES (701, 701)");
        jdbc.update("INSERT INTO iam_authorization_profile "
                + "(id, user_id, template_version_id, profile_key, display_name, client_types, enabled, default_profile) "
                + "VALUES (701, 701, 701, 'acceptance-profile', 'Acceptance profile', '[\"WEB\"]', TRUE, TRUE)");
        jdbc.update("INSERT INTO iam_authorization_scope "
                + "(profile_id, resource_type, resource_id, scope_access) VALUES (701, 'PROJECT', '701', 'READ')");
    }

    @Test
    void independent_consumer_can_login_and_enforce_permission_and_scope() throws Exception {
        Login login = login();

        assertEquals(200, request("/acceptance/resources/701", "GET", login.token(), null).statusCode());
        assertEquals(403, request("/acceptance/resources/702", "GET", login.token(), null).statusCode());
    }

    @Test
    void independent_consumer_rejects_a_revoked_session_token() throws Exception {
        Login login = login();

        assertEquals(204, request("/iam/sessions/" + login.sessionId() + "/revoke", "POST", login.token(), "{}").statusCode());
        assertEquals(401, request("/acceptance/resources/701", "GET", login.token(), null).statusCode());
    }

    private Login login() throws Exception {
        var response = request("/iam/auth/login", "POST", null,
                "{\"username\":\"acceptance-user\",\"password\":\"acceptance-pass\",\"clientType\":\"WEB\"}");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        return new Login(body.path("accessToken").asText(), body.path("sessionId").asText());
    }

    private HttpResponse<String> request(String path, String method, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private record Login(String token, String sessionId) {
    }
}
