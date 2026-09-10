package com.github.wcqtech.jakit.dbucket.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.wcqtech.jakit.dbucket.it.IntegrationConfig;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Opt-in integration test executing the generated fragments against real databases with plain JDBC.
 *
 * <p>It guards what unit tests cannot see: placeholder order, dialect syntax and the shared
 * accrue/guard/deposit behaviour, independently of {@link JdbcBucketStore}. Configure it through
 * {@link IntegrationConfig}; unreachable databases are reported as skipped so an ordinary build never
 * fails just because a local instance is stopped. Generated SQL problems, in contrast, fail the build.
 */
class DialectSqlIntegrationTest {

    private static final String TABLE = "dbucket_sql_it_bucket";
    private static final String NS = "it";
    private static final String NAME = "bucket";

    @Test
    void fragmentsBehaveOnRealDatabases() throws Exception {
        List<IntegrationConfig.Target> targets = IntegrationConfig.targets();
        Assumptions.assumeFalse(targets.isEmpty(),
                "no integration config found (set -Ddbucket.it.config=<path>)");

        List<String> failures = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (IntegrationConfig.Target target : targets) {
            DialectSql sql = IntegrationConfig.sqlFor(target, TABLE);
            Connection connection;
            try {
                connection = open(target);
            } catch (Exception e) {
                skipped.add(target.key() + " (unreachable: " + e.getMessage() + ")");
                continue;
            }
            try (connection) {
                verify(connection, target, sql);
            } catch (AssertionError | Exception e) {
                failures.add(target.key() + " -> " + e);
            }
        }
        if (!skipped.isEmpty()) {
            System.out.println("skipped targets: " + String.join(", ", skipped));
        }
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    private void verify(Connection connection, IntegrationConfig.Target target, DialectSql sql)
            throws Exception {
        exec(connection, "DROP TABLE IF EXISTS " + TABLE);
        exec(connection, sql.createTable());

        // full bucket: capacity 10, rate 0, tokens 10
        update(connection, sql.createIfAbsent(), NS, NAME,
                new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("10"));
        assertEquals(0, effective(connection, sql).compareTo(new BigDecimal("10")),
                target.key() + ": initial tokens");

        // atomic guard + consume
        assertEquals(1, update(connection, sql.tryConsume(), 7L, NS, NAME, 7L),
                target.key() + ": consume 7");
        assertEquals(0, effective(connection, sql).compareTo(new BigDecimal("3")),
                target.key() + ": tokens after consume");

        // insufficient balance is rejected and changes nothing
        assertEquals(0, update(connection, sql.tryConsume(), 5L, NS, NAME, 5L),
                target.key() + ": guard rejects");
        assertEquals(0, effective(connection, sql).compareTo(new BigDecimal("3")),
                target.key() + ": tokens unchanged after rejection");
        assertTrue(exists(connection, sql), target.key() + ": existence probe");

        // lazy refill: empty bucket, rate 1, last write two seconds ago
        exec(connection, "UPDATE " + TABLE + " SET tokens = 0, rate = 1, last_refill = "
                        + backdated(sql.dialect()) + " WHERE namespace = ? AND name = ?",
                NS, NAME);
        assertEquals(1, update(connection, sql.tryConsume(), 1L, NS, NAME, 1L),
                target.key() + ": consume after lazy refill");
        BigDecimal afterRefill = effective(connection, sql);
        assertTrue(afterRefill.compareTo(BigDecimal.ONE) >= 0
                        && afterRefill.compareTo(new BigDecimal("1.2")) < 0,
                target.key() + ": fractional remainder preserved but was " + afterRefill);

        // manual deposit accrues first and caps at capacity
        assertEquals(1, update(connection, sql.deposit(), 5L, NS, NAME),
                target.key() + ": deposit");
        assertTrue(effective(connection, sql).compareTo(new BigDecimal("6")) > 0,
                target.key() + ": deposit applied");

        // exact remaining through RETURNING where available
        if (sql.supportsReturning()) {
            BigDecimal remaining = consumeReturning(connection, sql, 1L);
            assertNotNull(remaining, target.key() + ": RETURNING balance");
            assertTrue(remaining.compareTo(BigDecimal.ONE) >= 0,
                    target.key() + ": RETURNING balance was " + remaining);
        }

        // capacity shrink clamps the stored balance
        update(connection, sql.adjustCapacity(),
                new BigDecimal("2"), new BigDecimal("2"), NS, NAME);
        assertEquals(0, capacity(connection, sql).compareTo(new BigDecimal("2")),
                target.key() + ": capacity updated");
        assertTrue(effective(connection, sql).compareTo(new BigDecimal("2")) <= 0,
                target.key() + ": balance clamped to capacity");

        // delete wins and later reads report absence
        assertEquals(1, update(connection, sql.delete(), NS, NAME), target.key() + ": delete");
        assertFalse(exists(connection, sql), target.key() + ": deleted bucket is gone");

        exec(connection, "DROP TABLE IF EXISTS " + TABLE);
    }

    /**
     * Test-only helper that backdates the last write. MySQL needs its own interval syntax; KingbaseES
     * rejects {@code INTERVAL 2 SECOND} even in MySQL compatibility mode and accepts the PostgreSQL
     * literal instead.
     */
    private static String backdated(Dialect dialect) {
        return dialect == Dialect.MYSQL
                ? "DATE_SUB(NOW(6), INTERVAL 2 SECOND)"
                : "NOW() - INTERVAL '2 second'";
    }

    private static Connection open(IntegrationConfig.Target target) throws Exception {
        if (!target.driver().isBlank()) {
            Class.forName(target.driver());
        }
        return DriverManager.getConnection(target.url(), target.user(), target.password());
    }

    private static void exec(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.execute();
        }
    }

    private static int update(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    private static BigDecimal consumeReturning(Connection connection, DialectSql sql, long tokens)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql.tryConsumeReturning())) {
            bind(statement, tokens, NS, NAME, tokens);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : null;
            }
        }
    }

    private static boolean exists(Connection connection, DialectSql sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql.exists())) {
            bind(statement, NS, NAME);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static BigDecimal effective(Connection connection, DialectSql sql) throws SQLException {
        BigDecimal value = value(connection, sql, "effective_tokens");
        assertNotNull(value, "effective_tokens must be present");
        return value;
    }

    private static BigDecimal capacity(Connection connection, DialectSql sql) throws SQLException {
        BigDecimal value = value(connection, sql, "capacity");
        assertNotNull(value, "capacity must be present");
        return value;
    }

    private static BigDecimal value(Connection connection, DialectSql sql, String column)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql.get())) {
            bind(statement, NS, NAME);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(column) : null;
            }
        }
    }
}
