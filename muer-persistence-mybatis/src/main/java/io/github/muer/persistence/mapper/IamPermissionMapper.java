package io.github.muer.persistence.mapper;

import io.github.muer.authorization.PermissionDefinition;
import org.apache.ibatis.annotations.Param;

public interface IamPermissionMapper {
    int upsert(@Param("definition") PermissionDefinition definition);
}
