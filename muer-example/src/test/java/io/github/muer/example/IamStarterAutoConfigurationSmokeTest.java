package io.github.iamstarter.example;

import io.github.iamstarter.authentication.AuthenticationService;
import io.github.iamstarter.authentication.LoginRequest;
import io.github.iamstarter.authorization.AuthorizationEngine;
import io.github.iamstarter.authorization.AuthorizationProfileRepository;
import io.github.iamstarter.authorization.AuthorizationVersionRepository;
import io.github.iamstarter.authorization.PermissionTemplateVersionRepository;
import io.github.iamstarter.core.port.ResourceHierarchyProvider;
import io.github.iamstarter.session.SessionRepository;
import io.github.iamstarter.session.TokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@SpringBootTest(
        classes = IamStarterAutoConfigurationSmokeTest.ConsumerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "iam.schema.enabled=false")
class IamStarterAutoConfigurationSmokeTest {
    @Test
    void operator_a_demo_credentials_resolve_to_the_reader_501_principal() {
        var authenticator = new ExampleIdentityAdapter().exampleIdentityAuthenticator();

        var principal = authenticator.authenticate(new LoginRequest("operator-a", "demo-pass", "WEB"));

        assertEquals(101L, principal.orElseThrow().userId());
        assertEquals("identity-operator-a", principal.orElseThrow().identityId());
        assertEquals(401L, principal.orElseThrow().activeProfileId());
        assertEquals(301L, principal.orElseThrow().templateVersionId());
    }

    @Test
    void operator_b_demo_credentials_resolve_to_the_reader_502_principal() {
        var authenticator = new ExampleIdentityAdapter().exampleIdentityAuthenticator();

        var principal = authenticator.authenticate(new LoginRequest("operator-b", "demo-pass", "WEB"));

        assertEquals(102L, principal.orElseThrow().userId());
        assertEquals("identity-operator-b", principal.orElseThrow().identityId());
        assertEquals(403L, principal.orElseThrow().activeProfileId());
        assertEquals(301L, principal.orElseThrow().templateVersionId());
    }

    @Test
    void starter_dependency_discovers_and_configures_the_iam_runtime(ApplicationContext context) {
        assertNotNull(context.getBean(AuthenticationService.class));
        assertNotNull(context.getBean(AuthorizationEngine.class));
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataRedisAutoConfiguration.class,
            DataRedisRepositoriesAutoConfiguration.class
    })
    static class ConsumerApplication {
        @Bean
        TokenStore tokenStore() {
            return mock(TokenStore.class);
        }

        @Bean
        SessionRepository sessionRepository() {
            return mock(SessionRepository.class);
        }

        @Bean
        AuthorizationVersionRepository authorizationVersionRepository() {
            return mock(AuthorizationVersionRepository.class);
        }

        @Bean
        AuthorizationProfileRepository authorizationProfileRepository() {
            return mock(AuthorizationProfileRepository.class);
        }

        @Bean
        PermissionTemplateVersionRepository permissionTemplateVersionRepository() {
            return mock(PermissionTemplateVersionRepository.class);
        }

        @Bean
        ResourceHierarchyProvider resourceHierarchyProvider() {
            return (resource, scope) -> false;
        }
    }
}
