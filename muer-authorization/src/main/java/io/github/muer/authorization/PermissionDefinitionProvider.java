package io.github.muer.authorization;

import java.util.Collection;

/** Supplies the permissions formally declared by an integrating application. */
@FunctionalInterface
public interface PermissionDefinitionProvider {
    Collection<PermissionDefinition> getPermissionDefinitions();
}
