package io.github.iamstarter.example;

import io.github.iamstarter.authentication.IdentityAuthenticator;
import io.github.iamstarter.core.model.IamPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration(proxyBeanMethods = false)
public class ExampleIdentityAdapter {
    @Bean
    IdentityAuthenticator exampleIdentityAuthenticator() {
        return request -> {
            if (!"demo-pass".equals(request.password())) {
                return Optional.empty();
            }
            return switch (request.username()) {
                case "operator-a" -> Optional.of(new IamPrincipal(
                        101L, "identity-operator-a", "EXAMPLE", 401L, 301L, request.clientType(), 1L));
                case "operator-b" -> Optional.of(new IamPrincipal(
                        102L, "identity-operator-b", "EXAMPLE", 403L, 301L, request.clientType(), 1L));
                default -> Optional.empty();
            };
        };
    }
}
