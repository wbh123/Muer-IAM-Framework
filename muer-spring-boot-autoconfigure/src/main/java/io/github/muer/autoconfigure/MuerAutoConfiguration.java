package io.github.muer.autoconfigure;

import io.github.muer.authentication.AuthenticationService;
import io.github.muer.authentication.IdentityAuthenticator;
import io.github.muer.authentication.AuthorizationProfileSwitchService;
import io.github.muer.authentication.AccountGovernanceService;
import io.github.muer.audit.AuditQueryRepository;
import io.github.muer.authorization.AuthorizationEngine;
import io.github.muer.authorization.AuthorizationPolicy;
import io.github.muer.authorization.AuthorizationProfileQueryRepository;
import io.github.muer.authorization.AuthorizationProfileRepository;
import io.github.muer.authorization.AuthorizationVersionRepository;
import io.github.muer.authorization.DefaultAuthorizationEngine;
import io.github.muer.authorization.PermissionTemplateQueryRepository;
import io.github.muer.authorization.PermissionTemplateVersionRepository;
import io.github.muer.authorization.AuthorizationProfileService;
import io.github.muer.authorization.AuthorizationScopeMutation;
import io.github.muer.authorization.AuthorizationVersionService;
import io.github.muer.authorization.PermissionTemplateService;
import io.github.muer.authorization.PermissionRegistrationService;
import io.github.muer.authorization.PermissionRepository;
import io.github.muer.core.port.ResourceHierarchyProvider;
import io.github.muer.core.port.IamUserRepository;
import io.github.muer.core.port.IdentityRepository;
import io.github.muer.core.port.OverviewRepository;
import io.github.muer.core.port.UserQueryRepository;
import io.github.muer.diagnostics.AuthorizationDiagnosticsService;
import io.github.muer.session.SessionQueryRepository;
import io.github.muer.session.SessionRepository;
import io.github.muer.session.SessionService;
import io.github.muer.session.TokenStore;
import io.github.muer.session.LoginEventRepository;
import io.github.muer.web.IamAdministrationController;
import io.github.muer.web.IamAuthenticationController;
import io.github.muer.web.IamAuthorizationController;
import io.github.muer.web.IamCapabilitiesController;
import io.github.muer.web.IamManagementAuditController;
import io.github.muer.web.IamManagementOverviewController;
import io.github.muer.web.IamManagementProfilesController;
import io.github.muer.web.IamManagementSessionsController;
import io.github.muer.web.IamManagementTemplatesController;
import io.github.muer.web.IamManagementUsersController;
import io.github.muer.web.IamSessionController;
import io.github.muer.persistence.MyBatisAuthorizationProfileQueryRepository;
import io.github.muer.persistence.MyBatisAuditQueryRepository;
import io.github.muer.persistence.MyBatisOverviewRepository;
import io.github.muer.persistence.MyBatisPermissionTemplateQueryRepository;
import io.github.muer.persistence.MyBatisSessionQueryRepository;
import io.github.muer.persistence.MyBatisUserQueryRepository;
import io.github.muer.persistence.MyBatisAuthorizationProfileRepository;
import io.github.muer.persistence.MyBatisAuthorizationScopeMutation;
import io.github.muer.persistence.MyBatisAuthorizationVersionRepository;
import io.github.muer.persistence.MyBatisPermissionTemplateVersionRepository;
import io.github.muer.persistence.MyBatisPermissionRepository;
import io.github.muer.persistence.MyBatisSessionRepository;
import io.github.muer.persistence.RedisTokenStore;
import io.github.muer.persistence.MyBatisLoginEventRepository;
import io.github.muer.persistence.MyBatisIamUserRepository;
import io.github.muer.persistence.MyBatisIdentityRepository;
import io.github.muer.autoconfigure.web.IamAuthorizationInterceptor;
import io.github.muer.autoconfigure.web.IamAuthorizationWebMvcConfiguration;
import io.github.muer.autoconfigure.web.IamAuthorizationFailureHandler;
import io.github.muer.autoconfigure.web.MvcResourceDescriptorResolver;
import io.github.muer.autoconfigure.web.ProblemDetailIamAuthorizationFailureHandler;
import io.github.muer.autoconfigure.web.RequirePermissionDefinitionWarningListener;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import java.io.IOException;

/**
 * Muer 的 Spring Boot 自动配置入口。
 *
 * <p>仅重命名框架入口与配置前缀；认证、授权、会话和诊断装配顺序保持不变。</p>
 */
@AutoConfiguration
@AutoConfigureAfter(name = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@EnableConfigurationProperties(MuerProperties.class)
@ConditionalOnProperty(prefix = "muer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MuerAutoConfiguration {
    private static final java.util.List<String> MAPPER_RESOURCES = java.util.List.of(
            "mapper/iam/IamSessionMapper.xml",
            "mapper/iam/IamUserMapper.xml",
            "mapper/iam/IamIdentityMapper.xml",
            "mapper/iam/IamLoginEventMapper.xml",
            "mapper/iam/IamAuthorizationVersionMapper.xml",
            "mapper/iam/IamAuthorizationProfileMapper.xml",
            "mapper/iam/IamPermissionTemplateVersionMapper.xml",
            "mapper/iam/IamPermissionMapper.xml",
            "mapper/iam/IamPermissionTemplateQueryMapper.xml",
            "mapper/iam/IamOverviewMapper.xml",
            "mapper/iam/IamAuditMapper.xml");

    @Bean
    @ConditionalOnMissingBean(ResourceHierarchyProvider.class)
    ResourceHierarchyProvider iamResourceHierarchyProvider() {
        return (resource, scope) -> false;
    }

    @Bean(initMethod = "migrate")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "muer.schema", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(MuerSchemaMigrator.class)
    MuerSchemaMigrator iamSchemaMigrator(DataSource dataSource, MuerProperties properties) {
        return new MuerSchemaMigrator(dataSource, properties.getSchema().getHistoryTable());
    }

    @Bean
    @ConditionalOnMissingBean(SqlSessionFactory.class)
    @ConditionalOnBean(DataSource.class)
    SqlSessionFactory iamSqlSessionFactory(DataSource dataSource) {
        var environment = new Environment("iam", new JdbcTransactionFactory(), dataSource);
        var configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        for (String resource : MAPPER_RESOURCES) {
            try (var reader = Resources.getResourceAsReader(resource)) {
                new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
            } catch (IOException exception) {
                throw new IllegalStateException("cannot load IAM MyBatis mapper: " + resource, exception);
            }
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationProfileRepository.class)
    AuthorizationProfileRepository iamAuthorizationProfileRepository(SqlSessionFactory sessions) {
        return new MyBatisAuthorizationProfileRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationVersionRepository.class)
    AuthorizationVersionRepository iamAuthorizationVersionRepository(SqlSessionFactory sessions) {
        return new MyBatisAuthorizationVersionRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationScopeMutation.class)
    @ConditionalOnBean(SqlSessionFactory.class)
    AuthorizationScopeMutation iamAuthorizationScopeMutation(SqlSessionFactory sessions) {
        return new MyBatisAuthorizationScopeMutation(sessions);
    }

    @Bean
    @ConditionalOnMissingBean(PermissionTemplateVersionRepository.class)
    PermissionTemplateVersionRepository iamPermissionTemplateVersionRepository(SqlSessionFactory sessions) {
        return new MyBatisPermissionTemplateVersionRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean(PermissionRepository.class)
    PermissionRepository iamPermissionRepository(SqlSessionFactory sessions) {
        return new MyBatisPermissionRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean
    PermissionRegistrationService iamPermissionRegistrationService(PermissionRepository permissions) {
        return new PermissionRegistrationService(permissions);
    }

    @Bean
    @ConditionalOnMissingBean
    PermissionDefinitionRegistrationListener iamPermissionDefinitionRegistrationListener(
            PermissionRegistrationService registration,
            java.util.List<io.github.muer.authorization.PermissionDefinitionProvider> providers) {
        return new PermissionDefinitionRegistrationListener(registration, providers);
    }

    @Bean
    @ConditionalOnMissingBean(SessionRepository.class)
    SessionRepository iamSessionRepository(SqlSessionFactory sessions) {
        return new MyBatisSessionRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean(LoginEventRepository.class)
    @ConditionalOnBean(SqlSessionFactory.class)
    LoginEventRepository iamLoginEventRepository(SqlSessionFactory sessions) {
        return new MyBatisLoginEventRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean(IamUserRepository.class)
    @ConditionalOnBean(SqlSessionFactory.class)
    IamUserRepository iamUserRepository(SqlSessionFactory sessions) {
        return new MyBatisIamUserRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean(IdentityRepository.class)
    @ConditionalOnBean(SqlSessionFactory.class)
    IdentityRepository iamIdentityRepository(SqlSessionFactory sessions) {
        return new MyBatisIdentityRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean(TokenStore.class)
    TokenStore iamTokenStore(StringRedisTemplate redis, MuerProperties properties) {
        return new RedisTokenStore(redis, properties.getToken().getRedisPrefix());
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationEngine.class)
    @ConditionalOnBean({ResourceHierarchyProvider.class, AuthorizationProfileRepository.class,
            PermissionTemplateVersionRepository.class})
    AuthorizationEngine iamAuthorizationEngine(ResourceHierarchyProvider hierarchy,
                                               AuthorizationProfileRepository profiles,
                                               PermissionTemplateVersionRepository templates,
                                               ObjectProvider<AuthorizationPolicy> policies) {
        return new DefaultAuthorizationEngine(hierarchy,
                principal -> principal.activeProfileId() == null ? java.util.Set.of()
                        : templates.require(profiles.require(principal.activeProfileId()).templateVersionId()).permissions(),
                principal -> principal.activeProfileId() == null ? java.util.List.of()
                        : profiles.require(principal.activeProfileId()).scopes(),
                policies.orderedStream().toList(),
                principal -> principal.activeProfileId() == null ? null
                        : profiles.require(principal.activeProfileId()),
                java.time.Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationService.class)
    @ConditionalOnBean({TokenStore.class, SessionRepository.class, AuthorizationVersionRepository.class})
    AuthenticationService iamAuthenticationService(TokenStore tokens, SessionRepository sessions,
                                                   ObjectProvider<LoginEventRepository> loginEvents,
                                                   AuthorizationVersionRepository versions,
                                                   ObjectProvider<IdentityAuthenticator> authenticator,
                                                   MuerProperties properties) {
        var allowedClientTypes = Set.copyOf(properties.getClientTypes());
        var hostAuthenticator = authenticator.getIfAvailable(() -> request -> Optional.empty());
        IdentityAuthenticator configuredAuthenticator = request -> allowedClientTypes.contains(request.clientType())
                ? hostAuthenticator.authenticate(request)
                : Optional.empty();
        return new AuthenticationService(tokens, sessions,
                loginEvents.getIfAvailable(() -> event -> { }), versions::currentVersion,
                configuredAuthenticator, Clock.systemUTC(),
                properties.getToken().getTtl(), properties.getSession().getTouchInterval(),
                MuerAutoConfiguration::randomId,
                MuerAutoConfiguration::randomId, MuerAutoConfiguration::randomId);
    }

    @Bean
    @ConditionalOnMissingBean(IamBearerTokenFilter.class)
    @ConditionalOnBean(AuthenticationService.class)
    IamBearerTokenFilter iamBearerTokenFilter(AuthenticationService authentication) {
        return new IamBearerTokenFilter(authentication);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(IamAuthorizationFailureHandler.class)
    IamAuthorizationFailureHandler iamAuthorizationFailureHandler(ObjectProvider<ObjectMapper> json) {
        return new ProblemDetailIamAuthorizationFailureHandler(json.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(AuthorizationEngine.class)
    @ConditionalOnMissingBean(IamAuthorizationInterceptor.class)
    IamAuthorizationInterceptor iamAuthorizationInterceptor(AuthorizationEngine authorization,
                                                             ObjectProvider<MvcResourceDescriptorResolver> resources,
                                                             IamAuthorizationFailureHandler failures) {
        return new IamAuthorizationInterceptor(authorization, resources.getIfAvailable(), failures);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(IamAuthorizationInterceptor.class)
    @ConditionalOnMissingBean(IamAuthorizationWebMvcConfiguration.class)
    WebMvcConfigurer iamAuthorizationWebMvcConfigurer(IamAuthorizationInterceptor interceptor) {
        return new IamAuthorizationWebMvcConfiguration(interceptor);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(RequirePermissionDefinitionWarningListener.class)
    RequirePermissionDefinitionWarningListener iamRequirePermissionDefinitionWarningListener(
            PermissionRegistrationService registration,
            ObjectProvider<org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping> mappings) {
        return new RequirePermissionDefinitionWarningListener(registration, mappings);
    }

    @Bean(name = "iamBearerTokenFilterRegistration")
    FilterRegistrationBean<IamBearerTokenFilter> iamBearerTokenFilterRegistration(
            IamBearerTokenFilter bearerFilter) {
        var registration = new FilterRegistrationBean<>(bearerFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean(name = "iamSecurityFilterChain")
    @Order(90)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(name = "iamSecurityFilterChain")
    SecurityFilterChain iamSecurityFilterChain(HttpSecurity http, IamBearerTokenFilter bearerFilter)
            throws Exception {
        return http
                .securityMatcher("/iam/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/iam/auth/login").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(bearerFilter, AnonymousAuthenticationFilter.class)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    AuthorizationVersionService iamAuthorizationVersionService(AuthorizationVersionRepository versions) {
        return new AuthorizationVersionService(versions);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({IamUserRepository.class, IdentityRepository.class})
    AccountGovernanceService iamAccountGovernanceService(IamUserRepository users, IdentityRepository identities,
                                                         AuthorizationVersionService versions,
                                                         ObjectProvider<UserQueryRepository> queries) {
        return new AccountGovernanceService(users, identities, versions, queries.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    AuthorizationProfileService iamAuthorizationProfileService(AuthorizationProfileRepository profiles,
                                                                AuthorizationVersionService versions,
                                                                ObjectProvider<AuthorizationScopeMutation> mutation) {
        var atomicMutation = mutation.getIfAvailable();
        return atomicMutation == null
                ? new AuthorizationProfileService(profiles, versions)
                : new AuthorizationProfileService(profiles, versions, atomicMutation);
    }

    @Bean
    @ConditionalOnMissingBean
    PermissionTemplateService iamPermissionTemplateService(PermissionTemplateVersionRepository templates) {
        return new PermissionTemplateService(templates);
    }

    @Bean
    @ConditionalOnMissingBean
    SessionService iamSessionService(SessionRepository sessions, TokenStore tokens) {
        return new SessionService(sessions, tokens);
    }

    @Bean
    @ConditionalOnMissingBean
    AuthorizationDiagnosticsService iamAuthorizationDiagnosticsService(AuthorizationEngine authorization) {
        return new AuthorizationDiagnosticsService(authorization);
    }

    @Bean
    @ConditionalOnMissingBean
    AuthorizationProfileSwitchService iamAuthorizationProfileSwitchService(AuthorizationProfileService profiles,
                                                                            AuthenticationService authentication) {
        return new AuthorizationProfileSwitchService(profiles, authentication);
    }

    @Bean
    @ConditionalOnMissingBean
    IamAuthenticationController iamAuthenticationController(AuthenticationService authentication,
                                                            TokenStore tokens, SessionService sessions) {
        return new IamAuthenticationController(authentication, tokens, sessions);
    }

    @Bean
    @ConditionalOnMissingBean
    IamSessionController iamSessionController(SessionRepository sessions, SessionService service) {
        return new IamSessionController(sessions, service);
    }

    @Bean
    @ConditionalOnMissingBean
    IamAuthorizationController iamAuthorizationController(AuthorizationDiagnosticsService diagnostics,
                                                          AuthorizationProfileService profiles,
                                                          AuthorizationProfileSwitchService switches) {
        return new IamAuthorizationController(diagnostics, profiles, switches);
    }

    @Bean
    @ConditionalOnMissingBean
    IamAdministrationController iamAdministrationController(AuthorizationEngine authorization,
                                                            PermissionTemplateService templates,
                                                            AuthorizationProfileService profiles,
                                                            AuthorizationVersionService versions,
                                                            SessionService sessions,
                                                            ObjectProvider<AccountGovernanceService> accounts) {
        return new IamAdministrationController(authorization, templates, profiles, versions, sessions,
                accounts.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SqlSessionFactory.class)
    UserQueryRepository iamUserQueryRepository(SqlSessionFactory sessions) {
        return new MyBatisUserQueryRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SqlSessionFactory.class)
    AuthorizationProfileQueryRepository iamAuthorizationProfileQueryRepository(SqlSessionFactory sessions) {
        return new MyBatisAuthorizationProfileQueryRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SqlSessionFactory.class)
    SessionQueryRepository iamSessionQueryRepository(SqlSessionFactory sessions) {
        return new MyBatisSessionQueryRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SqlSessionFactory.class)
    PermissionTemplateQueryRepository iamPermissionTemplateQueryRepository(SqlSessionFactory sessions) {
        return new MyBatisPermissionTemplateQueryRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SqlSessionFactory.class)
    AuditQueryRepository iamAuditQueryRepository(SqlSessionFactory sessions) {
        return new MyBatisAuditQueryRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SqlSessionFactory.class)
    OverviewRepository iamOverviewRepository(SqlSessionFactory sessions) {
        return new MyBatisOverviewRepository(sessions);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({UserQueryRepository.class, AuthorizationEngine.class})
    IamManagementUsersController iamManagementUsersController(AuthorizationEngine authorization,
                                                              UserQueryRepository users,
                                                              IdentityRepository identities) {
        return new IamManagementUsersController(authorization, users, identities);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PermissionTemplateQueryRepository.class, AuthorizationEngine.class})
    IamManagementTemplatesController iamManagementTemplatesController(
            AuthorizationEngine authorization,
            PermissionTemplateQueryRepository templates) {
        return new IamManagementTemplatesController(authorization, templates);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AuthorizationProfileQueryRepository.class, UserQueryRepository.class,
            AuthorizationProfileRepository.class, AuthorizationEngine.class})
    IamManagementProfilesController iamManagementProfilesController(
            AuthorizationEngine authorization,
            AuthorizationProfileQueryRepository query,
            AuthorizationProfileRepository profiles,
            UserQueryRepository users) {
        return new IamManagementProfilesController(authorization, query, profiles, users);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({SessionQueryRepository.class, UserQueryRepository.class, AuthorizationEngine.class})
    IamManagementSessionsController iamManagementSessionsController(
            AuthorizationEngine authorization,
            SessionQueryRepository sessions,
            UserQueryRepository users) {
        return new IamManagementSessionsController(authorization, sessions, users);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AuditQueryRepository.class, AuthorizationEngine.class})
    IamManagementAuditController iamManagementAuditController(
            AuthorizationEngine authorization,
            AuditQueryRepository audits) {
        return new IamManagementAuditController(authorization, audits);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({OverviewRepository.class, AuthorizationEngine.class})
    IamManagementOverviewController iamManagementOverviewController(
            AuthorizationEngine authorization,
            OverviewRepository overview) {
        return new IamManagementOverviewController(authorization, overview);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AuthorizationProfileRepository.class, PermissionTemplateVersionRepository.class})
    IamCapabilitiesController iamCapabilitiesController(
            AuthorizationProfileRepository profiles,
            PermissionTemplateVersionRepository versions) {
        return new IamCapabilitiesController(profiles, versions);
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
