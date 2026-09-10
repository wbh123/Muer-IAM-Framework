package cloud.muer.acceptance;

import cloud.muer.authentication.IdentityAuthenticator;
import cloud.muer.authentication.LoginRequest;
import cloud.muer.core.model.IamPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration(proxyBeanMethods = false)
public class AcceptanceIdentityConfiguration {
    @Bean
    IdentityAuthenticator acceptanceIdentityAuthenticator() {
        return (LoginRequest request) -> {
            if (!"acceptance-user".equals(request.username()) || !"acceptance-pass".equals(request.password())) {
                return Optional.empty();
            }
            return Optional.of(new IamPrincipal(
                    701L, "acceptance-identity", "ACCEPTANCE", 701L, 701L,
                    request.clientType(), 1L));
        };
    }
}
