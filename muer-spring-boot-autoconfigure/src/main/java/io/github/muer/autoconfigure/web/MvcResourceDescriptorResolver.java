package io.github.iamstarter.autoconfigure.web;

import io.github.iamstarter.core.model.ResourceDescriptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;

import java.util.Optional;

/** Resolves a host-owned resource descriptor for an annotated MVC handler. */
@FunctionalInterface
public interface MvcResourceDescriptorResolver {
    Optional<ResourceDescriptor> resolve(HttpServletRequest request, HandlerMethod handlerMethod);
}
