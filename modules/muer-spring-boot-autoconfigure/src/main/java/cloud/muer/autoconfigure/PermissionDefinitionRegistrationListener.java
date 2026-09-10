package cloud.muer.autoconfigure;

import cloud.muer.authorization.PermissionDefinitionProvider;
import cloud.muer.authorization.PermissionRegistrationService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Objects;

/** Registers application permission definitions after schema initialization and context readiness. */
final class PermissionDefinitionRegistrationListener {
    private final PermissionRegistrationService registration;
    private final List<PermissionDefinitionProvider> providers;

    PermissionDefinitionRegistrationListener(PermissionRegistrationService registration,
                                             List<PermissionDefinitionProvider> providers) {
        this.registration = Objects.requireNonNull(registration, "registration must not be null");
        this.providers = List.copyOf(providers);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    public void onApplicationReady() {
        registration.register(providers);
    }
}
