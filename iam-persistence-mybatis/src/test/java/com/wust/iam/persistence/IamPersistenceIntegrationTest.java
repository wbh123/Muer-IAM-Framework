package com.wust.iam.persistence;

import org.flywaydb.core.Flyway;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IamPersistenceIntegrationTest {
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("iam")
            .withUsername("iam")
            .withPassword("iam-secret")
            .withCommand("--log-bin-trust-function-creators=1");
    private static int migrationsExecuted;

    @BeforeAll
    static void startMySql() {
        MYSQL.start();
        migrationsExecuted = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/iam/migration")
                .load()
                .migrate()
                .migrationsExecuted;
    }

    @AfterAll
    static void stopMySql() {
        MYSQL.stop();
    }

    @Test
    void independent_migrations_create_the_complete_generic_schema() throws Exception {
        assertEquals(6, migrationsExecuted);
        assertEquals(Set.of(
                "iam_audit_log", "iam_audit_subject_link", "iam_authorization_profile",
                "iam_authorization_scope", "iam_identity", "iam_login_event", "iam_permission",
                "iam_permission_template", "iam_permission_template_version", "iam_session",
                "iam_template_permission", "iam_user"), tableNames());
    }

    @Test
    void session_repository_round_trips_and_excludes_revoked_sessions() throws Exception {
        insertUser(41L);
        var repository = new MyBatisSessionRepository(sessionFactory());
        Instant loginAt = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        var active = new com.wust.iam.session.AuthSession("session-41", 41L, "WEB", "browser-41",
                "203.0.113.41", "integration-browser/1.0", loginAt, loginAt,
                loginAt.plus(Duration.ofDays(1)), null, null, null);

        repository.save(active);

        assertEquals(active, repository.findById("session-41").orElseThrow());
        assertEquals(java.util.List.of(active), repository.activeForUser(41L));

        var touchedAt = loginAt.plusSeconds(30);
        repository.touch("session-41", touchedAt);
        var touched = repository.findById("session-41").orElseThrow();
        assertEquals(touchedAt, touched.lastSeenAt());
        assertEquals(active.loginAt(), touched.loginAt());

        repository.save(touched.revoke(loginAt.plusSeconds(60), "USER_REQUEST"));

        assertTrue(repository.activeForUser(41L).isEmpty());
    }

    @Test
    void authorization_version_increment_is_atomic_and_persisted() throws Exception {
        insertUser(42L);
        var repository = new MyBatisAuthorizationVersionRepository(sessionFactory());

        assertEquals(1L, repository.currentVersion(42L));
        assertEquals(2L, repository.increment(42L));
        assertEquals(2L, new MyBatisAuthorizationVersionRepository(sessionFactory()).currentVersion(42L));
    }

    @Test
    void account_governance_repositories_round_trip_users_and_identities_with_pagination() throws Exception {
        var users = new MyBatisIamUserRepository(sessionFactory());
        var identities = new MyBatisIdentityRepository(sessionFactory());
        var user = new com.wust.iam.core.model.IamUser(45L, "account-45", "MEMBER", true, 1L);
        users.save(user);
        identities.save(new com.wust.iam.core.model.Identity(
                "identity-45", 45L, "account-45@example.test", "ACCOUNT", true));

        assertEquals(user, users.findById(45L).orElseThrow());
        assertEquals(List.of(user), users.findPage(44L, 1));
        assertEquals("account-45@example.test", identities.findByUserId(45L).getFirst().identityKey());
    }

    @Test
    void login_event_repository_appends_a_queryable_security_event() throws Exception {
        insertUser(44L);
        var repository = new MyBatisLoginEventRepository(sessionFactory());
        repository.append(new com.wust.iam.session.LoginEvent(
                "login-44", 44L, "alex", "ACCOUNT", "WEB", "browser-44",
                "203.0.113.44", "integration-browser/1.0", "DESKTOP", "Linux", "Firefox", "1.2.0",
                "session-44", com.wust.iam.session.LoginResult.SUCCESS, null, "request-44",
                Instant.parse("2026-08-26T02:00:00Z")));

        try (var connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.prepareStatement("""
                     SELECT result_code, session_id, identity_key
                     FROM iam_login_event WHERE event_id = ?
                     """)) {
            statement.setString(1, "login-44");
            try (var row = statement.executeQuery()) {
                assertTrue(row.next());
                assertEquals("SUCCESS", row.getString(1));
                assertEquals("session-44", row.getString(2));
                assertEquals("alex", row.getString(3));
            }
        }
    }

    @Test
    void authorization_profile_round_trips_multiple_clients_and_replaces_scopes_atomically() throws Exception {
        insertUser(43L);
        insertTemplateVersion(61L, 60L);
        var repository = new MyBatisAuthorizationProfileRepository(sessionFactory());
        var original = new com.wust.iam.authorization.AuthorizationProfile(
                51L, 43L, "operations", 61L, Set.of("WEB", "MOBILE"), true, false,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                List.of(new com.wust.iam.core.model.ResourceScope(
                        "DEPARTMENT", "7", com.wust.iam.core.model.ScopeAccess.READ)));

        repository.save(original);
        assertEquals(original, repository.require(51L));
        assertEquals(List.of(original), repository.findByUserId(43L));

        var replacement = original.withScopes(List.of(new com.wust.iam.core.model.ResourceScope(
                "DEPARTMENT", "9", com.wust.iam.core.model.ScopeAccess.WRITE)));
        repository.save(replacement);

        assertEquals(replacement, new MyBatisAuthorizationProfileRepository(sessionFactory()).require(51L));
        assertEquals(List.of(replacement),
                new MyBatisAuthorizationProfileRepository(sessionFactory()).findByUserId(43L));
    }

    @Test
    void scope_and_authorization_version_mutation_rolls_back_together_on_version_failure() throws Exception {
        insertUser(46L);
        insertTemplateVersion(63L, 62L);
        var profiles = new MyBatisAuthorizationProfileRepository(sessionFactory());
        var original = new com.wust.iam.authorization.AuthorizationProfile(
                52L, 46L, "security", 63L, Set.of("WEB"), true, false, null, null,
                List.of(new com.wust.iam.core.model.ResourceScope(
                        "DEPARTMENT", "7", com.wust.iam.core.model.ScopeAccess.READ)));
        profiles.save(original);
        executeSql("""
                CREATE TRIGGER fail_iam_authorization_version
                BEFORE UPDATE ON iam_user FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced version failure'
                """);
        try {
            var mutation = new MyBatisAuthorizationScopeMutation(sessionFactory());

            assertThrows(RuntimeException.class, () -> mutation.replaceScopesAndIncrementVersion(
                    52L, List.of(new com.wust.iam.core.model.ResourceScope(
                            "DEPARTMENT", "9", com.wust.iam.core.model.ScopeAccess.WRITE))));
        } finally {
            executeSql("DROP TRIGGER IF EXISTS fail_iam_authorization_version");
        }

        assertEquals(original, new MyBatisAuthorizationProfileRepository(sessionFactory()).require(52L));
        assertEquals(1L, new MyBatisAuthorizationVersionRepository(sessionFactory()).currentVersion(46L));
    }

    @Test
    void permission_template_version_round_trips_and_replaces_permission_links_atomically() throws Exception {
        insertTemplate(70L);
        var repository = new MyBatisPermissionTemplateVersionRepository(sessionFactory());
        var draft = new com.wust.iam.authorization.PermissionTemplateVersion(
                71L, 70L, 1, com.wust.iam.authorization.TemplateVersionStatus.DRAFT,
                Set.of("account.read", "account.write"));

        repository.save(draft);
        assertEquals(draft, repository.require(71L));

        var published = new com.wust.iam.authorization.PermissionTemplateVersion(
                71L, 70L, 1, com.wust.iam.authorization.TemplateVersionStatus.PUBLISHED,
                Set.of("account.read"));
        repository.save(published);

        assertEquals(published, new MyBatisPermissionTemplateVersionRepository(sessionFactory()).require(71L));
    }

    @Test
    void audit_append_persists_json_and_subjects_and_rolls_back_partial_writes() throws Exception {
        var repository = new MyBatisAuditRepository(sessionFactory());
        var record = new com.wust.iam.audit.AuditRecord(
                "audit-81", "service:operator", "account.update", "ACCOUNT", "81", "SUCCESS", "request-81",
                Map.of("name", "before"), Map.of("name", "after"),
                Map.of("source", "management \"api\"\nline"),
                Instant.parse("2026-08-26T08:30:00Z"));
        var subject = new com.wust.iam.audit.AuditSubjectLink(
                "ACCOUNT", "81", com.wust.iam.audit.AuditSubjectRelation.PRIMARY);

        repository.append(record, List.of(subject));

        assertEquals(new StoredAudit("service:operator", "before", "after", "management \"api\"\nline", 1),
                storedAudit("audit-81"));

        var duplicate = new com.wust.iam.audit.AuditRecord(
                "audit-82", "service:operator", "account.update", "ACCOUNT", "82", "FAILED", "request-82",
                Map.of(), Map.of(), Map.of(), Instant.parse("2026-08-26T08:31:00Z"));
        assertThrows(RuntimeException.class, () -> repository.append(duplicate, List.of(subject, subject)));
        assertEquals(0, auditCount("audit-82"));
    }

    private static Set<String> tableNames() throws Exception {
        var names = new TreeSet<String>();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.prepareStatement("""
                     SELECT table_name
                     FROM information_schema.tables
                     WHERE table_schema = ? AND table_name LIKE 'iam\\_%'
                     """)) {
            statement.setString(1, MYSQL.getDatabaseName());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) names.add(rows.getString(1));
            }
        }
        return names;
    }

    private static void insertUser(long userId) throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.prepareStatement(
                     "INSERT INTO iam_user (id, external_ref, username, user_type) VALUES (?, ?, ?, 'MEMBER')")) {
            statement.setLong(1, userId);
            statement.setString(2, "ref-" + userId);
            statement.setString(3, "account-" + userId);
            statement.executeUpdate();
        }
    }

    private static void insertTemplateVersion(long versionId, long templateId) throws Exception {
        insertTemplate(templateId);
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            try (var version = connection.prepareStatement("""
                    INSERT INTO iam_permission_template_version (id, template_id, version_number, status)
                    VALUES (?, ?, 1, 'DRAFT')
                    """)) {
                version.setLong(1, versionId);
                version.setLong(2, templateId);
                version.executeUpdate();
            }
        }
    }

    private static void insertTemplate(long templateId) throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var template = connection.prepareStatement("""
                     INSERT INTO iam_permission_template (id, template_key, display_name)
                     VALUES (?, ?, ?)
                     """)) {
            template.setLong(1, templateId);
            template.setString(2, "template-" + templateId);
            template.setString(3, "Template " + templateId);
            template.executeUpdate();
        }
    }

    private static void executeSql(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static StoredAudit storedAudit(String auditId) throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.prepareStatement("""
                     SELECT audit.operator_ref,
                            JSON_UNQUOTE(JSON_EXTRACT(audit.before_state, '$.name')),
                            JSON_UNQUOTE(JSON_EXTRACT(audit.after_state, '$.name')),
                            JSON_UNQUOTE(JSON_EXTRACT(audit.metadata, '$.source')),
                            COUNT(subject.id)
                     FROM iam_audit_log audit
                     LEFT JOIN iam_audit_subject_link subject ON subject.audit_log_id = audit.id
                     WHERE audit.event_id = ?
                     GROUP BY audit.id
                     """)) {
            statement.setString(1, auditId);
            try (var row = statement.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("audit event missing: " + auditId);
                return new StoredAudit(row.getString(1), row.getString(2), row.getString(3), row.getString(4),
                        row.getInt(5));
            }
        }
    }

    private static int auditCount(String auditId) throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM iam_audit_log WHERE event_id = ?")) {
            statement.setString(1, auditId);
            try (var row = statement.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        }
    }

    private static SqlSessionFactory sessionFactory() throws Exception {
        var dataSource = new UnpooledDataSource(
                "com.mysql.cj.jdbc.Driver", MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var environment = new Environment("integration", new JdbcTransactionFactory(), dataSource);
        var configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        for (String resource : java.util.List.of(
                "mapper/iam/IamSessionMapper.xml",
                "mapper/iam/IamUserMapper.xml",
                "mapper/iam/IamIdentityMapper.xml",
                "mapper/iam/IamLoginEventMapper.xml",
                "mapper/iam/IamAuthorizationVersionMapper.xml",
                "mapper/iam/IamAuthorizationProfileMapper.xml",
                "mapper/iam/IamPermissionTemplateVersionMapper.xml",
                "mapper/iam/IamAuditMapper.xml")) {
            try (var reader = Resources.getResourceAsReader(resource)) {
                new XMLMapperBuilder(reader, configuration, resource,
                        configuration.getSqlFragments()).parse();
            }
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private record StoredAudit(String operator, String beforeName, String afterName, String source,
                               int subjectCount) {
    }
}
