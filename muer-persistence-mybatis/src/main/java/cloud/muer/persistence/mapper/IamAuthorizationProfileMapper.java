package cloud.muer.persistence.mapper;

import cloud.muer.authorization.AuthorizationProfile;
import cloud.muer.core.model.ResourceScope;
import cloud.muer.persistence.AuthorizationProfileRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IamAuthorizationProfileMapper {
    AuthorizationProfileRow findProfile(@Param("profileId") long profileId);
    List<AuthorizationProfileRow> findProfilesByUserId(@Param("userId") long userId);
    List<AuthorizationProfileRow> search(@Param("afterProfileId") long afterProfileId,
                                         @Param("limit") int limit,
                                         @Param("userId") Long userId,
                                         @Param("templateVersionId") Long templateVersionId,
                                         @Param("enabled") Boolean enabled,
                                         @Param("revoked") Boolean revoked,
                                         @Param("clientType") String clientType);
    List<ResourceScope> findScopes(@Param("profileId") long profileId);
    int exists(@Param("profileId") long profileId);
    int insert(@Param("profile") AuthorizationProfile profile, @Param("clientTypesJson") String clientTypesJson);
    int insertGenerated(@Param("profile") AuthorizationProfile profile, @Param("clientTypesJson") String clientTypesJson);
    long lastInsertId();
    int update(@Param("profile") AuthorizationProfile profile, @Param("clientTypesJson") String clientTypesJson);
    int deleteScopes(@Param("profileId") long profileId);
    int insertScope(@Param("profileId") long profileId, @Param("scope") ResourceScope scope);
}
