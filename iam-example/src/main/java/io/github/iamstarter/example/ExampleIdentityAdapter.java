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
            if (!"demo".equals(request.username()) || !"demo-pass".equals(request.password())
                    || !"WEB".equals(request.clientType())) {
                return Optional.empty();
            }
            return Optional.of(new IamPrincipal(
                    101L, "example-user", "EXAMPLE", 401L, 301L, request.clientType(), 1L));
        };
    }
}
