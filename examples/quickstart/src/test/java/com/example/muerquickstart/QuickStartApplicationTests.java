package com.example.muerquickstart;

import cloud.muer.authentication.AuthenticationService;
import cloud.muer.authentication.IdentityAuthenticator;
import cloud.muer.authorization.AuthorizationEngine;
import cloud.muer.authorization.AuthorizationProfileRepository;
import cloud.muer.authorization.AuthorizationVersionRepository;
import cloud.muer.authorization.PermissionDefinitionProvider;
import cloud.muer.authorization.PermissionTemplateVersionRepository;
import cloud.muer.core.port.ResourceHierarchyProvider;
import cloud.muer.session.SessionRepository;
import cloud.muer.session.TokenStore;
import com.example.muerquickstart.account.DemoAccountService;
import com.example.muerquickstart.account.DemoIdentityAuthenticator;
import com.example.muerquickstart.document.DocumentController;
import com.example.muerquickstart.document.DocumentResourceResolver;
import com.example.muerquickstart.document.DocumentService;
import com.example.muerquickstart.security.DocumentResourceHierarchyProvider;
import com.example.muerquickstart.security.MuerPermissionConfiguration;
import com.example.muerquickstart.security.QuickStartSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Smoke test: boots a Spring context with the Muer runtime but WITHOUT MySQL or
 * Redis (persistence repositories are mocked), and asserts the host SPI beans
 * and the protected controller are wired. This keeps {@code mvn test} free of
 * any Testcontainers / infrastructure, exactly as the Docs CI requires.
 */
@SpringBootTest(
        classes = QuickStartApplicationTests.ConsumerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "muer.schema.enabled=false")
class QuickStartApplicationTests {

    @Test
    void muer_runtime_and_host_spi_beans_are_wired(ApplicationContext context) {
        // Provided by the Starter.
        assertNotNull(context.getBean(AuthenticationService.class));
        assertNotNull(context.getBean(AuthorizationEngine.class));

        // Provided by the quick-start host (Public API only).
        assertNotNull(context.getBean(DemoAccountService.class));
        assertNotNull(context.getBean(IdentityAuthenticator.class));
        assertNotNull(context.getBean(PermissionDefinitionProvider.class));
        assertNotNull(context.getBean(ResourceHierarchyProvider.class));
        assertNotNull(context.getBean(DocumentResourceResolver.class));
        assertNotNull(context.getBean(DocumentController.class));
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataRedisAutoConfiguration.class,
            DataRedisRepositoriesAutoConfiguration.class
    })
    @Import({DemoIdentityAuthenticator.class, MuerPermissionConfiguration.class,
            QuickStartSecurityConfiguration.class})
    static class ConsumerApplication {
        @Bean
        DemoAccountService demoAccountService() {
            return new DemoAccountService();
        }

        @Bean
        DocumentService documentService() {
            return new DocumentService();
        }

        @Bean
        DocumentResourceResolver documentResourceResolver(DocumentService documents) {
            return new DocumentResourceResolver(documents);
        }

        @Bean
        DocumentResourceHierarchyProvider documentResourceHierarchyProvider() {
            return new DocumentResourceHierarchyProvider();
        }

        @Bean
        DocumentController documentController(DocumentService documents) {
            return new DocumentController(documents);
        }

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
    }
}
