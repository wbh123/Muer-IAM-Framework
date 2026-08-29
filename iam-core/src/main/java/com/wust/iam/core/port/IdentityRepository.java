package com.wust.iam.core.port;

import com.wust.iam.core.model.Identity;

import java.util.List;
import java.util.Optional;

public interface IdentityRepository {
    Optional<Identity> findById(String identityId);
    List<Identity> findByUserId(long userId);
    void save(Identity identity);

    default Identity require(String identityId) {
        return findById(identityId)
                .orElseThrow(() -> new IllegalArgumentException("identity not found: " + identityId));
    }
}
