package cloud.muer.persistence;

import cloud.muer.authorization.AuthorizationProfile;
import cloud.muer.authorization.AuthorizationProfileQueryRepository;
import cloud.muer.persistence.mapper.IamAuthorizationProfileMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;
import java.util.Objects;

public final class MyBatisAuthorizationProfileQueryRepository implements AuthorizationProfileQueryRepository {
    private final SqlSessionFactory sessions;
    private final StringSetJsonCodec clientTypes = new StringSetJsonCodec();

    public MyBatisAuthorizationProfileQueryRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public List<AuthorizationProfile> search(long afterProfileId, int limit, Long userId,
                                             Long templateVersionId, Boolean enabled,
                                             Boolean revoked, String clientType) {
        if (afterProfileId < 0) throw new IllegalArgumentException("afterProfileId must not be negative");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        try (var session = sessions.openSession()) {
            var mapper = session.getMapper(IamAuthorizationProfileMapper.class);
            return mapper.search(afterProfileId, limit, userId, templateVersionId, enabled,
                            revoked, blankToNull(clientType)).stream()
                    .map(row -> toProfile(mapper, row))
                    .toList();
        }
    }

    private AuthorizationProfile toProfile(IamAuthorizationProfileMapper mapper, AuthorizationProfileRow row) {
        return new AuthorizationProfile(row.profileId(), row.userId(), row.profileName(), row.templateVersionId(),
                clientTypes.decode(row.clientTypesJson()), row.enabled(), row.revokedAt() != null,
                row.validFrom(), row.validUntil(), mapper.findScopes(row.profileId()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
