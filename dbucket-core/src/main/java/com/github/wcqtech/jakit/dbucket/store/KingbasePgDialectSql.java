package com.github.wcqtech.jakit.dbucket.store;

/**
 * KingbaseES V8R6 in {@code pg} compatibility mode.
 *
 * <p>Verified on V008R006C009B0014: {@code NOW()}, {@code EXTRACT(EPOCH FROM (NOW() - col))},
 * {@code NUMERIC(20,6)}, {@code TIMESTAMPTZ}, {@code ON CONFLICT} and
 * {@code UPDATE ... RETURNING} behave exactly like PostgreSQL, so this profile reuses the
 * PostgreSQL statements and only reports a different dialect identity.
 *
 * <p>The {@code mysql} compatibility mode needs different fragments; see
 * {@link KingbaseMysqlDialectSql}.
 */
public final class KingbasePgDialectSql extends PostgresDialectSql {

    public KingbasePgDialectSql(String table) {
        super(table);
    }

    @Override
    public Dialect dialect() {
        return Dialect.KINGBASE;
    }

    @Override
    public String dialectName() {
        return "kingbase(pg)";
    }
}
