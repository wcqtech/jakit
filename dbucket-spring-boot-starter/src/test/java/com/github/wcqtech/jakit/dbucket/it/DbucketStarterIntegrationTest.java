package com.github.wcqtech.jakit.dbucket.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.wcqtech.jakit.dbucket.BucketAcquireException;
import com.github.wcqtech.jakit.dbucket.BucketStore;
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.annotation.DBucket;
import com.github.wcqtech.jakit.dbucket.autoconfigure.DbucketAutoConfiguration;
import com.github.wcqtech.jakit.dbucket.store.Dialect;
import com.github.wcqtech.jakit.dbucket.store.JdbcBucketStore;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * Opt-in end-to-end test of the starter on real databases: properties to options, dialect detection
 * (including the MySQL UTC check and KingbaseES {@code database_mode}), embedded DDL, auto-creation,
 * the {@code @DBucket} gate and the admin facade.
 *
 * <p>Configuration comes from {@code dbucket-probe/probe.properties} (or
 * {@code -Ddbucket.it.config=<path>}); unreachable databases are skipped. KingbaseES targets need the
 * {@code -Pkingbase-it} profile for the driver.
 */
class DbucketStarterIntegrationTest {

    private static final String TABLE = "dbucket_starter_it_bucket";
    private static final String NAMESPACE = "it";
    private static final String BUCKET = "gate";

    @Test
    void starterWiresAgainstRealDatabases() throws Exception {
        List<Target> targets = loadTargets();
        Assumptions.assumeFalse(targets.isEmpty(),
                "no integration config found (set -Ddbucket.it.config=<path>)");

        List<String> failures = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Target target : targets) {
            DataSource dataSource;
            try {
                dataSource = dataSource(target);
                dropTable(dataSource);
            } catch (Exception e) {
                skipped.add(target.key() + " (unreachable: " + e.getMessage() + ")");
                continue;
            }
            try {
                verify(target, dataSource);
            } catch (AssertionError | Exception e) {
                failures.add(target.key() + " -> " + e);
            } finally {
                try {
                    dropTable(dataSource);
                } catch (Exception ignored) {
                    // cleanup is best effort
                }
            }
        }
        if (!skipped.isEmpty()) {
            System.out.println("skipped targets: " + String.join(", ", skipped));
        }
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    private void verify(Target target, DataSource dataSource) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DbucketAutoConfiguration.class))
                .withUserConfiguration(AopConfiguration.class)
                .withBean("dataSource", DataSource.class, () -> dataSource)
                .withPropertyValues(
                        "jakit.dbucket.table=" + TABLE,
                        "jakit.dbucket.namespace=" + NAMESPACE,
                        "jakit.dbucket.ddl.auto-init=true",
                        "jakit.dbucket.create.capacity=2",
                        "jakit.dbucket.create.rate=0",
                        "jakit.dbucket.wait.poll-interval=10ms")
                .run(context -> {
                    assertEquals(expectedDialect(target),
                            ((JdbcBucketStore) context.getBean(BucketStore.class)).dialectSql().dialect(),
                            target.key() + ": detected dialect");

                    Gate gate = context.getBean(Gate.class);

                    // first call auto-creates the bucket (capacity 2) and consumes one token
                    assertTrue(gate.attempt(), target.key() + ": first gated call");
                    assertTrue(gate.attempt(), target.key() + ": second gated call");

                    // rate 0 and capacity 2: the third call must be rejected by the aspect
                    BucketAcquireException rejection = assertThrows(BucketAcquireException.class,
                            gate::attempt, target.key() + ": third gated call");
                    assertEquals(NAMESPACE, rejection.getNamespace());
                    assertEquals(BUCKET, rejection.getName());

                    // facade and admin work on the same bucket
                    Dbucket dbucket = context.getBean(Dbucket.class);
                    assertEquals(0, dbucket.bucket(NAMESPACE, BUCKET).snapshot().orElseThrow()
                            .tokens().compareTo(BigDecimal.ZERO), target.key() + ": drained");
                    assertTrue(dbucket.admin().delete(NAMESPACE, BUCKET), target.key() + ": delete");
                });
    }

    private static Dialect expectedDialect(Target target) {
        if (target.key().startsWith("kingbase")) {
            return Dialect.KINGBASE;
        }
        return "mysql".equals(target.profile()) ? Dialect.MYSQL : Dialect.POSTGRESQL;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class AopConfiguration {

        @Bean
        Gate gate() {
            return new Gate();
        }
    }

    /** Gated service: one token per call from the configured default namespace. */
    public static class Gate {

        @DBucket(BUCKET)
        public boolean attempt() {
            return true;
        }
    }

    private static DataSource dataSource(Target target) throws Exception {
        Driver driver = (Driver) Class.forName(target.driver()).getDeclaredConstructor().newInstance();
        return new SimpleDriverDataSource(driver, target.url(), target.user(), target.password());
    }

    private static void dropTable(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + TABLE);
        }
    }

    private record Target(String key, String url, String user, String password, String driver,
                          String profile) {
    }

    private static List<Target> loadTargets() throws Exception {
        Path config = locateConfig();
        if (config == null) {
            return List.of();
        }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(config)) {
            properties.load(reader);
        }
        List<Target> targets = new ArrayList<>();
        for (String key : List.of("mysql", "pg", "kingbase-mysql", "kingbase-pg")) {
            String url = properties.getProperty(key + ".url");
            if (url == null || url.isBlank()) {
                continue;
            }
            targets.add(new Target(key, url.trim(),
                    properties.getProperty(key + ".user", "").trim(),
                    properties.getProperty(key + ".password", ""),
                    properties.getProperty(key + ".driver", "").trim(),
                    properties.getProperty(key + ".profile", "pg").trim().toLowerCase()));
        }
        return targets;
    }

    private static Path locateConfig() {
        String configured = System.getProperty("dbucket.it.config");
        List<Path> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(Path.of(configured));
            candidates.add(Path.of("..", configured));
        }
        candidates.add(Path.of("..", "dbucket-probe", "probe.properties"));
        candidates.add(Path.of("dbucket-probe", "probe.properties"));
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }
}
