package io.github.iamstarter.example;

import io.github.iamstarter.authentication.IdentityAuthenticator;
import io.github.iamstarter.core.model.IamPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class ExampleIdentityAdapter {
    private static final Map<String, AppUser> USERS = Map.of(
            "author-a", new AppUser(101L, "author-a", "demo-pass", true, "Author A"),
            "reader-b", new AppUser(102L, "reader-b", "demo-pass", true, "Reader B"),
            "disabled-c", new AppUser(103L, "disabled-c", "demo-pass", false, "Disabled C"));

    @Bean
    IdentityAuthenticator exampleIdentityAuthenticator() {
        return request -> {
            var user = USERS.get(request.username());
            if (user == null || !user.enabled() || !user.password().equals(request.password())) {
                return Optional.empty();
            }
            var profileId = user.id() == 101L ? 401L : 403L;
            return Optional.of(new IamPrincipal(user.id(), "app-user-" + user.id(),
                    "EXAMPLE", profileId, 301L, request.clientType(), 1L));
        };
    }
}
