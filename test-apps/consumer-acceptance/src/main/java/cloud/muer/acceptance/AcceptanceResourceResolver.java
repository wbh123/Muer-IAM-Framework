package cloud.muer.acceptance;

import cloud.muer.autoconfigure.web.MvcResourceDescriptorResolver;
import cloud.muer.core.model.ResourceDescriptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AcceptanceResourceResolver implements MvcResourceDescriptorResolver {
    @Override
    public Optional<ResourceDescriptor> resolve(HttpServletRequest request, HandlerMethod handlerMethod) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(variables instanceof Map<?, ?> pathVariables)
                || !(pathVariables.get("id") instanceof String id)) {
            return Optional.empty();
        }
        return Optional.of(new ResourceDescriptor(
                "DOCUMENT", id, List.of("PROJECT:" + (id.equals("701") ? "701" : "702")), Map.of()));
    }
}
