package io.github.muer.persistence.mapper;

import io.github.muer.session.LoginEvent;

public interface IamLoginEventMapper {
    int insert(LoginEvent event);
}
