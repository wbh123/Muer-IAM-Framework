package com.example.muerquickstart.account;

import cloud.muer.authentication.IdentityAuthenticator;
import cloud.muer.authentication.LoginRequest;
import cloud.muer.core.model.IamPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * The single place where Muer learns "who is asking to log in".
 *
 * <p>Muer never owns your users. You verify the credential against your own
 * user source and, on success, project a {@link IamPrincipal}. Note the
 * authenticator does <strong>not</strong> create Profiles: it only returns the
 * current authorization projection (which Profile / Template Version is active
 * for this user right now). The Profile / Template / Scope rows themselves are
 * created by {@code QuickStartAuthorizationSeeder} (dev only) or, in a managed
 * environment, through the Admin Console / Management API.</p>
 *
 * <p>When you already have a {@code UserService}, replace {@code
 * DemoAccountService} with it here and keep everything else unchanged.</p>
 */
@Configuration(proxyBeanMethods = false)
public class DemoIdentityAuthenticator {

    @Bean
    IdentityAuthenticator demoIdentityAuthenticator(DemoAccountService accounts) {
        return (LoginRequest request) -> accounts
                .findByUsername(request.username())
                .filter(account -> account.password().equals(request.password()))
                .map(account -> new IamPrincipal(
                        account.id(),
                        "identity-" + account.username(),
                        account.identityDomain(),
                        account.activeProfileId(),
                        account.templateVersionId(),
                        request.clientType(),
                        account.authorizationVersion()));
    }
}
