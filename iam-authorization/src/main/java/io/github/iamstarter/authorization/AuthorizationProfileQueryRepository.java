package io.github.iamstarter.authorization;

import java.util.List;

/**
 * Read-side query surface used by the IAM administration console.
 *
 * <p>Filters are optional: a {@code null} value disables that predicate. Ordering is
 * by ascending profile id strictly after {@code afterProfileId}.
 */
public interface AuthorizationProfileQueryRepository {

    /**
     * Cursor-paginated profile search.
     *
     * @param afterProfileId cursor of the last visible profile (exclusive)
     * @param limit maximum number of rows, between 1 and 100
     * @param userId restrict to the profiles of a user
     * @param templateVersionId restrict to profiles bound to a template version
     * @param enabled restrict to profiles with the given enabled flag
     * @param revoked restrict to profiles with the given revocation state
     * @param clientType restrict to profiles that allow the given client type
     */
    List<AuthorizationProfile> search(long afterProfileId, int limit, Long userId, Long templateVersionId,
                                      Boolean enabled, Boolean revoked, String clientType);
}
