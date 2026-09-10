package cloud.muer.acceptance;

import cloud.muer.authorization.PermissionDefinition;
import cloud.muer.authorization.PermissionDefinitionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class AcceptancePermissionConfiguration {
    @Bean
    PermissionDefinitionProvider acceptancePermissions() {
        return () -> List.of(new PermissionDefinition(
                "acceptance:read", "Acceptance read", "Read an acceptance resource"));
    }
}
