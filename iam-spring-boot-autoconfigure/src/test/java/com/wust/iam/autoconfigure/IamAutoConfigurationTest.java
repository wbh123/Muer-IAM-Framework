package com.wust.iam.autoconfigure;

import com.wust.iam.authorization.AuthorizationEngine;
import com.wust.iam.authorization.AuthorizationProfileRepository;
import com.wust.iam.authorization.AuthorizationVersionRepository;
import com.wust.iam.authorization.PermissionTemplateVersionRepository;
import com.wust.iam.authentication.AuthenticationService;
import com.wust.iam.core.model.IamPrincipal;
import com.wust.iam.core.port.ResourceHierarchyProvider;
import com.wust.iam.session.TokenStore;
import com.wust.iam.session.SessionRepository;
import com.wust.iam.web.IamAdministrationController;
import com.wust.iam.web.IamAuthenticationController;
import com.wust.iam.web.IamAuthorizationController;
import com.wust.iam.web.IamSessionController;
import com.wust.iam.persistence.MyBatisAuthorizationProfileRepository;
import com.wust.iam.persistence.MyBatisAuthorizationVersionRepository;
import com.wust.iam.persistence.MyBatisPermissionTemplateVersionRepository;
import com.wust.iam.persistence.MyBatisSessionRepository;
import com.wust.iam.persistence.RedisTokenStore;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
