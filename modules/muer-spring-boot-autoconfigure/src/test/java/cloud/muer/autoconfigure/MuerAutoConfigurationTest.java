package cloud.muer.autoconfigure;

import cloud.muer.authorization.AuthorizationEngine;
import cloud.muer.authorization.AuthorizationProfile;
import cloud.muer.authorization.AuthorizationProfileRepository;
import cloud.muer.authorization.AuthorizationRequest;
import cloud.muer.authorization.AuthorizationVersionRepository;
import cloud.muer.authorization.AdministrationBootstrapRequest;
import cloud.muer.authorization.MuerAdministrationBootstrapService;
import cloud.muer.authorization.PermissionSummary;
import cloud.muer.authorization.PermissionSummaryPage;
import cloud.muer.authorization.PermissionTemplate;
import cloud.muer.authorization.PermissionTemplateQueryRepository;
import cloud.muer.authorization.PermissionTemplateVersionRepository;
import cloud.muer.authorization.PermissionTemplateCommandRepository;
import cloud.muer.authorization.PermissionTemplateLifecycleService;
import cloud.muer.authorization.PermissionTemplateVersion;
import cloud.muer.authorization.TemplateVersionStatus;
import cloud.muer.authentication.AuthenticationService;
import cloud.muer.authentication.IdentityAuthenticator;
import cloud.muer.authentication.LoginRequest;
import cloud.muer.core.model.IamPrincipal;
import cloud.muer.core.model.IamUser;
import cloud.muer.core.model.ResourceDescriptor;
import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.model.ScopeAccess;
import cloud.muer.core.metrics.MuerMetrics;
import cloud.muer.core.metrics.NoOpMuerMetrics;
import cloud.muer.autoconfigure.observability.MicrometerMuerMetrics;
import cloud.muer.autoconfigure.observability.MuerHealthIndicator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import cloud.muer.core.port.ResourceHierarchyProvider;
import cloud.muer.core.port.IamUserRepository;
import cloud.muer.session.TokenStore;
import cloud.muer.session.SessionRepository;
import cloud.muer.http.IamAdministrationController;
import cloud.muer.http.IamAuthenticationController;
import cloud.muer.http.IamAuthorizationController;
import cloud.muer.http.IamManagementTemplatesController;
import cloud.muer.http.IamSessionController;
import cloud.muer.persistence.MyBatisAuthorizationProfileRepository;
import cloud.muer.persistence.MyBatisAuthorizationVersionRepository;
import cloud.muer.persistence.MyBatisPermissionTemplateVersionRepository;
import cloud.muer.persistence.MyBatisPermissionTemplateCommandRepository;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.Set;
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
    void core_only_context_does_not_create_persistence_bound_template_lifecycle() {
        contextRunner.run(context -> {
            assertTrue(context.isRunning());
            assertFalse(context.containsBean("iamPermissionTemplateCommandRepository"));
            assertFalse(context.containsBean("iamPermissionTemplateLifecycleService"));
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
    void default_hierarchy_allows_muer_management_but_not_host_resources() {
        authorizationRunner(DefaultAuthorizationConfiguration.class).run(context -> {
            var engine = context.getBean(AuthorizationEngine.class);
            var principal = DefaultAuthorizationConfiguration.principal();

            assertTrue(engine.decide(principal, request("iam.admin.template.read",
                    "IAM_PERMISSION_TEMPLATE_COLLECTION", "templates", ScopeAccess.READ)).allowed());
            assertFalse(engine.decide(principal, request("document:read",
                    "DOCUMENT", "1001", ScopeAccess.READ)).allowed());
            assertFalse(engine.decide(principal, request("iam.admin.user.read",
                    "IAM_USER", "7", ScopeAccess.READ)).allowed());
        });
    }

    @Test
    void default_autoconfiguration_bootstraps_an_administrator_that_can_read_and_write_management_resources() {
        authorizationRunner(BootstrapAuthorizationConfiguration.class).run(context -> {
            var result = context.getBean(MuerAdministrationBootstrapService.class)
                    .bootstrapFirstAdministrator(new AdministrationBootstrapRequest(7L, Set.of("WEB")));
            var principal = new IamPrincipal(7L, "host-admin", "HOST", result.profileId(),
                    result.templateVersionId(), "WEB", 1L);
            var engine = context.getBean(AuthorizationEngine.class);

            assertTrue(engine.decide(principal, request("iam.admin.template.read",
                    "IAM_PERMISSION_TEMPLATE_COLLECTION", "templates", ScopeAccess.READ)).allowed());
            assertTrue(engine.decide(principal, request("iam.admin.template.write",
                    "IAM_PERMISSION_TEMPLATE_COLLECTION", "templates", ScopeAccess.WRITE)).allowed());

            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken(principal, null, "ROLE_TEST"));
            assertEquals(200, context.getBean(IamManagementTemplatesController.class)
                    .listPermissionTemplates(null, null).getStatusCode().value());
        });
    }

    @Test
    void host_hierarchy_is_composed_with_muer_management_hierarchy() {
        authorizationRunner(DefaultAuthorizationConfiguration.class, HostResourceHierarchyConfiguration.class).run(context -> {
            var engine = context.getBean(AuthorizationEngine.class);
            var principal = DefaultAuthorizationConfiguration.principal();

            assertTrue(engine.decide(principal, request("iam.admin.template.read",
                    "IAM_PERMISSION_TEMPLATE_COLLECTION", "templates", ScopeAccess.READ)).allowed());
            assertTrue(engine.decide(principal, request("document:read",
                    "DOCUMENT", "1001", ScopeAccess.READ)).allowed());
        });
    }

    @Test
    void host_resource_hierarchy_remains_unambiguous_for_host_consumers() {
        authorizationRunner(DefaultAuthorizationConfiguration.class, HostResourceHierarchyConfiguration.class).run(context ->
                assertEquals(context.getBean("hostResourceHierarchyProvider", ResourceHierarchyProvider.class),
                        context.getBean(ResourceHierarchyProvider.class)));
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
                    assertEquals(MyBatisPermissionTemplateCommandRepository.class,
                            context.getBean(PermissionTemplateCommandRepository.class).getClass());
                    assertNotNull(context.getBean(PermissionTemplateLifecycleService.class));
                    assertEquals(MyBatisSessionRepository.class,
                            context.getBean(SessionRepository.class).getClass());
                    assertEquals(RedisTokenStore.class, context.getBean(TokenStore.class).getClass());
                    assertNotNull(context.getBean(IamAuthenticationController.class));
                });
    }

    @Test
    void host_template_command_repository_suppresses_the_default() {
        new ApplicationContextRunner()
                .withUserConfiguration(InfrastructureConfiguration.class, CustomTemplateCommandConfiguration.class)
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(MuerAutoConfiguration.class))
                .withPropertyValues("muer.schema.enabled=false")
                .run(context -> assertEquals(context.getBean("customTemplateCommandRepository"),
                        context.getBean(PermissionTemplateCommandRepository.class)));
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

    @SafeVarargs
    private static ApplicationContextRunner authorizationRunner(Class<?>... configurations) {
        return new ApplicationContextRunner()
                .withUserConfiguration(configurations)
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(MuerAutoConfiguration.class));
    }

    private static AuthorizationRequest request(String permission, String resourceType, String resourceId,
                                                ScopeAccess access) {
        return new AuthorizationRequest(permission, "HOST", "WEB",
                new ResourceDescriptor(resourceType, resourceId, List.of(), Map.of()), access);
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
    static class DefaultAuthorizationConfiguration {
        private static final AuthorizationProfile PROFILE = new AuthorizationProfile(10L, 7L, "admin", 20L,
                Set.of("WEB"), true, false, null, null, List.of(
                new ResourceScope("IAM_ADMIN", "*", ScopeAccess.READ),
                new ResourceScope("PROJECT", "101", ScopeAccess.READ)));
        private static final PermissionTemplateVersion VERSION = new PermissionTemplateVersion(20L, 1L, 1,
                TemplateVersionStatus.PUBLISHED, Set.of("iam.admin.template.read", "document:read"));

        static IamPrincipal principal() {
            return new IamPrincipal(7L, "host-admin", "HOST", 10L, 20L, "WEB", 1L);
        }

        @Bean
        TokenStore tokenStore() { return mock(TokenStore.class); }

        @Bean
        SessionRepository sessionRepository() { return mock(SessionRepository.class); }

        @Bean
        AuthorizationVersionRepository authorizationVersionRepository() {
            return new AuthorizationVersionRepository() {
                public long currentVersion(long userId) { return 1L; }
                public long increment(long userId) { return 2L; }
            };
        }

        @Bean
        AuthorizationProfileRepository authorizationProfileRepository() {
            return new AuthorizationProfileRepository() {
                public AuthorizationProfile require(long profileId) { return PROFILE; }
                public List<AuthorizationProfile> findByUserId(long userId) { return List.of(PROFILE); }
                public void save(AuthorizationProfile profile) { }
            };
        }

        @Bean
        PermissionTemplateVersionRepository permissionTemplateVersionRepository() {
            return new PermissionTemplateVersionRepository() {
                public PermissionTemplateVersion require(long versionId) { return VERSION; }
                public void save(PermissionTemplateVersion version) { }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HostResourceHierarchyConfiguration {
        @Bean
        ResourceHierarchyProvider hostResourceHierarchyProvider() {
            return (resource, scope) -> "PROJECT".equals(scope.scopeType())
                    && "101".equals(scope.scopeRefId())
                    && "DOCUMENT".equals(resource.resourceType())
                    && "1001".equals(resource.resourceId());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BootstrapAuthorizationConfiguration {
        @Bean
        BootstrapFixture bootstrapFixture() {
            return new BootstrapFixture();
        }

        @Bean
        TokenStore tokenStore() { return mock(TokenStore.class); }

        @Bean
        SessionRepository sessionRepository() { return mock(SessionRepository.class); }

        @Bean
        AuthorizationVersionRepository authorizationVersionRepository() {
            return new AuthorizationVersionRepository() {
                public long currentVersion(long userId) { return 1L; }
                public long increment(long userId) { return 2L; }
            };
        }

        @Bean
        AuthorizationProfileRepository authorizationProfileRepository(BootstrapFixture fixture) {
            return fixture.profiles;
        }

        @Bean
        PermissionTemplateVersionRepository permissionTemplateVersionRepository(BootstrapFixture fixture) {
            return fixture.versions;
        }

        @Bean
        IamUserRepository iamUserRepository() {
            return new IamUserRepository() {
                public Optional<IamUser> findById(long userId) {
                    return userId == 7 ? Optional.of(new IamUser(7L, "host-admin", "HOST", true, 1L))
                            : Optional.empty();
                }
                public List<IamUser> findPage(long afterUserId, int limit) { return List.of(); }
                public void save(IamUser user) { }
            };
        }

        @Bean
        PermissionTemplateCommandRepository permissionTemplateCommandRepository(BootstrapFixture fixture) {
            return fixture.templates;
        }

        @Bean
        PermissionTemplateQueryRepository permissionTemplateQueryRepository(BootstrapFixture fixture) {
            return new PermissionTemplateQueryRepository() {
                public Optional<PermissionTemplate> findTemplate(long templateId) {
                    return fixture.findTemplate(templateId);
                }
                public List<PermissionTemplate> listTemplates(long afterTemplateId, int limit) {
                    return fixture.listTemplates(afterTemplateId, limit);
                }
                public List<PermissionTemplateVersion> findVersionsByTemplate(long templateId) {
                    return fixture.findVersionsByTemplate(templateId);
                }
                public Optional<PermissionTemplateVersion> findVersionById(long versionId) {
                    return fixture.findVersionById(versionId);
                }
                public PermissionSummaryPage listPermissions(String keyword, String domain,
                                                              long afterPermissionId, int limit) {
                    return fixture.listPermissions(keyword, domain, afterPermissionId, limit);
                }
            };
        }
    }

    static final class BootstrapFixture {
        private long nextTemplateId = 1L;
        private long nextVersionId = 1L;
        private long nextProfileId = 1L;
        final Map<Long, PermissionTemplate> templatesById = new HashMap<>();
        final Map<Long, PermissionTemplateVersion> versionsById = new HashMap<>();
        final Map<Long, AuthorizationProfile> profilesById = new HashMap<>();
        final PermissionTemplateCommandRepository templates = new PermissionTemplateCommandRepository() {
            public PermissionTemplate createTemplate(String key, String name, String description, boolean enabled) {
                var template = new PermissionTemplate(nextTemplateId++, key, name, description, enabled);
                templatesById.put(template.templateId(), template);
                return template;
            }

            public PermissionTemplateVersion createNextDraftVersion(long templateId, Set<String> permissions) {
                var version = new PermissionTemplateVersion(nextVersionId++, templateId, 1,
                        TemplateVersionStatus.DRAFT, permissions);
                versionsById.put(version.versionId(), version);
                return version;
            }
        };
        final PermissionTemplateVersionRepository versions = new PermissionTemplateVersionRepository() {
            public PermissionTemplateVersion require(long versionId) { return versionsById.get(versionId); }
            public void save(PermissionTemplateVersion version) { versionsById.put(version.versionId(), version); }
        };
        final AuthorizationProfileRepository profiles = new AuthorizationProfileRepository() {
            public AuthorizationProfile require(long profileId) { return profilesById.get(profileId); }
            public List<AuthorizationProfile> findByUserId(long userId) {
                return profilesById.values().stream().filter(profile -> profile.userId() == userId).toList();
            }
            public void save(AuthorizationProfile profile) { profilesById.put(profile.profileId(), profile); }
            public AuthorizationProfile create(AuthorizationProfile profile) {
                var created = new AuthorizationProfile(nextProfileId++, profile.userId(), profile.profileName(),
                        profile.templateVersionId(), profile.clientTypes(), profile.enabled(), profile.revoked(),
                        profile.validFrom(), profile.validUntil(), profile.scopes());
                profilesById.put(created.profileId(), created);
                return created;
            }
        };

        public Optional<PermissionTemplate> findTemplate(long templateId) {
            return Optional.ofNullable(templatesById.get(templateId));
        }

        public List<PermissionTemplate> listTemplates(long afterTemplateId, int limit) {
            return templatesById.values().stream().filter(template -> template.templateId() > afterTemplateId)
                    .limit(limit).toList();
        }

        public List<PermissionTemplateVersion> findVersionsByTemplate(long templateId) {
            return versionsById.values().stream().filter(version -> version.templateId() == templateId).toList();
        }

        public Optional<PermissionTemplateVersion> findVersionById(long versionId) {
            return Optional.ofNullable(versionsById.get(versionId));
        }

        public PermissionSummaryPage listPermissions(String keyword, String domain, long afterPermissionId, int limit) {
            var permissions = List.of(
                    new PermissionSummary("iam.admin.template.read", "Read templates", null, true, 1L),
                    new PermissionSummary("iam.admin.template.write", "Write templates", null, true, 2L));
            return new PermissionSummaryPage(afterPermissionId == 0 ? permissions : List.of(), 2L);
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

    @Configuration(proxyBeanMethods = false)
    static class CustomTemplateCommandConfiguration {
        @Bean
        PermissionTemplateCommandRepository customTemplateCommandRepository() {
            return mock(PermissionTemplateCommandRepository.class);
        }
    }
}
