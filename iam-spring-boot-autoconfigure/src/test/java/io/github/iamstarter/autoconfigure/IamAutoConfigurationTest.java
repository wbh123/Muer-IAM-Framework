package io.github.iamstarter.autoconfigure;

import io.github.iamstarter.authorization.AuthorizationEngine;
import io.github.iamstarter.authorization.AuthorizationProfileRepository;
import io.github.iamstarter.authorization.AuthorizationVersionRepository;
import io.github.iamstarter.authorization.PermissionTemplateVersionRepository;
import io.github.iamstarter.authentication.AuthenticationService;
import io.github.iamstarter.authentication.IdentityAuthenticator;
import io.github.iamstarter.authentication.LoginRequest;
import io.github.iamstarter.core.model.IamPrincipal;
import io.github.iamstarter.core.port.ResourceHierarchyProvider;
import io.github.iamstarter.session.TokenStore;
import io.github.iamstarter.session.SessionRepository;
import io.github.iamstarter.web.IamAdministrationController;
import io.github.iamstarter.web.IamAuthenticationController;
import io.github.iamstarter.web.IamAuthorizationController;
import io.github.iamstarter.web.IamSessionController;
import io.github.iamstarter.persistence.MyBatisAuthorizationProfileRepository;
import io.github.iamstarter.persistence.MyBatisAuthorizationVersionRepository;
import io.github.iamstarter.persistence.MyBatisPermissionTemplateVersionRepository;
import io.github.iamstarter.persistence.MyBatisSessionRepository;
import io.github.iamstarter.persistence.RedisTokenStore;
import org.apache.ibatis.session.SqlSessionFactory;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IamAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AdapterConfiguration.class)
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(IamAutoConfiguration.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enabled_configuration_registers_engine_authentication_and_bearer_filter() {
        contextRunner.withPropertyValues(
                        "iam.enabled=true",
                        "iam.token.ttl=4h",
                        "iam.token.redis-prefix=custom-iam",
                        "iam.session.touch-interval=3m",
                        "iam.client-types=WEB,MOBILE")
                .run(context -> {
                    assertNotNull(context.getBean(AuthorizationEngine.class));
                    assertNotNull(context.getBean(IamBearerTokenFilter.class));
                    assertFalse(context.getBean("iamBearerTokenFilterRegistration",
                            FilterRegistrationBean.class).isEnabled());
                    assertNotNull(context.getBean(IamAuthenticationController.class));
                    assertNotNull(context.getBean(IamAuthorizationController.class));
                    assertNotNull(context.getBean(IamAdministrationController.class));
                    assertNotNull(context.getBean(IamSessionController.class));
                    var properties = context.getBean(IamProperties.class);
                    assertEquals(Duration.ofHours(4), properties.getToken().getTtl());
                    assertEquals("custom-iam", properties.getToken().getRedisPrefix());
                    assertEquals(Duration.ofMinutes(3), properties.getSession().getTouchInterval());
                    assertEquals(java.util.List.of("WEB", "MOBILE"), properties.getClientTypes());
                });
    }

    @Test
    void disabled_configuration_registers_no_runtime_components() {
        contextRunner.withPropertyValues("iam.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean("iamAuthorizationEngine"));
                    assertFalse(context.containsBean("iamBearerTokenFilter"));
                });
    }

    @Test
    void rejects_a_non_positive_token_ttl_during_startup() {
        contextRunner.withPropertyValues("iam.token.ttl=0s")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void rejects_an_empty_login_client_type_allow_list_during_startup() {
        contextRunner.withPropertyValues("iam.client-types=")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void configured_client_types_reject_a_login_before_the_host_authenticator_runs() {
        contextRunner.withUserConfiguration(PermissiveIdentityConfiguration.class)
                .withPropertyValues("iam.client-types=WEB")
                .run(context -> {
                    var authentication = context.getBean(AuthenticationService.class);

                    assertTrue(authentication.login(new LoginRequest(
                            "operator", "secret", "MOBILE")).isEmpty());
                    assertEquals(0, context.getBean(AtomicInteger.class).get());
                });
    }

    @Test
    void schema_migration_can_be_disabled_for_a_host_managed_database() {
        new ApplicationContextRunner()
                .withUserConfiguration(InfrastructureConfiguration.class)
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(IamAutoConfiguration.class))
                .withPropertyValues("iam.schema.enabled=false")
                .run(context -> assertFalse(context.containsBean("iamSchemaMigrator")));
    }

    @Test
    void infrastructure_beans_activate_default_persistence_adapters() {
        new ApplicationContextRunner()
                .withUserConfiguration(InfrastructureConfiguration.class)
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(IamAutoConfiguration.class))
                .withPropertyValues("iam.schema.enabled=false")
                .run(context -> {
                    assertEquals(MyBatisAuthorizationProfileRepository.class,
                            context.getBean(AuthorizationProfileRepository.class).getClass());
                    assertEquals(MyBatisAuthorizationVersionRepository.class,
                            context.getBean(AuthorizationVersionRepository.class).getClass());
                    assertEquals(MyBatisPermissionTemplateVersionRepository.class,
                            context.getBean(PermissionTemplateVersionRepository.class).getClass());
                    assertEquals(MyBatisSessionRepository.class,
                            context.getBean(SessionRepository.class).getClass());
                    assertEquals(RedisTokenStore.class, context.getBean(TokenStore.class).getClass());
                    assertNotNull(context.getBean(IamAuthenticationController.class));
                });
    }

    @Test
    void servlet_application_registers_a_dedicated_iam_security_chain() {
        new WebApplicationContextRunner()
                .withUserConfiguration(AdapterConfiguration.class)
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
                        IamAutoConfiguration.class))
                .run(context -> assertNotNull(context.getBean("iamSecurityFilterChain", SecurityFilterChain.class)));
    }

    @Test
    void bearer_principal_cannot_be_replaced_by_forged_user_parameter() throws Exception {
        var authentication = mock(AuthenticationService.class);
        var principal = new IamPrincipal(7L, "identity-7", "SECURITY", 31L, 9L, "WEB", 4L);
        when(authentication.resolve("valid-token")).thenReturn(Optional.of(principal));
        var request = new MockHttpServletRequest("GET", "/iam/sessions");
        request.addHeader("Authorization", "Bearer valid-token");
        request.setParameter("userId", "999");

        new IamBearerTokenFilter(authentication).doFilter(
                request, new MockHttpServletResponse(), new MockFilterChain());

        var authenticated = (IamPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(7L, authenticated.userId());
    }

    @Configuration(proxyBeanMethods = false)
    static class AdapterConfiguration {
        @Bean
        TokenStore tokenStore() {
            return mock(TokenStore.class);
        }

        @Bean
        SessionRepository sessionRepository() {
            return mock(SessionRepository.class);
        }

        @Bean
        AuthorizationVersionRepository authorizationVersions() {
            return mock(AuthorizationVersionRepository.class);
        }

        @Bean
        AuthorizationProfileRepository authorizationProfiles() {
            return mock(AuthorizationProfileRepository.class);
        }

        @Bean
        PermissionTemplateVersionRepository templateVersions() {
            return mock(PermissionTemplateVersionRepository.class);
        }

        @Bean
        ResourceHierarchyProvider hierarchyProvider() {
            return (resource, scope) -> false;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InfrastructureConfiguration {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class PermissiveIdentityConfiguration {
        @Bean
        AtomicInteger authenticatorInvocations() {
            return new AtomicInteger();
        }

        @Bean
        IdentityAuthenticator identityAuthenticator(AtomicInteger authenticatorInvocations) {
            return request -> {
                authenticatorInvocations.incrementAndGet();
                return Optional.of(new IamPrincipal(
                    7L, "identity-7", "EXAMPLE", 31L, 9L, request.clientType(), 4L));
            };
        }
    }
}
