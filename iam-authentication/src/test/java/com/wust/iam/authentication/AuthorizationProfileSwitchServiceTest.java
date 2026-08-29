package com.wust.iam.authentication;

import com.wust.iam.authorization.AuthorizationProfile;
import com.wust.iam.authorization.AuthorizationProfileRepository;
import com.wust.iam.authorization.AuthorizationProfileService;
import com.wust.iam.authorization.AuthorizationVersionRepository;
import com.wust.iam.authorization.AuthorizationVersionService;
import com.wust.iam.core.model.IamPrincipal;
import com.wust.iam.session.TokenRecord;
import com.wust.iam.session.TokenStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationProfileSwitchServiceTest {
    @Test
    void switching_to_an_available_profile_issues_a_token_with_one_new_active_profile() {
        var target = new AuthorizationProfile(31L, 7L, "Operator", 9L, Set.of("WEB"), true, false,
                null, null, List.of());
        var tokens = new CapturingTokens();
        var authentication = authentication(tokens);
        var service = new AuthorizationProfileSwitchService(profileService(List.of(target)), authentication);
        var current = new IamPrincipal(7L, "identity-7", "SECURITY", 19L, 3L, "WEB", 4L);

        var result = service.switchTo(current, 31L).orElseThrow();

        assertEquals("switched-token", result.accessToken());
        assertEquals(31L, result.principal().activeProfileId());
        assertEquals(9L, result.principal().templateVersionId());
        assertEquals(5L, result.principal().authorizationVersion());
        assertEquals(result.principal(), tokens.record.principal());
    }

    @Test
    void switching_rejects_a_profile_not_available_to_the_current_principal() {
        var anotherUsersProfile = new AuthorizationProfile(32L, 8L, "Other", 9L, Set.of("WEB"), true, false,
                null, null, List.of());
        var service = new AuthorizationProfileSwitchService(
                profileService(List.of(anotherUsersProfile)), authentication(new CapturingTokens()));
        var current = new IamPrincipal(7L, "identity-7", "SECURITY", 19L, 3L, "WEB", 4L);

        assertTrue(service.switchTo(current, 32L).isEmpty());
    }

    private static AuthenticationService authentication(CapturingTokens tokens) {
        return new AuthenticationService(tokens, userId -> 5L, request -> Optional.empty(),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC), Duration.ofHours(8),
                () -> "switched-token", () -> "switched-session");
    }

    private static AuthorizationProfileService profileService(List<AuthorizationProfile> profiles) {
        AuthorizationProfileRepository repository = new AuthorizationProfileRepository() {
            public AuthorizationProfile require(long profileId) { return profiles.getFirst(); }
            public List<AuthorizationProfile> findByUserId(long userId) {
                return profiles.stream().filter(profile -> profile.userId() == userId).toList();
            }
            public void save(AuthorizationProfile profile) { }
        };
        AuthorizationVersionRepository versions = new AuthorizationVersionRepository() {
            public long currentVersion(long userId) { return 5L; }
            public long increment(long userId) { return 6L; }
        };
        return new AuthorizationProfileService(repository, new AuthorizationVersionService(versions),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC));
    }

    private static final class CapturingTokens implements TokenStore {
        private TokenRecord record;
        public Optional<TokenRecord> resolve(String token) { return Optional.ofNullable(record); }
        public void save(String token, TokenRecord record, Duration ttl) { this.record = record; }
        public void revoke(String token) { }
        public void revokeSession(String sessionId) { }
        public void revokeUser(long userId) { }
        public void refreshTtl(String token, Duration ttl) { }
    }
}
