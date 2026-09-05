package io.github.iamstarter.persistence.mapper;

import io.github.iamstarter.authorization.AuthorizationProfile;
import io.github.iamstarter.core.model.ResourceScope;
import io.github.iamstarter.persistence.AuthorizationProfileRow;
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
    int update(@Param("profile") AuthorizationProfile profile, @Param("clientTypesJson") String clientTypesJson);
    int deleteScopes(@Param("profileId") long profileId);
    int insertScope(@Param("profileId") long profileId, @Param("scope") ResourceScope scope);
}
