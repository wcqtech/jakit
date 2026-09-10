package com.github.wcqtech.jakit.dbucket.store;

import java.util.Objects;

/**
 * Factory for the built-in {@link DialectSql} profiles.
 *
 * <p>KingbaseES always needs an explicit {@link KingbaseMode} because the two compatibility modes
 * cannot be told apart from the product name. The JDBC storage layer resolves it once per
 * datasource with {@code SHOW database_mode}.
 */
public final class DialectSqls {

    private DialectSqls() {
    }

    /** MySQL 8 profile. */
    public static DialectSql mysql(String table) {
        return new MysqlDialectSql(table);
    }

    /** PostgreSQL profile. */
    public static DialectSql postgresql(String table) {
        return new PostgresDialectSql(table);
    }

    /** KingbaseES profile for the given compatibility mode. */
    public static DialectSql kingbase(String table, KingbaseMode mode) {
        Objects.requireNonNull(mode, "mode must not be null");
        return mode == KingbaseMode.PG
                ? new KingbasePgDialectSql(table)
                : new KingbaseMysqlDialectSql(table);
    }

    /**
     * Resolves a profile from a dialect family.
     *
     * @throws IllegalArgumentException for {@link Dialect#KINGBASE}, which additionally needs a
     *         mode: use {@link #kingbase(String, KingbaseMode)}
     */
    public static DialectSql forDialect(Dialect dialect, String table) {
        Objects.requireNonNull(dialect, "dialect must not be null");
        return switch (dialect) {
            case MYSQL -> mysql(table);
            case POSTGRESQL -> postgresql(table);
            case KINGBASE -> throw new IllegalArgumentException(
                    "KingbaseES needs a compatibility mode: use kingbase(table, mode)");
        };
    }
}
