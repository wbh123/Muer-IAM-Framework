package cloud.muer.authorization;

import java.util.List;

public interface AuthorizationProfileRepository {
    AuthorizationProfile require(long profileId);
    List<AuthorizationProfile> findByUserId(long userId);
    void save(AuthorizationProfile profile);
}
