package cloud.muer.autoconfigure;

import cloud.muer.authentication.AuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public final class IamBearerTokenFilter extends OncePerRequestFilter {
    private static final String PREFIX = "Bearer ";
    private final AuthenticationService authentication;

    public IamBearerTokenFilter(AuthenticationService authentication) {
        this.authentication = Objects.requireNonNull(authentication, "authentication must not be null");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            bearerToken(request).flatMap(authentication::resolve).ifPresent(principal -> {
                var authenticated = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authenticated);
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }

    private java.util.Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)) return java.util.Optional.empty();
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(token);
    }
}
