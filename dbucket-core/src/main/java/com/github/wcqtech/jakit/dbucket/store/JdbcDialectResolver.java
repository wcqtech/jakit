package com.github.wcqtech.jakit.dbucket.store;

import com.github.wcqtech.jakit.dbucket.DbucketStoreException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Resolves the {@link DialectSql} profile of a datasource.
 *
 * <p>Resolution is meant to run once at startup:
 *
 * <ol>
 *   <li>The product name, falling back to the JDBC URL, picks the {@link Dialect} family.</li>
 *   <li>KingbaseES additionally needs its compatibility mode, read with {@code SHOW database_mode}.</li>
 *   <li>MySQL is verified to run with a UTC session, because bucket math and {@code lastRefill} mapping
 *       assume UTC wall-clock values. The check fails fast with the exact connection setting to add,
 *       instead of silently shifting timestamps.</li>
 * </ol>
 *
 * <p>A database that cannot be mapped (H2, MariaDB, KingbaseES in Oracle mode, ...) raises
 * {@link DbucketStoreException} rather than guessing.
 */
public final class JdbcDialectResolver {

    private static final String KINGBASE_MODE_SQL = "SHOW database_mode";

    /** Seconds between the session wall clock and UTC; {@code 0} means the session is already UTC. */
    private static final String MYSQL_UTC_CHECK_SQL =
            "SELECT ABS(TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), NOW()))";

    private JdbcDialectResolver() {
    }

    /**
     * Detects and validates the dialect, including the KingbaseES compatibility mode and the MySQL
     * session time zone.
     */
    public static DialectSql resolve(DataSource dataSource, String table) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        try (Connection connection = dataSource.getConnection()) {
            Dialect dialect = detectDialect(connection);
            switch (dialect) {
                case MYSQL:
                    verifyMysqlUtcSession(connection);
                    return DialectSqls.mysql(table);
                case POSTGRESQL:
                    return DialectSqls.postgresql(table);
                default:
                    return DialectSqls.kingbase(table, detectKingbaseMode(connection));
            }
        } catch (SQLException e) {
            throw new DbucketStoreException("cannot resolve dbucket dialect: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves a profile from an explicit configuration, which takes precedence over detection.
     *
     * <p>A {@code null} {@code kingbaseMode} still triggers {@code SHOW database_mode}; MySQL sessions
     * are still verified to be UTC.
     */
    public static DialectSql resolve(DataSource dataSource, String table, Dialect dialect,
                                     KingbaseMode kingbaseMode) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        Objects.requireNonNull(dialect, "dialect must not be null");
        if (dialect == Dialect.KINGBASE) {
            return DialectSqls.kingbase(table,
                    kingbaseMode != null ? kingbaseMode : detectKingbaseMode(dataSource));
        }
        if (dialect == Dialect.MYSQL) {
            try (Connection connection = dataSource.getConnection()) {
                verifyMysqlUtcSession(connection);
            } catch (SQLException e) {
                throw new DbucketStoreException("cannot verify MySQL session time zone: "
                        + e.getMessage(), e);
            }
        }
        return DialectSqls.forDialect(dialect, table);
    }

    /** Detects only the dialect family, without validating anything else. */
    public static Dialect detectDialect(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        try (Connection connection = dataSource.getConnection()) {
            return detectDialect(connection);
        } catch (SQLException e) {
            throw new DbucketStoreException("cannot detect dbucket dialect: " + e.getMessage(), e);
        }
    }

    /** Reads the KingbaseES compatibility mode with {@code SHOW database_mode}. */
    public static KingbaseMode detectKingbaseMode(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        try (Connection connection = dataSource.getConnection()) {
            return detectKingbaseMode(connection);
        } catch (SQLException e) {
            throw new DbucketStoreException("cannot detect KingbaseES compatibility mode: "
                    + e.getMessage(), e);
        }
    }

    private static Dialect detectDialect(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String productName = metadata.getDatabaseProductName();
        Optional<Dialect> byProductName = Dialect.fromProductName(productName);
        if (byProductName.isPresent()) {
            return byProductName.get();
        }
        String url = metadata.getURL();
        return Dialect.fromJdbcUrl(url).orElseThrow(() -> new DbucketStoreException(
                "unsupported database: productName='" + productName + "', url='" + url
                        + "'; supported dialects are MySQL, PostgreSQL and KingbaseES,"
                        + " or configure the dialect explicitly"));
    }

    private static KingbaseMode detectKingbaseMode(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(KINGBASE_MODE_SQL)) {
            String mode = rs.next() ? rs.getString(1) : null;
            return KingbaseMode.fromDatabaseMode(mode).orElseThrow(() -> new DbucketStoreException(
                    "unsupported KingbaseES database_mode '" + mode
                            + "'; only 'pg' and 'mysql' are supported"));
        }
    }

    private static void verifyMysqlUtcSession(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(MYSQL_UTC_CHECK_SQL)) {
            long offsetSeconds = rs.next() ? rs.getLong(1) : -1;
            if (offsetSeconds != 0) {
                throw new DbucketStoreException("MySQL session time zone must be UTC but NOW() differs"
                        + " from UTC_TIMESTAMP() by " + offsetSeconds + "s; add"
                        + " '?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' to the JDBC"
                        + " URL, because bucket math and lastRefill mapping assume UTC wall-clock values");
            }
        }
    }
}
