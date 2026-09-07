package cloud.muer.persistence;

import cloud.muer.authorization.AuthorizationProfile;
import cloud.muer.authorization.AuthorizationProfileRepository;
import cloud.muer.persistence.mapper.IamAuthorizationProfileMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.List;

public final class MyBatisAuthorizationProfileRepository implements AuthorizationProfileRepository {
    private final SqlSessionFactory sessions;
    private final StringSetJsonCodec clientTypes = new StringSetJsonCodec();

    public MyBatisAuthorizationProfileRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public AuthorizationProfile require(long profileId) {
        try (var session = sessions.openSession()) {
            var mapper = session.getMapper(IamAuthorizationProfileMapper.class);
            var row = mapper.findProfile(profileId);
            if (row == null) throw new NoSuchElementException("authorization profile not found: " + profileId);
            return toProfile(mapper, row);
        }
    }

    @Override
    public List<AuthorizationProfile> findByUserId(long userId) {
        try (var session = sessions.openSession()) {
            var mapper = session.getMapper(IamAuthorizationProfileMapper.class);
            return mapper.findProfilesByUserId(userId).stream()
                    .map(row -> toProfile(mapper, row))
                    .toList();
        }
    }

    @Override
    public void save(AuthorizationProfile profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        try (var session = sessions.openSession(false)) {
            var mapper = session.getMapper(IamAuthorizationProfileMapper.class);
            String encodedClients = clientTypes.encode(profile.clientTypes());
            if (mapper.exists(profile.profileId()) == 0) mapper.insert(profile, encodedClients);
            else mapper.update(profile, encodedClients);
            mapper.deleteScopes(profile.profileId());
            profile.scopes().forEach(scope -> mapper.insertScope(profile.profileId(), scope));
            session.commit();
        }
    }

    private AuthorizationProfile toProfile(IamAuthorizationProfileMapper mapper, AuthorizationProfileRow row) {
        return new AuthorizationProfile(row.profileId(), row.userId(), row.profileName(), row.templateVersionId(),
                clientTypes.decode(row.clientTypesJson()), row.enabled(), row.revokedAt() != null,
                row.validFrom(), row.validUntil(), mapper.findScopes(row.profileId()));
    }
}
