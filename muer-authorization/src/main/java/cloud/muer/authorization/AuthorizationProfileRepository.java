package cloud.muer.authorization;

import java.util.List;

public interface AuthorizationProfileRepository {
    AuthorizationProfile require(long profileId);
    List<AuthorizationProfile> findByUserId(long userId);
    void save(AuthorizationProfile profile);

    /** Creates a profile with a repository-assigned identifier. */
    default AuthorizationProfile create(AuthorizationProfile profile) {
        throw new UnsupportedOperationException("authorization profile creation is not configured");
    }
}
