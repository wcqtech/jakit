package com.github.wcqtech.jakit.dbucket.store;

import java.util.Locale;
import java.util.Optional;

/**
 * The two KingbaseES compatibility modes this component supports, as reported by
 * {@code SHOW database_mode}.
 *
 * <p>The Oracle (and SQL Server) modes are deliberately not mapped: they change string and date
 * semantics in ways this component does not verify, and the storage layer fails fast instead of
 * guessing.
 */
public enum KingbaseMode {

    /** {@code database_mode = 'pg'}: PostgreSQL compatible fragments. */
    PG,

    /** {@code database_mode = 'mysql'}: MySQL compatible function set on a PostgreSQL kernel. */
    MYSQL;

    /**
     * Maps the value returned by {@code SHOW database_mode}.
     *
     * @return the mode, or empty for unsupported or unknown values
     */
    public static Optional<KingbaseMode> fromDatabaseMode(String databaseMode) {
        if (databaseMode == null) {
            return Optional.empty();
        }
        return switch (databaseMode.trim().toLowerCase(Locale.ROOT)) {
            case "mysql" -> Optional.of(MYSQL);
            case "pg", "postgres", "postgresql" -> Optional.of(PG);
            default -> Optional.empty();
        };
    }
}
