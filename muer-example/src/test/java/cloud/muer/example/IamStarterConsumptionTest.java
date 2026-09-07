package cloud.muer.example;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import cloud.muer.authentication.AuthenticationService;
import cloud.muer.session.SessionRepository;
import cloud.muer.web.dto.AuthorizationEvaluationRequest;
import org.junit.jupiter.api.AfterAll;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = IamExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IamStarterConsumptionTest {
    private final IamShowcaseFixture fixture = new IamShowcaseFixture();

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

    @Test
    void one_starter_dependency_auto_configures_the_runtime() {
        assertNotNull(context.getBean(AuthenticationService.class));
        assertNotNull(context.getBean(SessionRepository.class));
    }

    @Test
    void seeded_reader_profile_can_read_only_its_department_through_operator_a_principal() throws Exception {
        fixture.seedOperatorA(jdbc);

        assertEquals(List.of("READ"), jdbc.queryForList("""
                SELECT scope_access FROM iam_authorization_scope
                WHERE profile_id = 401 AND resource_type = 'DEPARTMENT' AND resource_id = '501'
                ORDER BY scope_access
                """, String.class));
        assertEquals(200, requestAsOperatorA("/example/orders/9001").statusCode());
        assertEquals(403, requestAsOperatorA("/example/orders/9002").statusCode());
    }

    @Test
    void login_token_and_persisted_session_work_across_real_infrastructure() throws Exception {
        fixture.seedOperatorA(jdbc);
        var login = request("/iam/auth/login", "POST", null,
                "{\"username\":\"operator-a\",\"password\":\"demo-pass\",\"clientType\":\"WEB\"}");
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

    @Test
    void profile_switch_changes_order_approval_from_reader_denial_to_approver_success() throws Exception {
        fixture.seedOperatorAProfiles(jdbc);
        String readerToken = loginToken("operator-a");

        assertEquals(403, request("/example/orders/9001/approve", "POST", readerToken, null).statusCode());
        var profiles = request("/iam/authorization/profiles", "GET", readerToken, null);
        assertEquals(200, profiles.statusCode());
        assertEquals(2, json.readTree(profiles.body()).size());

        var switchResponse = request("/iam/authorization/profiles/" + IamShowcaseFixture.APPROVER_501_PROFILE_ID + "/switch", "POST", readerToken, null);
        assertEquals(200, switchResponse.statusCode());
        String approverToken = json.readTree(switchResponse.body()).path("accessToken").asText();

        assertEquals(200, request("/example/orders/9001/approve", "POST", approverToken, null).statusCode());
    }

    @Test
    void authorization_diagnostics_projects_denied_and_allowed_runtime_decisions() throws Exception {
        fixture.seedOperatorAProfiles(jdbc);
        String readerToken = loginToken("operator-a");

        var denied = diagnose(readerToken, authorizationRequest(
                "order.approve", "ORDER", "9001", AuthorizationEvaluationRequest.ScopeAccessEnum.WRITE));

        assertEquals(false, denied.allowed());
        assertEquals("PERMISSION_DENIED", denied.decisionCode());
        assertTrue(denied.steps().stream().anyMatch(step -> step.code().contains("PERMISSION")));

        var allowed = diagnose(readerToken, authorizationRequest(
                "order.read", "DEPARTMENT", "501", AuthorizationEvaluationRequest.ScopeAccessEnum.READ));

        assertEquals(true, allowed.allowed());
        assertEquals("ALLOWED", allowed.decisionCode());
    }

    @Test
    void rejected_client_or_credentials_do_not_create_a_session() throws Exception {
        fixture.seedOperatorA(jdbc);

        assertEquals(401, login("operator-a", "demo-pass", "MOBILE").statusCode());
        assertEquals(401, login("operator-a", "wrong", "WEB").statusCode());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM iam_session", Long.class));
    }

    private HttpResponse<String> request(String path, String method, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> requestAsOperatorA(String path) throws Exception {
        return request(path, "GET", loginToken("operator-a"), null);
    }

    private String loginToken(String username) throws Exception {
        var login = request("/iam/auth/login", "POST", null,
                "{\"username\":\"%s\",\"password\":\"demo-pass\",\"clientType\":\"WEB\"}".formatted(username));
        assertEquals(200, login.statusCode(), "%s must authenticate".formatted(username));
        return json.readTree(login.body()).path("accessToken").asText();
    }

    private HttpResponse<String> login(String username, String password, String clientType) throws Exception {
        return request("/iam/auth/login", "POST", null, """
                {"username":"%s","password":"%s","clientType":"%s"}
                """.formatted(username, password, clientType));
    }

    private AuthorizationEvaluationRequest authorizationRequest(String permissionCode, String resourceType,
                                                                  String resourceId,
                                                                  AuthorizationEvaluationRequest.ScopeAccessEnum scopeAccess) {
        return new AuthorizationEvaluationRequest(permissionCode, "EXAMPLE", "WEB", resourceType, resourceId, scopeAccess);
    }

    private Diagnosis diagnose(String token, AuthorizationEvaluationRequest authorizationRequest) throws Exception {
        var response = request("/iam/authorization/diagnostics", "POST", token,
                json.writeValueAsString(authorizationRequest));
        assertEquals(200, response.statusCode());
        JsonNode body = json.readTree(response.body());
        return new Diagnosis(body.path("allowed").asBoolean(), body.path("decisionCode").asText(), body.path("steps").valueStream()
                .map(step -> new DiagnosisStep(step.path("code").asText()))
                .toList());
    }

    private record Diagnosis(boolean allowed, String decisionCode, List<DiagnosisStep> steps) {
    }

    private record DiagnosisStep(String code) {
    }
}
