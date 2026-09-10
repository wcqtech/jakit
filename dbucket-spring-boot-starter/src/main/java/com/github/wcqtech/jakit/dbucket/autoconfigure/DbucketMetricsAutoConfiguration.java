package com.github.wcqtech.jakit.dbucket.autoconfigure;

import com.github.wcqtech.jakit.dbucket.BucketStore;
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.metrics.MeteredDbucket;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wraps the {@link Dbucket} facade with Micrometer meters when {@code jakit.dbucket.metrics.enabled}
 * is set and Micrometer is on the classpath.
 *
 * <p>Runs before {@link DbucketAutoConfiguration} so its {@code @ConditionalOnMissingBean(Dbucket.class)}
 * wins and the plain facade backs off.
 */
@AutoConfiguration(before = DbucketAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "jakit.dbucket.metrics", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DbucketProperties.class)
public class DbucketMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Dbucket.class)
    public Dbucket meteredDbucket(DbucketProperties properties, BucketStore store,
                                  MeterRegistry registry) {
        return MeteredDbucket.wrap(Dbucket.create(store, DbucketOptionsFactory.toOptions(properties)),
                registry);
    }
}
