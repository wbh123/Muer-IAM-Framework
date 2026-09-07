package cloud.muer.autoconfigure;

import cloud.muer.authorization.AuthorizationEngine;
import cloud.muer.authorization.AuthorizationProfileRepository;
import cloud.muer.authorization.AuthorizationVersionRepository;
import cloud.muer.authorization.PermissionTemplateVersionRepository;
import cloud.muer.authentication.AuthenticationService;
import cloud.muer.authentication.IdentityAuthenticator;
import cloud.muer.authentication.LoginRequest;
import cloud.muer.core.model.IamPrincipal;
import cloud.muer.core.metrics.MuerMetrics;
import cloud.muer.core.metrics.NoOpMuerMetrics;
import cloud.muer.autoconfigure.observability.MicrometerMuerMetrics;
import cloud.muer.autoconfigure.observability.MuerHealthIndicator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import cloud.muer.core.port.ResourceHierarchyProvider;
import cloud.muer.session.TokenStore;
import cloud.muer.session.SessionRepository;
import cloud.muer.web.IamAdministrationController;
import cloud.muer.web.IamAuthenticationController;
import cloud.muer.web.IamAuthorizationController;
import cloud.muer.web.IamSessionController;
import cloud.muer.persistence.MyBatisAuthorizationProfileRepository;
import cloud.muer.persistence.MyBatisAuthorizationVersionRepository;
import cloud.muer.persistence.MyBatisPermissionTemplateVersionRepository;
import cloud.muer.persistence.MyBatisSessionRepository;
import cloud.muer.persistence.RedisTokenStore;
import cloud.muer.autoconfigure.web.IamAuthorizationInterceptor;
import cloud.muer.autoconfigure.web.IamAuthorizationFailureHandler;
import cloud.muer.autoconfigure.web.ProblemDetailIamAuthorizationFailureHandler;
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
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
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

class MuerAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AdapterConfiguration.class)
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(MuerAutoConfiguration.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enabled_configuration_registers_engine_authentication_and_bearer_filter() {
        contextRunner.withPropertyValues(
                        "muer.enabled=true",
                        "muer.token.ttl=4h",
                        "muer.token.redis-prefix=custom-iam",
                        "muer.session.touch-interval=3m",
                        "muer.client-types=WEB,MOBILE")
                .run(context -> {
                    assertNotNull(context.getBean(AuthorizationEngine.class));
                    assertNotNull(context.getBean(IamBearerTokenFilter.class));
                    assertFalse(context.getBean("iamBearerTokenFilterRegistration",
                            FilterRegistrationBean.class).isEnabled());
                    assertNotNull(context.getBean(IamAuthenticationController.class));
                    assertNotNull(context.getBean(IamAuthorizationController.class));
                    assertNotNull(context.getBean(IamAdministrationController.class));
                    assertNotNull(context.getBean(IamSessionController.class));
                    var properties = context.getBean(MuerProperties.class);
                    assertEquals(Duration.ofHours(4), properties.getToken().getTtl());
                    assertEquals("custom-iam", properties.getToken().getRedisPrefix());
                    assertEquals(Duration.ofMinutes(3), properties.getSession().getTouchInterval());
                    assertEquals(java.util.List.of("WEB", "MOBILE"), properties.getClientTypes());
                });
    }

    @Test
    void disabled_configuration_registers_no_runtime_components() {
        contextRunner.withPropertyValues("muer.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean("iamAuthorizationEngine"));
                    assertFalse(context.containsBean("iamBearerTokenFilter"));
                });
    }

    @Test
    void observability_selects_micrometer_only_when_a_registry_is_available() {
        new ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        MuerMicrometerAutoConfiguration.class, MuerObservabilityAutoConfiguration.class))
                .run(context -> assertEquals(NoOpMuerMetrics.INSTANCE, context.getBean(MuerMetrics.class)));

        new ApplicationContextRunner()
                .withBean(SimpleMeterRegistry.class)
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        MuerMicrometerAutoConfiguration.class, MuerObservabilityAutoConfiguration.class))
                .run(context -> assertEquals(MicrometerMuerMetrics.class, context.getBean(MuerMetrics.class).getClass()));
    }

    @Test
    void health_indicator_reports_framework_availability_without_a_datastore_probe() {
        var health = new MuerHealthIndicator().health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals(true, health.getDetails().get("enabled"));
    }

    @Test
    void rejects_a_non_positive_token_ttl_during_startup() {
        contextRunner.withPropertyValues("muer.token.ttl=0s")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void rejects_an_empty_login_client_type_allow_list_during_startup() {
        contextRunner.withPropertyValues("muer.client-types=")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void rejects_an_empty_redis_token_prefix_during_startup() {
        contextRunner.withPropertyValues("muer.token.redis-prefix=")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void rejects_an_empty_schema_history_table_during_startup() {
        contextRunner.withPropertyValues("muer.schema.history-table=")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void configured_client_types_reject_a_login_before_the_host_authenticator_runs() {
        contextRunner.withUserConfiguration(PermissiveIdentityConfiguration.class)
                .withPropertyValues("muer.client-types=WEB")
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
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(MuerAutoConfiguration.class))
                .withPropertyValues("muer.schema.enabled=false")
                .run(context -> assertFalse(context.containsBean("iamSchemaMigrator")));
    }

    @Test
    void infrastructure_beans_activate_default_persistence_adapters() {
        new ApplicationContextRunner()
                .withUserConfiguration(InfrastructureConfiguration.class)
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(MuerAutoConfiguration.class))
                .withPropertyValues("muer.schema.enabled=false")
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
                        MuerAutoConfiguration.class))
                .run(context -> {
                    assertNotNull(context.getBean("iamSecurityFilterChain", SecurityFilterChain.class));
                    assertNotNull(context.getBean(IamAuthorizationInterceptor.class));
                    assertNotNull(context.getBean("iamAuthorizationWebMvcConfigurer", WebMvcConfigurer.class));
                });
    }

    @Test
    void servlet_application_registers_the_default_authorization_failure_handler() {
        new WebApplicationContextRunner()
                .withUserConfiguration(AdapterConfiguration.class)
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
                        MuerAutoConfiguration.class))
                .run(context -> assertEquals(ProblemDetailIamAuthorizationFailureHandler.class,
                        context.getBean(IamAuthorizationFailureHandler.class).getClass()));
    }

    @Test
    void host_authorization_failure_handler_replaces_the_default() {
        new WebApplicationContextRunner()
                .withUserConfiguration(AdapterConfiguration.class, CustomFailureHandlerConfiguration.class)
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
                        MuerAutoConfiguration.class))
                .run(context -> {
                    assertTrue(context.containsBean("customFailureHandler"));
                    assertFalse(context.containsBean("iamAuthorizationFailureHandler"));
                    assertEquals(context.getBean("customFailureHandler", IamAuthorizationFailureHandler.class),
                            context.getBean(IamAuthorizationFailureHandler.class));
                });
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

    @Configuration(proxyBeanMethods = false)
    static class CustomFailureHandlerConfiguration {
        @Bean
        IamAuthorizationFailureHandler customFailureHandler() {
            return (request, response, status, failure) -> response.setStatus(status);
        }
    }
}
