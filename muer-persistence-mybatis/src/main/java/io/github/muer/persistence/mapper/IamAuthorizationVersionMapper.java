package io.github.iamstarter.persistence.mapper;

import org.apache.ibatis.annotations.Param;

public interface IamAuthorizationVersionMapper {
    Long findVersion(@Param("userId") long userId);

    int increment(@Param("userId") long userId);
}
