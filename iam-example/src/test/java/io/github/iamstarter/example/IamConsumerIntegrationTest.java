package io.github.iamstarter.example;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = IamExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IamConsumerIntegrationTest {
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("iam_consumer")
            .withUsername("iam")
            .withPassword("iam-secret");
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void generic_consumer_fixture_seeds_admin_reader_permissions_and_project_scope() {
        new IamShowcaseFixture().seedIndependentConsumer(jdbc);

        assertEquals(4L, jdbc.queryForObject("SELECT COUNT(*) FROM iam_permission", Long.class));
    }

    @Test
    void document_routes_enforce_permission_and_project_scope_from_a_real_bearer_token() throws Exception {
        new IamShowcaseFixture().seedIndependentConsumer(jdbc);

        var author = login("author-a");
        var reader = login("reader-b");

        assertEquals(200, request("/iam/auth/me", "GET", author, null).statusCode());
        var authorRead = request("/api/documents/1001", "GET", author, null);
        assertEquals(200, authorRead.statusCode(), authorRead.body());
        assertEquals(200, request("/api/documents/1001", "GET", reader, null).statusCode());
        var crossProjectRead = request("/api/documents/2001", "GET", reader, null);
        assertEquals(403, crossProjectRead.statusCode(), crossProjectRead.body());
        assertEquals(401, request("/api/documents/1001", "GET", null, null).statusCode());
    }

    @Test
    void document_updates_require_the_document_update_permission_and_write_scope() throws Exception {
        new IamShowcaseFixture().seedIndependentConsumer(jdbc);

        var author = login("author-a");
        var reader = login("reader-b");
        var update = "{\"status\":\"PUBLISHED\"}";

        assertEquals(401, request("/api/documents/1001", "POST", null, update).statusCode());
        assertEquals(403, request("/api/documents/1001", "POST", reader, update).statusCode());
        var updated = request("/api/documents/1001", "POST", author, update);
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals("PUBLISHED", json.readTree(updated.body()).path("status").asText());
    }

    private String login(String username) throws Exception {
        var response = request("/iam/auth/login", "POST", null,
                "{\"username\":\"%s\",\"password\":\"demo-pass\",\"clientType\":\"WEB\"}".formatted(username));
        assertEquals(200, response.statusCode());
        JsonNode body = json.readTree(response.body());
        return body.path("accessToken").asText();
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
