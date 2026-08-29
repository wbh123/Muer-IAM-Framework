package com.wust.iam.session;

import java.time.Duration;
import java.util.Optional;

public interface TokenStore {
    void save(String token, TokenRecord record, Duration ttl);
    Optional<TokenRecord> resolve(String token);
    void revoke(String token);
    void revokeSession(String sessionId);
    void revokeUser(long userId);
    void refreshTtl(String token, Duration ttl);

    default boolean acquireSessionTouchLease(String sessionId, Duration interval) {
        return false;
    }
}
