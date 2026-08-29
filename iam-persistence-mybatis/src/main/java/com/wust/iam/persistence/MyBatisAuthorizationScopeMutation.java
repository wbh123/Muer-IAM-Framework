package com.wust.iam.persistence;

import com.wust.iam.authorization.AuthorizationScopeMutation;
import com.wust.iam.core.model.ResourceScope;
import com.wust.iam.persistence.mapper.IamAuthorizationProfileMapper;
import com.wust.iam.persistence.mapper.IamAuthorizationVersionMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class MyBatisAuthorizationScopeMutation implements AuthorizationScopeMutation {
    private final SqlSessionFactory sessions;

    public MyBatisAuthorizationScopeMutation(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public void replaceScopesAndIncrementVersion(long profileId, List<ResourceScope> scopes) {
        var replacements = List.copyOf(Objects.requireNonNull(scopes, "scopes must not be null"));
        try (var session = sessions.openSession(false)) {
            var profiles = session.getMapper(IamAuthorizationProfileMapper.class);
            var profile = profiles.findProfile(profileId);
            if (profile == null) {
                throw new NoSuchElementException("authorization profile not found: " + profileId);
            }
            profiles.deleteScopes(profileId);
            replacements.forEach(scope -> profiles.insertScope(profileId, scope));

            var versions = session.getMapper(IamAuthorizationVersionMapper.class);
            if (versions.increment(profile.userId()) != 1) {
                throw new NoSuchElementException("IAM user not found: " + profile.userId());
            }
            session.commit();
        }
    }
}
