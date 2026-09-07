package io.github.muer.autoconfigure.web;

import io.github.muer.authorization.PermissionRegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Advises when MVC annotations have no matching application permission definition. */
public final class RequirePermissionDefinitionWarningListener {
    private static final Logger log = LoggerFactory.getLogger(RequirePermissionDefinitionWarningListener.class);
    private final PermissionRegistrationService registration;
    private final ObjectProvider<RequestMappingHandlerMapping> mappings;

    RequirePermissionDefinitionWarningListener(PermissionRegistrationService registration) {
        this(registration, new ObjectProvider<>() {
            @Override
            public RequestMappingHandlerMapping getObject() {
                return null;
            }
        });
    }

    public RequirePermissionDefinitionWarningListener(PermissionRegistrationService registration,
                                                      ObjectProvider<RequestMappingHandlerMapping> mappings) {
        this.registration = registration;
        this.mappings = mappings;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void warnForMissingDefinitions() {
        var mapping = mappings.getIfAvailable();
        if (mapping == null) return;
        missingCodes(mapping.getHandlerMethods().values()).forEach(code ->
                log.warn("Muer permission '{}' is referenced but no PermissionDefinitionProvider registered it.", code));
    }

    Set<String> missingCodes(Collection<HandlerMethod> handlers) {
        Set<String> missing = new LinkedHashSet<>();
        Set<String> declared = registration.declaredCodes();
        for (HandlerMethod handler : handlers) {
            RequirePermission requirement = handler.getMethodAnnotation(RequirePermission.class);
            if (requirement == null) requirement = handler.getBeanType().getAnnotation(RequirePermission.class);
            if (requirement != null && !declared.contains(requirement.value())) missing.add(requirement.value());
        }
        return Set.copyOf(missing);
    }
}
