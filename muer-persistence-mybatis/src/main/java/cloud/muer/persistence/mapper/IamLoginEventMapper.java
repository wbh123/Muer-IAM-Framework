package cloud.muer.persistence.mapper;

import cloud.muer.session.LoginEvent;

public interface IamLoginEventMapper {
    int insert(LoginEvent event);
}
