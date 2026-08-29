package com.wust.iam.persistence.mapper;

import com.wust.iam.session.LoginEvent;

public interface IamLoginEventMapper {
    int insert(LoginEvent event);
}
