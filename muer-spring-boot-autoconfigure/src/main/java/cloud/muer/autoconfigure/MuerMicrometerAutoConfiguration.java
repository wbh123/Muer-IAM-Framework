package cloud.muer.autoconfigure;

import cloud.muer.autoconfigure.observability.MicrometerMuerMetrics;
import cloud.muer.core.metrics.MuerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Activates Micrometer integration only when the host supplies a registry. */
@AutoConfiguration(before = {MuerObservabilityAutoConfiguration.class, MuerAutoConfiguration.class})
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
public class MuerMicrometerAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(MuerMetrics.class)
    MuerMetrics micrometerMuerMetrics(MeterRegistry registry) {
        return new MicrometerMuerMetrics(registry);
    }
}
