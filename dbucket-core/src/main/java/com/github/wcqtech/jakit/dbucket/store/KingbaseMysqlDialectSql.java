package com.github.wcqtech.jakit.dbucket.store;

/**
 * KingbaseES V8R6 in {@code mysql} compatibility mode.
 *
 * <p>This profile is neither plain MySQL nor plain PostgreSQL. Verified on V008R006C009B0014:
 *
 * <ul>
 *   <li>{@code NOW(6)} does not exist ({@code function NOW(integer) does not exist}) and {@code NOW()}
 *       yields a {@code timestamp} without time zone.</li>
 *   <li>Subtracting timestamps follows MySQL numeric semantics, so
 *       {@code EXTRACT(EPOCH FROM (NOW() - last_refill))} fails with
 *       {@code date_part(unknown, bigint) does not exist}. Elapsed seconds must come from
 *       {@code TIMESTAMPDIFF(MICROSECOND, last_refill, NOW()) / 1000000.0}, which accepts a
 *       {@code timestamptz} first argument.</li>
 *   <li>{@code TIMESTAMPTZ} columns are supported, and the explicit
 *       {@code NOW()::timestamptz} cast resolves the type clash observed with plain
 *       {@code GREATEST(NOW(), <timestamptz column>)}. Using the cast keeps the bucket independent of
 *       the session time zone, exactly like the {@code pg} profile - no {@code SET TIME ZONE} setup is
 *       required.</li>
 *   <li>{@code ON CONFLICT}, {@code ON DUPLICATE KEY UPDATE} and {@code UPDATE ... RETURNING} are all
 *       supported; {@code ON CONFLICT} is used here to stay close to the PostgreSQL profile.</li>
 *   <li>MySQL table options are rejected: {@code ENGINE}/{@code DEFAULT CHARSET} is a syntax error,
 *       so the DDL stays option free.</li>
 * </ul>
 */
public final class KingbaseMysqlDialectSql extends AbstractDialectSql {

    public KingbaseMysqlDialectSql(String table) {
        super(table);
    }

    @Override
    public Dialect dialect() {
        return Dialect.KINGBASE;
    }

    @Override
    public String dialectName() {
        return "kingbase(mysql)";
    }

    @Override
    public boolean zonedLastRefill() {
        return true;
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
                  last_refill TIMESTAMPTZ   NOT NULL,
                  PRIMARY KEY (namespace, name)
                )""".formatted(table());
    }

    @Override
    public String createIfAbsent() {
        return "INSERT INTO " + table()
                + " (namespace, name, capacity, rate, tokens, last_refill)"
                + " VALUES (?, ?, ?, ?, ?, NOW()::timestamptz)"
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
                + " last_refill = " + now()
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
                + " last_refill = " + now()
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

    /** {@code NOW()} returns a plain timestamp here, so it must be cast before meeting a timestamptz. */
    private String now() {
        return "GREATEST(NOW()::timestamptz, last_refill)";
    }

    private String elapsed() {
        return "TIMESTAMPDIFF(MICROSECOND, last_refill, NOW()) / 1000000.0 * rate";
    }

    private String effective() {
        return "LEAST(capacity, tokens + GREATEST(0, " + elapsed() + "))";
    }
}
