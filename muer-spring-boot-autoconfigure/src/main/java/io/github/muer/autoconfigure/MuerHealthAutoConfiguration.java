package io.github.muer.autoconfigure;

import io.github.muer.autoconfigure.observability.MuerHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

/** Optional framework availability health contribution with no datastore probes. */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
public class MuerHealthAutoConfiguration {
    @Bean(name = "muerHealthIndicator")
    @ConditionalOnMissingBean(name = "muerHealthIndicator")
    HealthIndicator muerHealthIndicator() {
        return new MuerHealthIndicator();
    }
}
