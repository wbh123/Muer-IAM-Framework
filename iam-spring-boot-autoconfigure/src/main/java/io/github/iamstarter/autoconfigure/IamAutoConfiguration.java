package io.github.iamstarter.autoconfigure;

import io.github.iamstarter.authentication.AuthenticationService;
import io.github.iamstarter.authentication.IdentityAuthenticator;
import io.github.iamstarter.authentication.AuthorizationProfileSwitchService;
import io.github.iamstarter.authentication.AccountGovernanceService;
import io.github.iamstarter.audit.AuditQueryRepository;
import io.github.iamstarter.authorization.AuthorizationEngine;
import io.github.iamstarter.authorization.AuthorizationPolicy;
import io.github.iamstarter.authorization.AuthorizationProfileQueryRepository;
import io.github.iamstarter.authorization.AuthorizationProfileRepository;
import io.github.iamstarter.authorization.AuthorizationVersionRepository;
import io.github.iamstarter.authorization.DefaultAuthorizationEngine;
import io.github.iamstarter.authorization.PermissionTemplateQueryRepository;
import io.github.iamstarter.authorization.PermissionTemplateVersionRepository;
import io.github.iamstarter.authorization.AuthorizationProfileService;
import io.github.iamstarter.authorization.AuthorizationScopeMutation;
import io.github.iamstarter.authorization.AuthorizationVersionService;
import io.github.iamstarter.authorization.PermissionTemplateService;
import io.github.iamstarter.core.port.ResourceHierarchyProvider;
import io.github.iamstarter.core.port.IamUserRepository;
import io.github.iamstarter.core.port.IdentityRepository;
import io.github.iamstarter.core.port.OverviewRepository;
import io.github.iamstarter.core.port.UserQueryRepository;
import io.github.iamstarter.diagnostics.AuthorizationDiagnosticsService;
import io.github.iamstarter.session.SessionQueryRepository;
import io.github.iamstarter.session.SessionRepository;
import io.github.iamstarter.session.SessionService;
import io.github.iamstarter.session.TokenStore;
import io.github.iamstarter.session.LoginEventRepository;
import io.github.iamstarter.web.IamAdministrationController;
import io.github.iamstarter.web.IamAuthenticationController;
import io.github.iamstarter.web.IamAuthorizationController;
import io.github.iamstarter.web.IamCapabilitiesController;
import io.github.iamstarter.web.IamManagementAuditController;
import io.github.iamstarter.web.IamManagementOverviewController;
import io.github.iamstarter.web.IamManagementProfilesController;
import io.github.iamstarter.web.IamManagementSessionsController;
import io.github.iamstarter.web.IamManagementTemplatesController;
import io.github.iamstarter.web.IamManagementUsersController;
import io.github.iamstarter.web.IamSessionController;
import io.github.iamstarter.persistence.MyBatisAuthorizationProfileQueryRepository;
import io.github.iamstarter.persistence.MyBatisAuditQueryRepository;
import io.github.iamstarter.persistence.MyBatisOverviewRepository;
import io.github.iamstarter.persistence.MyBatisPermissionTemplateQueryRepository;
import io.github.iamstarter.persistence.MyBatisSessionQueryRepository;
import io.github.iamstarter.persistence.MyBatisUserQueryRepository;
import io.github.iamstarter.persistence.MyBatisAuthorizationProfileRepository;
import io.github.iamstarter.persistence.MyBatisAuthorizationScopeMutation;
import io.github.iamstarter.persistence.MyBatisAuthorizationVersionRepository;
import io.github.iamstarter.persistence.MyBatisPermissionTemplateVersionRepository;
import io.github.iamstarter.persistence.MyBatisSessionRepository;
import io.github.iamstarter.persistence.RedisTokenStore;
import io.github.iamstarter.persistence.MyBatisLoginEventRepository;
import io.github.iamstarter.persistence.MyBatisIamUserRepository;
import io.github.iamstarter.persistence.MyBatisIdentityRepository;
import io.github.iamstarter.autoconfigure.web.IamAuthorizationInterceptor;
import io.github.iamstarter.autoconfigure.web.IamAuthorizationWebMvcConfiguration;
import io.github.iamstarter.autoconfigure.web.IamAuthorizationFailureHandler;
import io.github.iamstarter.autoconfigure.web.MvcResourceDescriptorResolver;
import io.github.iamstarter.autoconfigure.web.ProblemDetailIamAuthorizationFailureHandler;
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

@AutoConfiguration
@AutoConfigureAfter(name = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@EnableConfigurationProperties(IamProperties.class)
@ConditionalOnProperty(prefix = "iam", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IamAutoConfiguration {
    private static final java.util.List<String> MAPPER_RESOURCES = java.util.List.of(
            "mapper/iam/IamSessionMapper.xml",
            "mapper/iam/IamUserMapper.xml",
            "mapper/iam/IamIdentityMapper.xml",
            "mapper/iam/IamLoginEventMapper.xml",
            "mapper/iam/IamAuthorizationVersionMapper.xml",
            "mapper/iam/IamAuthorizationProfileMapper.xml",
            "mapper/iam/IamPermissionTemplateVersionMapper.xml",
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
    @ConditionalOnProperty(prefix = "iam.schema", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(IamSchemaMigrator.class)
    IamSchemaMigrator iamSchemaMigrator(DataSource dataSource, IamProperties properties) {
        return new IamSchemaMigrator(dataSource, properties.getSchema().getHistoryTable());
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
    TokenStore iamTokenStore(StringRedisTemplate redis, IamProperties properties) {
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
                                                   IamProperties properties) {
        var allowedClientTypes = Set.copyOf(properties.getClientTypes());
        var hostAuthenticator = authenticator.getIfAvailable(() -> request -> Optional.empty());
        IdentityAuthenticator configuredAuthenticator = request -> allowedClientTypes.contains(request.clientType())
                ? hostAuthenticator.authenticate(request)
                : Optional.empty();
        return new AuthenticationService(tokens, sessions,
                loginEvents.getIfAvailable(() -> event -> { }), versions::currentVersion,
                configuredAuthenticator, Clock.systemUTC(),
                properties.getToken().getTtl(), properties.getSession().getTouchInterval(),
                IamAutoConfiguration::randomId,
                IamAutoConfiguration::randomId, IamAutoConfiguration::randomId);
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
                                                          AuthorizationProfileSwitchService switches,
                                                          AuthorizationEngine authorization) {
        return new IamAuthorizationController(diagnostics, profiles, switches, authorization);
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
