package cloud.muer.persistence.mapper;

import cloud.muer.authorization.PermissionDefinition;
import org.apache.ibatis.annotations.Param;

public interface IamPermissionMapper {
    int upsert(@Param("definition") PermissionDefinition definition);
}
