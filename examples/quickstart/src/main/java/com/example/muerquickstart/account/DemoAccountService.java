package com.example.muerquickstart.account;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Host-owned account directory for the quick-start. Kept intentionally as a
 * small in-memory {@code Map} so the tutorial teaches Muer, not user-system
 * design. Swap this class for your real user service and nothing else in the
 * Muer integration changes.
 */
@Service
public class DemoAccountService {

    /**
     * Seed values must match {@code QuickStartAuthorizationSeeder}:
     * alice (user 101) logs in with the Reader projection (Profile 401,
     * Template Version 301). The editor switch to Profile 402 happens through
     * the Profile Switch API, not through a second login.
     */
    private static final Map<String, DemoAccount> ACCOUNTS = Map.of(
            "alice", new DemoAccount(101L, "alice", "demo-pass",
                    "EXAMPLE", 401L, 301L, 1L));

    /** Find an account by username, or empty when it does not exist. */
    public Optional<DemoAccount> findByUsername(String username) {
        return Optional.ofNullable(ACCOUNTS.get(username));
    }
}
