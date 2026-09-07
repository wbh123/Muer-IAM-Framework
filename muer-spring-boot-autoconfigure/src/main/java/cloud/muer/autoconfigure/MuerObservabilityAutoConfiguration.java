package io.github.muer.autoconfigure;

import io.github.muer.core.metrics.MuerMetrics;
import io.github.muer.core.metrics.NoOpMuerMetrics;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Optional operational instrumentation that remains inert without an observability runtime. */
@AutoConfiguration(before = MuerAutoConfiguration.class)
public class MuerObservabilityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(value = MuerMetrics.class, type = "io.micrometer.core.instrument.MeterRegistry")
    MuerMetrics noOpMuerMetrics() {
        return NoOpMuerMetrics.INSTANCE;
    }
}
