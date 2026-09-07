package io.github.muer.persistence;

import java.time.Instant;

public record AuthorizationProfileRow(long profileId, long userId, String profileName, long templateVersionId,
                                      String clientTypesJson, boolean enabled, Instant revokedAt,
                                      Instant validFrom, Instant validUntil) {
}
