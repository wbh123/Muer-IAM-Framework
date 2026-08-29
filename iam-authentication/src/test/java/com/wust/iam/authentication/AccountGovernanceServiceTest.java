package com.wust.iam.authentication;

import com.wust.iam.authorization.AuthorizationVersionRepository;
import com.wust.iam.authorization.AuthorizationVersionService;
import com.wust.iam.core.model.IamUser;
import com.wust.iam.core.model.Identity;
import com.wust.iam.core.port.IamUserRepository;
import com.wust.iam.core.port.IdentityRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountGovernanceServiceTest {
    @Test
    void identity_change_increments_the_owning_users_authorization_version() {
        var users = new Users(new IamUser(7L, "alex", "MEMBER", true, 4L));
        var identities = new Identities();
        var versions = new Versions(4L);
        var service = new AccountGovernanceService(users, identities, new AuthorizationVersionService(versions));

        service.saveIdentity(new Identity("identity-7", 7L, "alex@example.test", "ACCOUNT", true));

        assertEquals(5L, versions.currentVersion(7L));
        assertEquals("ACCOUNT", identities.require("identity-7").domain());
    }

    @Test
    void existing_identity_cannot_be_transferred_to_another_user() {
        var users = new Users(
                new IamUser(7L, "alex", "MEMBER", true, 4L),
                new IamUser(8L, "casey", "MEMBER", true, 2L));
        var identities = new Identities(
                new Identity("identity-7", 7L, "alex@example.test", "ACCOUNT", true));
        var service = new AccountGovernanceService(users, identities,
                new AuthorizationVersionService(new Versions(4L)));

        assertThrows(IllegalArgumentException.class, () -> service.saveIdentity(
                new Identity("identity-7", 8L, "alex@example.test", "ACCOUNT", true)));
    }

    @Test
    void user_listing_is_cursor_paginated_and_bounded() {
        var users = new Users(
                new IamUser(1L, "one", "MEMBER", true, 1L),
                new IamUser(2L, "two", "MEMBER", true, 1L),
                new IamUser(3L, "three", "MEMBER", true, 1L));
        var service = new AccountGovernanceService(users, new Identities(),
                new AuthorizationVersionService(new Versions(1L)));

        assertEquals(List.of(2L, 3L), service.listUsers(1L, 50).stream().map(IamUser::userId).toList());
        assertThrows(IllegalArgumentException.class, () -> service.listUsers(0L, 101));
    }

    private static final class Users implements IamUserRepository {
        private final List<IamUser> values = new ArrayList<>();
        private Users(IamUser... initial) { values.addAll(List.of(initial)); }
        public Optional<IamUser> findById(long userId) {
            return values.stream().filter(value -> value.userId() == userId).findFirst();
        }
        public List<IamUser> findPage(long afterUserId, int limit) {
            return values.stream().filter(value -> value.userId() > afterUserId)
                    .sorted(Comparator.comparingLong(IamUser::userId)).limit(limit).toList();
        }
        public void save(IamUser user) {
            values.removeIf(value -> value.userId() == user.userId());
            values.add(user);
        }
    }

    private static final class Identities implements IdentityRepository {
        private final List<Identity> values = new ArrayList<>();
        private Identities(Identity... initial) { values.addAll(List.of(initial)); }
        public Optional<Identity> findById(String identityId) {
            return values.stream().filter(value -> value.identityId().equals(identityId)).findFirst();
        }
        public List<Identity> findByUserId(long userId) {
            return values.stream().filter(value -> value.userId() == userId).toList();
        }
        public void save(Identity identity) {
            values.removeIf(value -> value.identityId().equals(identity.identityId()));
            values.add(identity);
        }
    }

    private static final class Versions implements AuthorizationVersionRepository {
        private long value;
        private Versions(long value) { this.value = value; }
        public long currentVersion(long userId) { return value; }
        public long increment(long userId) { return ++value; }
    }
}
