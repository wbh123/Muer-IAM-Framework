package io.github.iamstarter.example;

import io.github.iamstarter.autoconfigure.IamBearerTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class ExampleSecurityConfiguration {
    @Bean
    @Order(100)
    SecurityFilterChain exampleSecurityFilterChain(HttpSecurity http, IamBearerTokenFilter bearerFilter)
            throws Exception {
        return http
                .securityMatcher("/example/**", "/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, failure) -> response.sendError(401)))
                .addFilterBefore(bearerFilter, AnonymousAuthenticationFilter.class)
                .build();
    }
}
