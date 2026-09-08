package com.example.muerquickstart.security;

import cloud.muer.authorization.PermissionDefinition;
import cloud.muer.authorization.PermissionDefinitionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * The developer's contract for "which capabilities does this system have?".
 *
 * <p>Permissions are declared here in code. They are NOT assigned to anyone
 * here: a separate administrative layer decides which Permission Template /
 * Profile / Scope a user gets, by referencing these codes. In the tutorial the
 * seeding happens in {@code QuickStartAuthorizationSeeder} (dev only); in a
 * managed environment an administrator composes them in the Admin Console.</p>
 */
@Configuration(proxyBeanMethods = false)
public class MuerPermissionConfiguration {

    @Bean
    PermissionDefinitionProvider documentPermissions() {
        return () -> List.of(
                new PermissionDefinition("document:read", "Read a document", "Read a document inside a project"),
                new PermissionDefinition("document:update", "Update a document", "Update a document inside a project"));
    }
}
