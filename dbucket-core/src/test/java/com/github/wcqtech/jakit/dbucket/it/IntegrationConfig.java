package com.github.wcqtech.jakit.dbucket.it;

import com.github.wcqtech.jakit.dbucket.store.Dialect;
import com.github.wcqtech.jakit.dbucket.store.DialectSql;
import com.github.wcqtech.jakit.dbucket.store.DialectSqls;
import com.github.wcqtech.jakit.dbucket.store.KingbaseMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * Shared configuration for the opt-in integration tests.
 *
 * <p>It reads the same property file as {@code dbucket-probe/probe.properties}
 * ({@code <key>.url/user/password/driver/profile}) and falls back to
 * {@code -Ddbucket.it.config=<path>} when given. Tests skip themselves when nothing is configured, so
 * a normal build never needs credentials.
 */
public final class IntegrationConfig {

    private IntegrationConfig() {
    }

    public record Target(String key, String url, String user, String password, String driver,
                         String profile) {
    }

    public static List<Target> targets() {
        Path config = locate();
        if (config == null) {
            return List.of();
        }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(config)) {
            properties.load(reader);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read integration config " + config, e);
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

    /** A datasource for the target, suitable for {@code JdbcBucketStore.detect}. */
    public static DataSource dataSource(Target target) throws Exception {
        Driver driver = (Driver) Class.forName(target.driver()).getDeclaredConstructor().newInstance();
        return new SimpleDriverDataSource(driver, target.url(), target.user(), target.password());
    }

    /** Fragment profile for the target, derived from its key and profile. */
    public static DialectSql sqlFor(Target target, String table) {
        if (target.key().startsWith("kingbase")) {
            return DialectSqls.kingbase(table, kingbaseMode(target));
        }
        return "mysql".equals(target.profile())
                ? DialectSqls.mysql(table)
                : DialectSqls.postgresql(table);
    }

    /** Dialect the target must resolve to. */
    public static Dialect expectedDialect(Target target) {
        if (target.key().startsWith("kingbase")) {
            return Dialect.KINGBASE;
        }
        return "mysql".equals(target.profile()) ? Dialect.MYSQL : Dialect.POSTGRESQL;
    }

    private static KingbaseMode kingbaseMode(Target target) {
        return target.profile().contains("mysql") ? KingbaseMode.MYSQL : KingbaseMode.PG;
    }

    private static Path locate() {
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
