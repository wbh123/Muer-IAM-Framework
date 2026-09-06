package io.github.muer.persistence.mapper;

import io.github.muer.core.model.Identity;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

public interface IamIdentityMapper {
    Optional<Identity> findById(@Param("identityId") String identityId);
    List<Identity> findByUserId(@Param("userId") long userId);
    int upsert(Identity identity);
}
