package io.github.muer.autoconfigure.web;

import io.github.muer.core.model.ScopeAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirePermissionTest {
    @Test
    void declares_permission_and_scope_access_for_a_handler() throws NoSuchMethodException {
        Method method = ExampleHandler.class.getDeclaredMethod("update");
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        assertTrue(method.isAnnotationPresent(RequirePermission.class));
        assertEquals("document:update", annotation.value());
        assertEquals(ScopeAccess.WRITE, annotation.access());
        assertEquals(ScopeAccess.READ, RequirePermission.class.getMethod("access").getDefaultValue());
        assertEquals(Optional.class, MvcResourceDescriptorResolver.class
                .getMethod("resolve", HttpServletRequest.class, HandlerMethod.class).getReturnType());
    }

    static final class ExampleHandler {
        @RequirePermission(value = "document:update", access = ScopeAccess.WRITE)
        void update() { }
    }
}
