package com.github.wcqtech.jakit.dbucket.store;

import com.github.wcqtech.jakit.dbucket.BucketSnapshot;
import com.github.wcqtech.jakit.dbucket.BucketSpec;
import com.github.wcqtech.jakit.dbucket.BucketStore;
import com.github.wcqtech.jakit.dbucket.ConsumeResult;
import com.github.wcqtech.jakit.dbucket.CreateResult;
import com.github.wcqtech.jakit.dbucket.DbucketStoreException;
import com.github.wcqtech.jakit.dbucket.internal.Values;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator;

/**
 * {@link BucketStore} implementation on top of a {@link DataSource} and a {@link DialectSql} profile.
 *
 * <p>Each operation runs as one atomic statement on its own connection:
 *
 * <ul>
 *   <li>Connections are taken directly from the {@link DataSource} and never through Spring's
 *       transaction-aware lookup, so a store operation cannot join a caller managed transaction. The
 *       row lock therefore lives only as long as the statement, not as long as the business
 *       transaction (D5).</li>
 *   <li>Lazy refill, the guard and the balance update happen inside a single SQL statement, using the
 *       database clock only.</li>
 *   <li>Failures are translated through {@link SQLStateSQLExceptionTranslator} and rethrown as
 *       {@link DbucketStoreException}; nothing is retried, so consuming stays at-most-once.</li>
 * </ul>
 *
 * <p>When {@code withRemaining} is requested and the dialect has no {@code RETURNING}, the consume is
 * wrapped in a short local transaction (update then read the stored balance) so the row lock is held
 * for exactly those two statements. This is the only place where a transaction is opened.
 */
public final class JdbcBucketStore implements BucketStore {

    private final DataSource dataSource;
    private final DialectSql sql;
    private final SQLExceptionTranslator translator = new SQLStateSQLExceptionTranslator();

    /**
     * @param dataSource datasource used for every statement; never a caller managed connection
     * @param sql        dialect profile, either detected with {@link JdbcDialectResolver} or built with
     *                   {@link DialectSqls}
     */
    public JdbcBucketStore(DataSource dataSource, DialectSql sql) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.sql = Objects.requireNonNull(sql, "sql must not be null");
    }

    /**
     * Detects the dialect, validates the session configuration and returns a store for the default
     * table. This is the recommended entry point: detection happens once at startup and fails fast
     * with an actionable message.
     */
    public static JdbcBucketStore detect(DataSource dataSource) {
        return detect(dataSource, DialectSql.DEFAULT_TABLE);
    }

    /** Detects the dialect for a custom table name; see {@link #detect(DataSource)}. */
    public static JdbcBucketStore detect(DataSource dataSource, String table) {
        return new JdbcBucketStore(dataSource, JdbcDialectResolver.resolve(dataSource, table));
    }

    /** Creates a store for an explicitly chosen profile, skipping dialect detection. */
    public static JdbcBucketStore of(DataSource dataSource, DialectSql sql) {
        return new JdbcBucketStore(dataSource, sql);
    }

    /** The dialect profile backing this store. */
    public DialectSql dialectSql() {
        return sql;
    }

    /** Dialect profile name, for logs, for example {@code kingbase(mysql)}. */
    public String dialectName() {
        return sql.dialectName();
    }

    /** Bucket table name. */
    public String tableName() {
        return sql.tableName();
    }

    // ------------------------------------------------------------------ SPI

    @Override
    public CreateResult createIfAbsent(BucketSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        return inConnection("create or read bucket", connection -> {
            Optional<BucketSnapshot> existing = read(connection, spec.namespace(), spec.name());
            if (existing.isPresent()) {
                return CreateResult.exists(existing.get());
            }
            try (PreparedStatement statement = connection.prepareStatement(sql.createIfAbsent())) {
                bind(statement, spec.namespace(), spec.name(), spec.capacity(), spec.rate(),
                        spec.initialTokens());
                statement.executeUpdate();
            }
            return CreateResult.created();
        });
    }

    @Override
    public Optional<BucketSnapshot> get(String namespace, String name) {
        return inConnection("read bucket", connection -> read(connection, namespace, name));
    }

    @Override
    public ConsumeResult tryConsume(String namespace, String name, long tokens, boolean withRemaining) {
        requirePositive(tokens, "tokens");
        if (!withRemaining) {
            return inConnection("consume tokens", connection ->
                    outcome(connection, namespace, name,
                            executeUpdate(connection, sql.tryConsume(), tokens, namespace, name, tokens)));
        }
        if (sql.supportsReturning()) {
            return inConnection("consume tokens with remaining", connection ->
                    consumeReturning(connection, namespace, name, tokens));
        }
        return inConnection("consume tokens with remaining", connection ->
                consumeWithLocalTransaction(connection, namespace, name, tokens));
    }

    @Override
    public boolean deposit(String namespace, String name, long tokens) {
        requirePositive(tokens, "tokens");
        return inConnection("deposit tokens", connection -> {
            int affected = executeUpdate(connection, sql.deposit(), tokens, namespace, name);
            return affected == 1 || exists(connection, namespace, name);
        });
    }

    @Override
    public boolean adjustCapacity(String namespace, String name, BigDecimal capacity) {
        BigDecimal value = Values.requireDecimal("capacity", capacity, false);
        return inConnection("adjust capacity", connection -> {
            int affected = executeUpdate(connection, sql.adjustCapacity(),
                    value, value, namespace, name);
            return affected == 1 || exists(connection, namespace, name);
        });
    }

    @Override
    public boolean adjustRate(String namespace, String name, BigDecimal rate) {
        BigDecimal value = Values.requireDecimal("rate", rate, true);
        return inConnection("adjust rate", connection -> {
            int affected = executeUpdate(connection, sql.adjustRate(), value, namespace, name);
            return affected == 1 || exists(connection, namespace, name);
        });
    }

    @Override
    public boolean delete(String namespace, String name) {
        return inConnection("delete bucket", connection ->
                executeUpdate(connection, sql.delete(), namespace, name) == 1);
    }

    // ----------------------------------------------------------- operations

    /** Connectivity probe for startup self checks; throws {@link DbucketStoreException} when down. */
    public void ping() {
        inConnection("ping", connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(sql.ping())) {
                return rs.next();
            }
        });
    }

    /** Idempotent DDL execution, used by the starter's optional {@code ddl.auto-init}. */
    public void createTable() {
        inConnection("create bucket table", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql.createTable());
                return null;
            }
        });
    }

    // -------------------------------------------------------------- helpers

    private Optional<BucketSnapshot> read(Connection connection, String namespace, String name)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql.get())) {
            bind(statement, namespace, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private BucketSnapshot map(ResultSet rs) throws SQLException {
        // Zoned columns carry an instant; unzoned ones store UTC wall clock, which the dialect only
        // guarantees when the session time zone is pinned to UTC (validated during detection).
        Instant lastRefill = sql.zonedLastRefill()
                ? rs.getTimestamp("last_refill").toInstant()
                : rs.getObject("last_refill", LocalDateTime.class).toInstant(ZoneOffset.UTC);
        return new BucketSnapshot(
                rs.getString("namespace"),
                rs.getString("name"),
                rs.getBigDecimal("capacity"),
                rs.getBigDecimal("rate"),
                rs.getBigDecimal("effective_tokens"),
                rs.getBigDecimal("stored_tokens"),
                lastRefill);
    }

    private ConsumeResult consumeReturning(Connection connection, String namespace, String name,
                                           long tokens) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql.tryConsumeReturning())) {
            bind(statement, tokens, namespace, name, tokens);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return ConsumeResult.success(rs.getBigDecimal(1));
                }
                return outcome(connection, namespace, name, 0);
            }
        }
    }

    private ConsumeResult consumeWithLocalTransaction(Connection connection, String namespace,
                                                      String name, long tokens) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int affected = executeUpdate(connection, sql.tryConsume(), tokens, namespace, name, tokens);
            if (affected == 1) {
                BigDecimal remaining = storedTokens(connection, namespace, name);
                connection.commit();
                return ConsumeResult.success(remaining);
            }
            connection.rollback();
            return outcome(connection, namespace, name, 0);
        } catch (SQLException e) {
            rollbackQuietly(connection);
            throw e;
        } finally {
            restoreAutoCommit(connection, autoCommit);
        }
    }

    private ConsumeResult outcome(Connection connection, String namespace, String name, int affected)
            throws SQLException {
        if (affected == 1) {
            return ConsumeResult.success();
        }
        return exists(connection, namespace, name)
                ? ConsumeResult.insufficient()
                : ConsumeResult.notFound();
    }

    private boolean exists(Connection connection, String namespace, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql.exists())) {
            bind(statement, namespace, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private BigDecimal storedTokens(Connection connection, String namespace, String name)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql.get())) {
            bind(statement, namespace, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("stored_tokens") : null;
            }
        }
    }

    private <T> T inConnection(String action, SqlCall<T> call) {
        try (Connection connection = open()) {
            return call.execute(connection);
        } catch (SQLException e) {
            throw translate(action, e);
        }
    }

    /**
     * Deliberately avoids {@code DataSourceUtils.getConnection}: joining the caller's transaction would
     * keep the bucket row lock until that transaction ends.
     */
    private Connection open() throws SQLException {
        Connection connection = dataSource.getConnection();
        if (!connection.getAutoCommit()) {
            connection.setAutoCommit(true);
        }
        return connection;
    }

    private DbucketStoreException translate(String action, SQLException e) {
        DataAccessException translated = translator.translate(action, null, e);
        String message = "dbucket " + action + " failed: "
                + (translated != null ? translated.getMessage() : e.getMessage());
        return new DbucketStoreException(message, translated != null ? translated : e);
    }

    private static int executeUpdate(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            return statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be > 0 but was " + value);
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // the original failure is the one worth reporting
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // pool returned connections are reset anyway
        }
    }

    @FunctionalInterface
    private interface SqlCall<T> {

        T execute(Connection connection) throws SQLException;
    }
}
