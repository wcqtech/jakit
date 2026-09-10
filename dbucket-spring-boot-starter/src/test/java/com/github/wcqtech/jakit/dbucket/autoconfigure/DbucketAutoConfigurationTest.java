package com.github.wcqtech.jakit.dbucket.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.github.wcqtech.jakit.dbucket.BucketStore;
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.DbucketAdmin;
import com.github.wcqtech.jakit.dbucket.aop.DbucketAspect;
import com.github.wcqtech.jakit.dbucket.metrics.MeteredDbucket;
import com.github.wcqtech.jakit.dbucket.test.FakeBucketStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.Ordered;

class DbucketAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DbucketAutoConfiguration.class))
            .withBean("bucketStore", BucketStore.class, FakeBucketStore::new);

    @Test
    void wiresStoreFacadeAdminAndAspect() {
        runner.run(context -> {
            assertEquals(FakeBucketStore.class, context.getBean(BucketStore.class).getClass());
            assertNotNull(context.getBean(Dbucket.class));
            assertNotNull(context.getBean(DbucketAdmin.class));
            assertEquals(Ordered.LOWEST_PRECEDENCE - 100, context.getBean(DbucketAspect.class).getOrder());
        });
    }

    @Test
    void backsOffCompletelyWhenDisabled() {
        runner.withPropertyValues("jakit.dbucket.enabled=false").run(context -> {
            assertFalse(context.containsBean("dbucket"));
            assertFalse(context.containsBean("dbucketAdmin"));
            assertFalse(context.containsBean("dbucketAspect"));
        });
    }

    @Test
    void aspectCanBeDisabled() {
        runner.withPropertyValues("jakit.dbucket.annotations.enabled=false").run(context ->
                assertFalse(context.containsBean("dbucketAspect")));
    }

    @Test
    void adviceOrderIsConfigurable() {
        runner.withPropertyValues("jakit.dbucket.annotations.order=42").run(context ->
                assertEquals(42, context.getBean(DbucketAspect.class).getOrder()));
    }

    @Test
    void metricsDecoratorTakesOverWhenEnabled() {
        runner.withConfiguration(AutoConfigurations.of(DbucketMetricsAutoConfiguration.class))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("jakit.dbucket.metrics.enabled=true")
                .run(context -> {
                    Dbucket dbucket = context.getBean(Dbucket.class);
                    assertTrue(dbucket instanceof MeteredDbucket);
                    dbucket.bucket("orders").tryAcquire(1);
                    assertNotNull(context.getBean(MeterRegistry.class).find("dbucket.acquire").counter());
                });
    }

    @Test
    void facadeStaysPlainWithoutMetrics() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> assertFalse(context.getBean(Dbucket.class) instanceof MeteredDbucket));
    }

    @Test
    void failsClearlyWhenSeveralDataSourcesArePresent() {
        newApplicationContextRunnerWithoutStore()
                .withBean("firstDataSource", DataSource.class, () -> mock(DataSource.class))
                .withBean("secondDataSource", DataSource.class, () -> mock(DataSource.class))
                .run(context -> {
                    String message = startupFailureMessage(context.getStartupFailure());
                    assertTrue(message.contains("datasource-bean-name"), message);
                });
    }

    @Test
    void failsClearlyWhenTheNamedDataSourceIsMissing() {
        newApplicationContextRunnerWithoutStore()
                .withBean("onlyDataSource", DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("jakit.dbucket.datasource-bean-name=missing")
                .run(context -> {
                    String message = startupFailureMessage(context.getStartupFailure());
                    assertTrue(message.contains("missing"), message);
                });
    }

    private static ApplicationContextRunner newApplicationContextRunnerWithoutStore() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DbucketAutoConfiguration.class));
    }

    private static String startupFailureMessage(Throwable failure) {
        assertNotNull(failure, "expected the context to fail");
        StringBuilder message = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            message.append(current.getMessage()).append(" | ");
        }
        return message.toString();
    }
}
