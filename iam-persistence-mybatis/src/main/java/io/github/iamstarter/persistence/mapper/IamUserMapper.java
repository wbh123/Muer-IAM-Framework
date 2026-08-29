package io.github.iamstarter.persistence.mapper;

import io.github.iamstarter.core.model.IamUser;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

public interface IamUserMapper {
    Optional<IamUser> findById(@Param("userId") long userId);
    List<IamUser> findPage(@Param("afterUserId") long afterUserId, @Param("limit") int limit);
    int upsert(IamUser user);
}
