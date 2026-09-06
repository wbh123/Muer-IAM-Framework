package io.github.muer.example;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = IamExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "muer.example.seed-demo=true")
@ActiveProfiles("dev")
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
    ObjectMapper json;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void quickstart_reader_switches_to_an_isolated_editor_session_and_revokes_only_that_session() throws Exception {
        var reader = login();
        var update = "{\"status\":\"PUBLISHED\"}";

        var currentUser = request("/iam/auth/me", "GET", reader.token(), null);
        assertEquals(200, currentUser.statusCode(), currentUser.body());
        assertEquals(401L, json.readTree(currentUser.body()).path("activeProfileId").asLong());
        assertEquals(200, request("/api/documents/1001", "GET", reader.token(), null).statusCode());
        assertEquals(403, request("/api/documents/1001", "POST", reader.token(), update).statusCode());
        var crossProjectRead = request("/api/documents/2001", "GET", reader.token(), null);
        assertEquals(403, crossProjectRead.statusCode(), crossProjectRead.body());

        var diagnosis = request("/iam/authorization/diagnostics", "POST", reader.token(), """
                {"permissionCode":"document:update","domain":"EXAMPLE","clientType":"WEB",
                 "resourceType":"PROJECT","resourceId":"101","scopeAccess":"WRITE"}
                """);
        assertEquals(200, diagnosis.statusCode(), diagnosis.body());
        assertEquals(false, json.readTree(diagnosis.body()).path("allowed").asBoolean());

        var editor = switchProfile(reader, 402L);
        // Profile switch must create an independent session, never overwrite the reader session.
        assertNotEquals(reader.sessionId(), editor.sessionId(), "profile switch must create a distinct session");
        assertNotEquals(reader.token(), editor.token(), "profile switch must create a distinct token");
        // Both sessions coexist under the same principal.
        assertTrue(activeSessionIds(reader.token()).contains(reader.sessionId()),
                "reader session must be listed");
        assertTrue(activeSessionIds(reader.token()).contains(editor.sessionId()),
                "editor session must be listed as a separate active session");
        var updated = request("/api/documents/1001", "POST", editor.token(), update);
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals("PUBLISHED", json.readTree(updated.body()).path("status").asText());
        assertEquals(403, request("/api/documents/1001", "POST", reader.token(), update).statusCode());
        assertEquals(204, request("/iam/sessions/" + editor.sessionId() + "/revoke", "POST",
                editor.token(), "{\"reason\":\"QUICKSTART_COMPLETE\"}").statusCode());
        assertEquals(401, request("/api/documents/1001", "GET", editor.token(), null).statusCode());
        assertEquals(200, request("/api/documents/1001", "GET", reader.token(), null).statusCode());
        var remaining = activeSessionIds(reader.token());
        assertTrue(remaining.contains(reader.sessionId()), "reader session must survive editor revoke");
        assertFalse(remaining.contains(editor.sessionId()), "revoked editor session must no longer be active");
    }

    private Login login() throws Exception {
        var response = request("/iam/auth/login", "POST", null,
                "{\"username\":\"alice\",\"password\":\"demo-pass\",\"clientType\":\"WEB\"}");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        return new Login(body.path("accessToken").asText(), body.path("sessionId").asText());
    }

    private Login switchProfile(Login current, long profileId) throws Exception {
        var response = request("/iam/authorization/profiles/" + profileId + "/switch", "POST", current.token(), null);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        return new Login(body.path("accessToken").asText(), body.path("sessionId").asText());
    }

    private Set<String> activeSessionIds(String token) throws Exception {
        var response = request("/iam/sessions", "GET", token, null);
        assertEquals(200, response.statusCode(), response.body());
        Set<String> ids = new HashSet<>();
        for (JsonNode session : json.readTree(response.body()).path("items")) {
            ids.add(session.path("sessionId").asText());
        }
        return ids;
    }

    private HttpResponse<String> request(String path, String method, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private record Login(String token, String sessionId) { }
}
