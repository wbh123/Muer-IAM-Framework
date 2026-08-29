package io.github.iamstarter.diagnostics;

import io.github.iamstarter.authorization.AuthorizationRequest;
import io.github.iamstarter.authorization.DefaultAuthorizationEngine;
import io.github.iamstarter.core.model.IamPrincipal;
import io.github.iamstarter.core.model.ResourceDescriptor;
import io.github.iamstarter.core.model.ResourceScope;
import io.github.iamstarter.core.model.ScopeAccess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorizationDiagnosticsServiceTest {
    @Test
    void diagnostics_projects_the_exact_runtime_decision() {
        var engine = new DefaultAuthorizationEngine((resource, scope) -> true,
                principal -> Set.of("order.read"),
                principal -> List.of(new ResourceScope("DEPARTMENT", "7", ScopeAccess.READ)));
        var principal = new IamPrincipal(7L, "id-7", "BUSINESS", 19L, 3L, "WEB", 2L);
        var request = new AuthorizationRequest("order.read", "BUSINESS", "WEB",
                new ResourceDescriptor("ORDER", "o-1", List.of(), Map.of()), ScopeAccess.READ);
        var runtime = engine.decide(principal, request);

        var diagnosis = new AuthorizationDiagnosticsService(engine).evaluate(principal, request);

        assertEquals(runtime.decisionCode(), diagnosis.decisionCode());
        assertEquals(runtime.steps(), diagnosis.steps());
    }
}
