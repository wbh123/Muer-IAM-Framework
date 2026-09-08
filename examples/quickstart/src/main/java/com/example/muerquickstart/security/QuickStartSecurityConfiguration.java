package com.example.muerquickstart.security;

import cloud.muer.autoconfigure.IamBearerTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Secures the host's own {@code /api/**} endpoints.
 *
 * <p>Muer auto-configures the {@code /iam/**} chain for you (login is open,
 * everything else under {@code /iam/**} is authenticated via the bearer token).
 * Your own business routes live outside {@code /iam/**}, so you declare a
 * chain for them here: every {@code /api/**} call must carry a valid Muer Bearer
 * token, which {@link IamBearerTokenFilter} turns into a Spring Security
 * authentication. {@code @RequirePermission} then does the fine-grained
 * authorization on top.</p>
 */
@Configuration(proxyBeanMethods = false)
public class QuickStartSecurityConfiguration {

    @Bean
    @Order(100)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, IamBearerTokenFilter bearerFilter)
            throws Exception {
        return http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, failure) -> response.sendError(401)))
                .addFilterBefore(bearerFilter, AnonymousAuthenticationFilter.class)
                .build();
    }
}
