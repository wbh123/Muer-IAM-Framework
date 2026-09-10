package cloud.muer.showcase;

public record AppUser(long id, String username, String password, boolean enabled, String displayName) {
}
