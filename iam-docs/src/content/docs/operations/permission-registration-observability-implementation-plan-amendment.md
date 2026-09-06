# Muer Product Closure Implementation Plan Amendment

This amendment is part of, and overrides the identified entries in,
`permission-registration-observability-implementation-plan.md`.

## Corrected document navigation target

Task 6 modifies the actual documentation configuration file:

```text
iam-docs/src/content.config.ts
```

It does not modify `iam-docs/src/content/config.ts`.

## Corrected no-Micrometer test contract

The base auto-configuration exposes `MuerMetrics` as the contract bean. When
no `MeterRegistry` is available, the test must assert that it is the no-op
instance; it must not require a separately registered bean whose declared type
is `NoOpMuerMetrics`.

```java
@Test
void noMeterRegistryUsesNoOpMetrics() {
    runner.run(context -> assertThat(context.getBean(MuerMetrics.class))
            .isSameAs(NoOpMuerMetrics.INSTANCE));
}
```

The no-Actuator check filters `HealthIndicator` from the classpath and asserts
that the application context starts without a `muerHealthIndicator` bean.

```java
@Test
void noActuatorClasspathStartsWithoutHealthContributor() {
    runner.withClassLoader(new FilteredClassLoader(
                    "org.springframework.boot.health.contributor.HealthIndicator"))
            .run(context -> assertThat(context).doesNotHaveBean("muerHealthIndicator"));
}
```
