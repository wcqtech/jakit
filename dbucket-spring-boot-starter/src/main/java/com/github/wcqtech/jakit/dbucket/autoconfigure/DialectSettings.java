package com.github.wcqtech.jakit.dbucket.autoconfigure;

import com.github.wcqtech.jakit.dbucket.store.Dialect;
import com.github.wcqtech.jakit.dbucket.store.KingbaseMode;
import java.util.Locale;

/**
 * Parsed dialect configuration: an optional explicit {@link Dialect} plus an optional
 * {@link KingbaseMode}.
 *
 * <p>{@code null} dialect means "detect from the connection"; {@code null} mode means "query
 * {@code SHOW database_mode}".
 */
record DialectSettings(Dialect dialect, KingbaseMode kingbaseMode) {

    static DialectSettings parse(DbucketProperties properties) {
        String raw = properties.getDialect() == null
                ? "auto"
                : properties.getDialect().trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty() || "auto".equals(raw)) {
            return new DialectSettings(null, null);
        }
        Dialect dialect = switch (raw) {
            case "mysql" -> Dialect.MYSQL;
            case "postgresql", "postgres", "pg" -> Dialect.POSTGRESQL;
            case "kingbase", "kingbase8" -> Dialect.KINGBASE;
            default -> throw new IllegalStateException("unsupported jakit.dbucket.dialect '"
                    + properties.getDialect() + "': expected auto, mysql, postgresql or kingbase");
        };
        if (dialect != Dialect.KINGBASE) {
            return new DialectSettings(dialect, null);
        }
        return new DialectSettings(dialect, parseKingbaseMode(properties.getKingbase()));
    }

    private static KingbaseMode parseKingbaseMode(DbucketProperties.Kingbase kingbase) {
        String raw = kingbase.getSqlProfile() == null
                ? "auto"
                : kingbase.getSqlProfile().trim().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "pg", "postgres", "postgresql" -> KingbaseMode.PG;
            case "mysql" -> KingbaseMode.MYSQL;
            case "", "auto" -> {
                if (!kingbase.isDetectMode()) {
                    throw new IllegalStateException("jakit.dbucket.kingbase.sql-profile=auto queries"
                            + " SHOW database_mode but detect-mode is disabled; set sql-profile to pg or"
                            + " mysql, or enable detect-mode");
                }
                yield null;
            }
            default -> throw new IllegalStateException("unsupported jakit.dbucket.kingbase.sql-profile '"
                    + kingbase.getSqlProfile() + "': expected auto, pg or mysql");
        };
    }
}
