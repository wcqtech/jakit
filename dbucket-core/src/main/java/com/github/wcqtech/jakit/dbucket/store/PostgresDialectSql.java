package com.github.wcqtech.jakit.dbucket.store;

/**
 * PostgreSQL fragments, and the shared base for the KingbaseES {@code pg} compatibility mode.
 *
 * <p>{@code NOW()} yields {@code timestamptz} and elapsed seconds come from
 * {@code EXTRACT(EPOCH FROM (NOW() - last_refill))}. Creation uses {@code ON CONFLICT ... DO NOTHING}
 * and {@code UPDATE ... RETURNING tokens} can return the exact remaining balance in the same atomic
 * statement.
 *
 * <p>Subclassed by {@link KingbasePgDialectSql}: KingbaseES has a PostgreSQL kernel and its
 * {@code pg} mode accepts these statements unchanged, the observed differences for the
 * {@code mysql} mode live in {@link KingbaseMysqlDialectSql}.
 */
public class PostgresDialectSql extends AbstractDialectSql {

    public PostgresDialectSql(String table) {
        super(table);
    }

    @Override
    public Dialect dialect() {
        return Dialect.POSTGRESQL;
    }

    @Override
    public String dialectName() {
        return "postgresql";
    }

    @Override
    public String createTable() {
        return """
                CREATE TABLE IF NOT EXISTS %s (
                  namespace   VARCHAR(64)   NOT NULL,
                  name        VARCHAR(128)  NOT NULL,
                  capacity    NUMERIC(20,6) NOT NULL,
                  rate        NUMERIC(20,6) NOT NULL,
                  tokens      NUMERIC(20,6) NOT NULL,
                  last_refill TIMESTAMPTZ   NOT NULL,
                  PRIMARY KEY (namespace, name)
                )""".formatted(table());
    }

    @Override
    public String createIfAbsent() {
        return "INSERT INTO " + table()
                + " (namespace, name, capacity, rate, tokens, last_refill)"
                + " VALUES (?, ?, ?, ?, ?, NOW())"
                + " ON CONFLICT (namespace, name) DO NOTHING";
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
                + " last_refill = GREATEST(NOW(), last_refill)"
                + " WHERE namespace = ? AND name = ?"
                + " AND " + effective() + " >= ?";
    }

    @Override
    public boolean supportsReturning() {
        return true;
    }

    @Override
    public String tryConsumeReturning() {
        return tryConsume() + " RETURNING tokens";
    }

    @Override
    public String deposit() {
        return "UPDATE " + table()
                + " SET tokens = LEAST(capacity, tokens + GREATEST(0, " + elapsed() + ") + ?),"
                + " last_refill = GREATEST(NOW(), last_refill)"
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
        return true;
    }

    private String elapsed() {
        return "EXTRACT(EPOCH FROM (NOW() - last_refill)) * rate";
    }

    private String effective() {
        return "LEAST(capacity, tokens + GREATEST(0, " + elapsed() + "))";
    }
}
