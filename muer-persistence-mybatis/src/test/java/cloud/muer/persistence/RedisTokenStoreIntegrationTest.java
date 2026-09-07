package cloud.muer.persistence;

import cloud.muer.core.model.IamPrincipal;
import cloud.muer.session.TokenRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisTokenStoreIntegrationTest {
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private static RedisTokenStore store;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        var template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        store = new RedisTokenStore(template, "test:iam:");
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) connectionFactory.destroy();
        REDIS.stop();
    }

    @Test
    void save_maintains_token_session_and_user_reverse_indexes() {
        var record = tokenRecord("session-1", 7L);

        store.save("opaque-1", record, Duration.ofMinutes(5));

        assertEquals(record, store.resolve("opaque-1").orElseThrow());
        assertEquals("session-1", store.sessionId("opaque-1").orElseThrow());
        assertTrue(store.hasTokenForSession("session-1", "opaque-1"));
        assertTrue(store.hasTokenForUser(7L, "opaque-1"));
    }

    @Test
    void revoke_session_removes_every_token_and_reverse_index_for_that_session() {
        store.save("opaque-2", tokenRecord("session-2", 8L), Duration.ofMinutes(5));
        store.save("opaque-3", tokenRecord("session-2", 8L), Duration.ofMinutes(5));
        assertTrue(store.acquireSessionTouchLease("session-2", Duration.ofMinutes(1)));
        assertTrue(!store.acquireSessionTouchLease("session-2", Duration.ofMinutes(1)));

        store.revokeSession("session-2");

        assertTrue(store.resolve("opaque-2").isEmpty());
        assertTrue(store.resolve("opaque-3").isEmpty());
        assertTrue(store.tokenDigestsForSession("session-2").isEmpty());
        assertTrue(store.tokenDigestsForUser(8L).isEmpty());
        assertTrue(store.acquireSessionTouchLease("session-2", Duration.ofMinutes(1)));
    }

    private static TokenRecord tokenRecord(String sessionId, long userId) {
        var principal = new IamPrincipal(userId, "identity-" + userId, "ACCOUNT", 3L, 5L, "WEB", 2L);
        return new TokenRecord(sessionId, principal, Instant.parse("2026-08-27T00:00:00Z"));
    }
}
