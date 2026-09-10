package com.github.wcqtech.jakit.dbucket.store;

/**
 * MySQL 8.x fragments.
 *
 * <p>Differences from the PostgreSQL family:
 * <ul>
 *   <li>{@code NOW(6)} carries microsecond precision; {@code last_refill} is a {@code DATETIME(6)}
 *       column.</li>
 *   <li>Elapsed seconds come from
 *       {@code TIMESTAMPDIFF(MICROSECOND, last_refill, NOW(6)) / 1000000.0} - the {@code .0} matters,
 *       integer division would truncate to whole seconds.</li>
 *   <li>Creation uses {@code ON DUPLICATE KEY UPDATE}; the affected row count is not a reliable
 *       "was created" signal and must not be used as one.</li>
 *   <li>There is no {@code UPDATE ... RETURNING}, so {@link #supportsReturning()} is {@code false}.</li>
 * </ul>
 */
public final class MysqlDialectSql extends AbstractDialectSql {

    public MysqlDialectSql(String table) {
        super(table);
    }

    @Override
    public Dialect dialect() {
        return Dialect.MYSQL;
    }

    @Override
    public String dialectName() {
        return "mysql";
    }

    @Override
    public String createTable() {
        return """
                CREATE TABLE IF NOT EXISTS %s (
                  namespace   VARCHAR(64)   NOT NULL,
                  name        VARCHAR(128)  NOT NULL,
                  capacity    DECIMAL(20,6) NOT NULL,
                  rate        DECIMAL(20,6) NOT NULL,
                  tokens      DECIMAL(20,6) NOT NULL,
                  last_refill DATETIME(6)   NOT NULL,
                  PRIMARY KEY (namespace, name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""".formatted(table());
    }

    @Override
    public String createIfAbsent() {
        return "INSERT INTO " + table()
                + " (namespace, name, capacity, rate, tokens, last_refill)"
                + " VALUES (?, ?, ?, ?, ?, NOW(6))"
                + " ON DUPLICATE KEY UPDATE name = name";
    }

    @Override
    public String get() {
        return "SELECT namespace, name, capacity, rate,"
                + " " + effective() + " AS effective_tokens,"
                + " tokens AS stored_tokens, last_refill"
                + " FROM " + table() + " WHERE namespace = ? AND name = ?";
    }

    @Override
    public String tryConsume() {
        return "UPDATE " + table()
                + " SET tokens = " + effective() + " - ?,"
                + " last_refill = GREATEST(NOW(6), last_refill)"
                + " WHERE namespace = ? AND name = ?"
                + " AND " + effective() + " >= ?";
    }

    @Override
    public boolean supportsReturning() {
        return false;
    }

    @Override
    public String tryConsumeReturning() {
        throw new UnsupportedOperationException("MySQL 8 has no UPDATE ... RETURNING");
    }

    @Override
    public String deposit() {
        return "UPDATE " + table()
                + " SET tokens = LEAST(capacity, tokens + GREATEST(0, " + elapsed() + ") + ?),"
                + " last_refill = GREATEST(NOW(6), last_refill)"
                + " WHERE namespace = ? AND name = ?";
    }

    @Override
    public String adjustCapacity() {
        return "UPDATE " + table()
                + " SET capacity = ?, tokens = LEAST(tokens, ?)"
                + " WHERE namespace = ? AND name = ?";
    }

    @Override
    public String adjustRate() {
        return "UPDATE " + table()
                + " SET rate = ?"
                + " WHERE namespace = ? AND name = ?";
    }

    @Override
    public String delete() {
        return "DELETE FROM " + table() + " WHERE namespace = ? AND name = ?";
    }

    @Override
    public boolean zonedLastRefill() {
        return false;
    }

    private String elapsed() {
        return "TIMESTAMPDIFF(MICROSECOND, last_refill, NOW(6)) / 1000000.0 * rate";
    }

    private String effective() {
        return "LEAST(capacity, tokens + GREATEST(0, " + elapsed() + "))";
    }
}
