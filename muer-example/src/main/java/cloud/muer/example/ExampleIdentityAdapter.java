package cloud.muer.example;

import cloud.muer.authentication.IdentityAuthenticator;
import cloud.muer.core.model.IamPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Configuration(proxyBeanMethods = false)
public class ExampleIdentityAdapter {
    private static Map<String, AppUser> baseUsers() {
        return Map.of(
                "alice", new AppUser(101L, "alice", "demo-pass", true, "Alice"),
                "author-a", new AppUser(101L, "author-a", "demo-pass", true, "Author A"),
                "reader-b", new AppUser(102L, "reader-b", "demo-pass", true, "Reader B"),
                "disabled-c", new AppUser(103L, "disabled-c", "demo-pass", false, "Disabled C"),
                "operator-a", new AppUser(101L, "operator-a", "demo-pass", true, "Operator A"),
                "operator-b", new AppUser(102L, "operator-b", "demo-pass", true, "Operator B"));
    }

    /** Plain entry point used by unit-style tests when no admin demo account exists. */
    public IdentityAuthenticator exampleIdentityAuthenticator() {
        return exampleIdentityAuthenticator(Optional.empty());
    }

    @Bean
    IdentityAuthenticator exampleIdentityAuthenticator(Optional<AdminDemoAccount> adminDemo) {
        var users = new HashMap<>(baseUsers());
        adminDemo.ifPresent(account -> users.put(account.user().username(), account.user()));
        return request -> {
            var user = users.get(request.username());
            if (user == null || !user.enabled() || !user.password().equals(request.password())) {
                return Optional.empty();
            }
            if (adminDemo.isPresent() && adminDemo.get().user().username().equals(request.username())) {
                return Optional.of(adminDemo.get().principal(request.clientType()));
            }
            var profileId = user.id() == 101L ? 401L : 403L;
            boolean independentConsumer = "author-a".equals(user.username());
            var templateVersionId = independentConsumer ? 302L : 301L;
            var identityId = independentConsumer ? "app-user-" + user.id() : "identity-" + user.username();
            return Optional.of(new IamPrincipal(user.id(), identityId,
                    "EXAMPLE", profileId, templateVersionId, request.clientType(), 1L));
        };
    }
}
