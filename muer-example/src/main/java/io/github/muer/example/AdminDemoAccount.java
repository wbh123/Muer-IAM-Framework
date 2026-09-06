package io.github.muer.example;

import io.github.muer.core.model.IamPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Supplies the IAM Admin Console demo identity to the {@link ExampleIdentityAdapter}.
 * Exists only when the dev profile and {@code iam.example.seed-admin=true} are both
 * active, so the demo account can never authenticate in a production-like setup.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "muer.example", name = "seed-admin", havingValue = "true")
final class AdminDemoAccount {
    AppUser user() {
        return new AppUser(AdminDemoSeedConstants.USER_ID, AdminDemoSeedConstants.USERNAME,
                AdminDemoSeedConstants.PASSWORD, true, AdminDemoSeedConstants.DISPLAY_NAME);
    }

    IamPrincipal principal(String clientType) {
        return new IamPrincipal(AdminDemoSeedConstants.USER_ID, AdminDemoSeedConstants.IDENTITY_ID,
                AdminDemoSeedConstants.IDENTITY_DOMAIN, AdminDemoSeedConstants.PROFILE_ID,
                AdminDemoSeedConstants.TEMPLATE_VERSION_ID, clientType, 1L);
    }
}
