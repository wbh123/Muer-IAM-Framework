package com.example.muerquickstart.account;

/**
 * The host-owned in-memory account projection used by this quick-start.
 *
 * <p>In a real application this row would be replaced by your own user table /
 * LDAP / enterprise user centre. The fields below are the ones the
 * {@code DemoIdentityAuthenticator} needs to build a Muer {@code IamPrincipal}.
 * They mirror the seed data the quick-start authorization seeder creates, so
 * the two always stay in sync.</p>
 *
 * @param id                   host user id (the same value used as the Muer {@code userId})
 * @param username             login name
 * @param password             plain credential used only for the local demo
 * @param identityDomain       identity domain, e.g. {@code EXAMPLE}
 * @param activeProfileId      the Muer Profile id that is active when this user logs in
 * @param templateVersionId    the Permission Template Version id behind that Profile
 * @param authorizationVersion the user's authorization version (bumped on change)
 */
public record DemoAccount(
        long id,
        String username,
        String password,
        String identityDomain,
        long activeProfileId,
        long templateVersionId,
        long authorizationVersion) {
}
