package io.github.iamstarter;

import io.github.iamstarter.core.model.IamUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchitectureIsolationTest {
    @Test
    void framework_exposes_a_generic_user_subject() {
        IamUser user = new IamUser(7L, "alex", "HUMAN", true, 3L);
        assertEquals("alex", user.username());
    }
}
