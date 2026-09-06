package io.github.muer.example;

import io.github.muer.authorization.PermissionDefinition;
import io.github.muer.authorization.PermissionDefinitionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class DocumentPermissionConfiguration {
    @Bean
    PermissionDefinitionProvider documentPermissions() {
        return () -> List.of(
                new PermissionDefinition("document:read", "Read document", "Read a document"),
                new PermissionDefinition("document:update", "Update document", "Update a document"));
    }
}
