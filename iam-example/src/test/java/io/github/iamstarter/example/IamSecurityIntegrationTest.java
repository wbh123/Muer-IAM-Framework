package io.github.iamstarter.example;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = IamExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IamSecurityIntegrationTest {
    private final IamShowcaseFixture fixture = new IamShowcaseFixture();

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("iam_security")
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
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void seedIdentityProjection() {
        fixture.seedOperatorA(jdbc);
    }

    @Test
    void revoked_session_rejects_reuse_of_its_opaque_token() throws Exception {
        var login = login();

        assertEquals(204, request("/iam/sessions/" + login.sessionId() + "/revoke", "POST", login.token(), "{}").statusCode());
        assertEquals(401, request("/example/orders/9001", "GET", login.token(), null).statusCode());
    }

    @Test
    void authorization_version_change_rejects_an_already_issued_token() throws Exception {
        var login = login();
        jdbc.update("UPDATE iam_user SET authorization_version = 2 WHERE id = 101");

        assertEquals(401, request("/example/orders/9001", "GET", login.token(), null).statusCode());
    }

    @Test
    void resource_id_tampering_and_direct_url_bypass_are_denied() throws Exception {
        var login = login();

        assertEquals(403, request("/example/orders/9002", "GET", login.token(), null).statusCode());
        assertEquals(401, request("/example/orders/9001", "GET", null, null).statusCode());
    }

    @Test
    void switched_profile_uses_a_new_listed_session_and_revocation_rejects_only_its_token() throws Exception {
        fixture.seedOperatorAProfiles(jdbc);
        var reader = login();

        var switched = switchProfile(reader.token(), IamShowcaseFixture.APPROVER_501_PROFILE_ID);

        assertNotEquals(reader.token(), switched.token());
        assertNotEquals(reader.sessionId(), switched.sessionId());
        assertEquals(403, request("/example/orders/9001/approve", "POST", reader.token(), null).statusCode());
        assertEquals(200, request("/example/orders/9001/approve", "POST", switched.token(), null).statusCode());
        assertEquals(200, request("/example/orders/9001", "GET", reader.token(), null).statusCode());
        assertEquals(200, request("/iam/sessions", "GET", switched.token(), null).statusCode());
        assertTrue(listedSessionIds(switched.token()).anyMatch(switched.sessionId()::equals));
        assertEquals(204, request("/iam/sessions/" + switched.sessionId() + "/revoke", "POST", switched.token(), "{}").statusCode());
        assertEquals(401, request("/example/orders/9001", "GET", switched.token(), null).statusCode());
        assertEquals(200, request("/example/orders/9001", "GET", reader.token(), null).statusCode());
    }

    private Login login() throws Exception {
        var response = request("/iam/auth/login", "POST", null,
                "{\"username\":\"operator-a\",\"password\":\"demo-pass\",\"clientType\":\"WEB\"}");
        assertEquals(200, response.statusCode());
        JsonNode body = json.readTree(response.body());
        return new Login(body.path("accessToken").asText(), body.path("sessionId").asText());
    }

    private Login switchProfile(String token, long profileId) throws Exception {
        var response = request("/iam/authorization/profiles/" + profileId + "/switch", "POST", token, null);
        assertEquals(200, response.statusCode());
        JsonNode body = json.readTree(response.body());
        return new Login(body.path("accessToken").asText(), body.path("sessionId").asText());
    }

    private java.util.stream.Stream<String> listedSessionIds(String token) throws Exception {
        var sessions = request("/iam/sessions", "GET", token, null);
        JsonNode items = json.readTree(sessions.body()).path("items");
        return StreamSupport.stream(items.spliterator(), false).map(item -> item.path("sessionId").asText());
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
