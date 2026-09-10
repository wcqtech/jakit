package com.github.wcqtech.jakit.dbucket.autoconfigure;

import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.DbucketAdmin;
import com.github.wcqtech.jakit.dbucket.BucketStore;
import com.github.wcqtech.jakit.dbucket.aop.DbucketAspect;
import com.github.wcqtech.jakit.dbucket.store.DialectSql;
import com.github.wcqtech.jakit.dbucket.store.JdbcBucketStore;
import com.github.wcqtech.jakit.dbucket.store.JdbcDialectResolver;
import javax.sql.DataSource;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Auto-configures the dbucket storage, facade and {@code @DBucket} aspect.
 *
 * <p>Wiring rules:
 *
 * <ul>
 *   <li>A user provided {@link BucketStore} bean (the SPI) disables the JDBC store entirely.</li>
 *   <li>The dialect comes from {@code jakit.dbucket.dialect} ({@code auto} detects it from the
 *       connection) and the MySQL UTC session check always runs during detection.</li>
 *   <li>{@code jakit.dbucket.ddl.auto-init} runs the embedded DDL, {@code verify-on-startup} pings
 *       once.</li>
 * </ul>
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "jakit.dbucket", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(DbucketProperties.class)
public class DbucketAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BucketStore bucketStore(DbucketProperties properties, ApplicationContext context,
                                   ObjectProvider<DataSource> dataSources) {
        DataSource dataSource = resolveDataSource(properties, context, dataSources);
        DialectSql dialectSql = resolveDialectSql(properties, dataSource);
        JdbcBucketStore store = JdbcBucketStore.of(dataSource, dialectSql);
        if (properties.getDdl().isAutoInit()) {
            store.createTable();
        }
        if (properties.isVerifyOnStartup()) {
            store.ping();
        }
        return store;
    }

    @Bean
    @ConditionalOnMissingBean
    public Dbucket dbucket(DbucketProperties properties, BucketStore store) {
        return Dbucket.create(store, DbucketOptionsFactory.toOptions(properties));
    }

    @Bean
    @ConditionalOnMissingBean
    public DbucketAdmin dbucketAdmin(Dbucket dbucket) {
        return dbucket.admin();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "jakit.dbucket.annotations", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public DbucketAspect dbucketAspect(Dbucket dbucket, DbucketProperties properties) {
        return new DbucketAspect(dbucket, properties.getAnnotations().getOrder());
    }

    /**
     * Resolves the datasource: an explicit bean name wins, otherwise the single datasource is used
     * (honouring {@code @Primary} when several exist).
     */
    static DataSource resolveDataSource(DbucketProperties properties, ApplicationContext context,
                                        ObjectProvider<DataSource> dataSources) {
        String beanName = properties.getDatasourceBeanName();
        if (StringUtils.hasText(beanName)) {
            try {
                return context.getBean(beanName.trim(), DataSource.class);
            } catch (NoSuchBeanDefinitionException e) {
                throw new IllegalStateException("jakit.dbucket.datasource-bean-name=" + beanName.trim()
                        + " does not match a DataSource bean", e);
            }
        }
        DataSource unique = dataSources.getIfUnique();
        if (unique != null) {
            return unique;
        }
        try {
            return context.getBean(DataSource.class);
        } catch (NoUniqueBeanDefinitionException e) {
            throw new IllegalStateException("jakit.dbucket needs a single DataSource but found "
                    + e.getBeanNamesFound()
                    + "; set jakit.dbucket.datasource-bean-name to the one to use", e);
        }
    }

    static DialectSql resolveDialectSql(DbucketProperties properties, DataSource dataSource) {
        DialectSettings settings = DialectSettings.parse(properties);
        if (settings.dialect() == null) {
            return JdbcDialectResolver.resolve(dataSource, properties.getTable());
        }
        return JdbcDialectResolver.resolve(dataSource, properties.getTable(), settings.dialect(),
                settings.kingbaseMode());
    }
}
