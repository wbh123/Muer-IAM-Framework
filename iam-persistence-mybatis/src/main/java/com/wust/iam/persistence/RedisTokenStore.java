package com.wust.iam.persistence;

import com.wust.iam.session.TokenRecord;
import com.wust.iam.session.TokenStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RedisTokenStore implements TokenStore {
    private static final DefaultRedisScript<Long> SAVE = script("""
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[5])
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[5])
            redis.call('SET', KEYS[3], ARGV[3], 'PX', ARGV[5])
            redis.call('SADD', KEYS[4], ARGV[4])
            redis.call('PEXPIRE', KEYS[4], ARGV[5])
            redis.call('SADD', KEYS[5], ARGV[4])
            redis.call('PEXPIRE', KEYS[5], ARGV[5])
            return 1
            """);
    private static final DefaultRedisScript<Long> REVOKE = script("""
            local sessionId = redis.call('GET', KEYS[2])
            local userId = redis.call('GET', KEYS[3])
            redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])
            if sessionId then redis.call('SREM', ARGV[1] .. sessionId .. ':tokens', ARGV[3]) end
            if userId then redis.call('SREM', ARGV[2] .. userId .. ':tokens', ARGV[3]) end
            return 1
            """);
    private static final DefaultRedisScript<Long> REVOKE_SESSION = script("""
            local digests = redis.call('SMEMBERS', KEYS[1])
            for _, digest in ipairs(digests) do
              local userKey = ARGV[1] .. digest .. ':user'
              local userId = redis.call('GET', userKey)
              redis.call('DEL', ARGV[1] .. digest .. ':record', ARGV[1] .. digest .. ':session', userKey)
              if userId then redis.call('SREM', ARGV[2] .. userId .. ':tokens', digest) end
            end
            redis.call('DEL', KEYS[1], KEYS[2])
            return #digests
            """);
    private static final DefaultRedisScript<Long> REVOKE_USER = script("""
            local digests = redis.call('SMEMBERS', KEYS[1])
            for _, digest in ipairs(digests) do
              local sessionKey = ARGV[1] .. digest .. ':session'
              local sessionId = redis.call('GET', sessionKey)
              redis.call('DEL', ARGV[1] .. digest .. ':record', sessionKey, ARGV[1] .. digest .. ':user')
              if sessionId then
                redis.call('SREM', ARGV[2] .. sessionId .. ':tokens', digest)
                redis.call('DEL', ARGV[2] .. sessionId .. ':touch')
              end
            end
            redis.call('DEL', KEYS[1])
            return #digests
            """);
    private static final DefaultRedisScript<Long> REFRESH = script("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            local sessionId = redis.call('GET', KEYS[2])
            local userId = redis.call('GET', KEYS[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            redis.call('PEXPIRE', KEYS[2], ARGV[3])
            redis.call('PEXPIRE', KEYS[3], ARGV[3])
            if sessionId then redis.call('PEXPIRE', ARGV[1] .. sessionId .. ':tokens', ARGV[3]) end
            if userId then redis.call('PEXPIRE', ARGV[2] .. userId .. ':tokens', ARGV[3]) end
            return 1
            """);

    private final StringRedisTemplate redis;
    private final String prefix;
    private final TokenRecordCodec codec = new TokenRecordCodec();

    public RedisTokenStore(StringRedisTemplate redis, String prefix) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("prefix must not be blank");
        this.prefix = prefix.endsWith(":") ? prefix : prefix + ":";
    }

    @Override
    public void save(String token, TokenRecord record, Duration ttl) {
        Objects.requireNonNull(record, "record must not be null");
        long ttlMillis = requirePositiveTtl(ttl);
        String digest = digest(token);
        redis.execute(SAVE, List.of(recordKey(digest), sessionKey(digest), userKey(digest),
                        sessionTokensKey(record.sessionId()), userTokensKey(record.principal().userId())),
                codec.encode(record), record.sessionId(), Long.toString(record.principal().userId()), digest,
                Long.toString(ttlMillis));
    }

    @Override
    public Optional<TokenRecord> resolve(String token) {
        return Optional.ofNullable(redis.opsForValue().get(recordKey(digest(token)))).map(codec::decode);
    }

    @Override
    public void revoke(String token) {
        String digest = digest(token);
        redis.execute(REVOKE, List.of(recordKey(digest), sessionKey(digest), userKey(digest)),
                prefix + "session:", prefix + "user:", digest);
    }

    @Override
    public void revokeSession(String sessionId) {
        requireText(sessionId, "sessionId");
        redis.execute(REVOKE_SESSION, List.of(sessionTokensKey(sessionId), sessionTouchKey(sessionId)),
                prefix + "token:", prefix + "user:");
    }

    @Override
    public void revokeUser(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        redis.execute(REVOKE_USER, List.of(userTokensKey(userId)), prefix + "token:", prefix + "session:");
    }

    @Override
    public void refreshTtl(String token, Duration ttl) {
        String digest = digest(token);
        redis.execute(REFRESH, List.of(recordKey(digest), sessionKey(digest), userKey(digest)),
                prefix + "session:", prefix + "user:", Long.toString(requirePositiveTtl(ttl)));
    }

    @Override
    public boolean acquireSessionTouchLease(String sessionId, Duration interval) {
        requireText(sessionId, "sessionId");
        Boolean acquired = redis.opsForValue().setIfAbsent(sessionTouchKey(sessionId), "1",
                Duration.ofMillis(requirePositiveTtl(interval)));
        return Boolean.TRUE.equals(acquired);
    }

    public Optional<String> sessionId(String token) {
        return Optional.ofNullable(redis.opsForValue().get(sessionKey(digest(token))));
    }

    public Set<String> tokenDigestsForSession(String sessionId) {
        requireText(sessionId, "sessionId");
        Set<String> values = redis.opsForSet().members(sessionTokensKey(sessionId));
        return values == null ? Set.of() : Set.copyOf(values);
    }

    public Set<String> tokenDigestsForUser(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        Set<String> values = redis.opsForSet().members(userTokensKey(userId));
        return values == null ? Set.of() : Set.copyOf(values);
    }

    public boolean hasTokenForSession(String sessionId, String token) {
        return tokenDigestsForSession(sessionId).contains(digest(token));
    }

    public boolean hasTokenForUser(long userId, String token) {
        return tokenDigestsForUser(userId).contains(digest(token));
    }

    private String recordKey(String digest) { return prefix + "token:" + digest + ":record"; }
    private String sessionKey(String digest) { return prefix + "token:" + digest + ":session"; }
    private String userKey(String digest) { return prefix + "token:" + digest + ":user"; }
    private String sessionTokensKey(String sessionId) { return prefix + "session:" + sessionId + ":tokens"; }
    private String sessionTouchKey(String sessionId) { return prefix + "session:" + sessionId + ":touch"; }
    private String userTokensKey(long userId) { return prefix + "user:" + userId + ":tokens"; }

    private static long requirePositiveTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("ttl must be positive");
        return ttl.toMillis();
    }

    private static String digest(String token) {
        requireText(token, "token");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    private static DefaultRedisScript<Long> script(String source) {
        return new DefaultRedisScript<>(source, Long.class);
    }
}
