package cloud.muer.example;

public record AppUser(long id, String username, String password, boolean enabled, String displayName) {
}
