package cloud.muer.authorization;

/** Internal persistence port for registered permission definitions. */
public interface PermissionRepository {
    void upsert(PermissionDefinition definition);
}
