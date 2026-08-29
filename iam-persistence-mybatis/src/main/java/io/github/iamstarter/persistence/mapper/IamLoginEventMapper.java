package io.github.iamstarter.persistence.mapper;

import io.github.iamstarter.session.LoginEvent;

public interface IamLoginEventMapper {
    int insert(LoginEvent event);
}
