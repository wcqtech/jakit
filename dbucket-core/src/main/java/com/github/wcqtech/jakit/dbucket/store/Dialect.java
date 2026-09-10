package com.github.wcqtech.jakit.dbucket.store;

import java.util.Locale;
import java.util.Optional;

/**
 * Storage dialect families supported out of the box.
 *
 * <p>{@link #KINGBASE} is a first class dialect rather than an alias of {@link #MYSQL} or
 * {@link #POSTGRESQL}. KingbaseES is a PostgreSQL kernel with switchable compatibility modes, and
 * the two modes differ in ways that matter to this component:
 *
 * <ul>
 *   <li><b>PG mode</b> ({@code SHOW database_mode} = {@code pg}): {@code NOW()} yields
 *       {@code timestamptz}, elapsed time comes from
 *       {@code EXTRACT(EPOCH FROM (NOW() - last_refill))}, and {@code ON CONFLICT} applies.</li>
 *   <li><b>MySQL mode</b> ({@code SHOW database_mode} = {@code mysql}): {@code NOW(6)} does not
 *       exist, subtracting timestamps follows MySQL numeric semantics so
 *       {@code EXTRACT(EPOCH FROM (NOW() - ...))} fails, elapsed time must come from
 *       {@code TIMESTAMPDIFF(MICROSECOND, last_refill, NOW())}, and {@code last_refill} must be a
 *       {@code TIMESTAMP(6)} column to avoid a type clash in {@code GREATEST(NOW(), last_refill)}.</li>
 * </ul>
 *
 * <p>The Oracle compatibility mode is not supported. The actual fragment selection is private to the
 * JDBC storage implementation, which resolves it from {@code SHOW database_mode}.
 */
public enum Dialect {

    /** MySQL 8.x ({@code NOW(6)}, {@code DATETIME(6)}, {@code ON DUPLICATE KEY UPDATE}, no RETURNING). */
    MYSQL,

    /** PostgreSQL ({@code NOW()}, {@code TIMESTAMPTZ}, {@code ON CONFLICT}, {@code RETURNING}). */
    POSTGRESQL,

    /** KingbaseES V8R6, both {@code pg} and {@code mysql} compatibility modes. */
    KINGBASE;

    private static final String JDBC_PREFIX = "jdbc:";

    /**
     * Resolves the dialect from a JDBC URL, for example
     * {@code jdbc:kingbase8://host:54321/db} to {@link #KINGBASE}.
     *
     * @return the dialect, or empty when the subprotocol is unknown (MariaDB and other unverified
     *         products are intentionally not mapped)
     */
    public static Optional<Dialect> fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return Optional.empty();
        }
        String url = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (!url.startsWith(JDBC_PREFIX)) {
            return Optional.empty();
        }
        String rest = url.substring(JDBC_PREFIX.length());
        int separator = rest.indexOf(':');
        String subProtocol = separator < 0 ? rest : rest.substring(0, separator);
        return switch (subProtocol) {
            case "mysql" -> Optional.of(MYSQL);
            case "postgresql", "postgres" -> Optional.of(POSTGRESQL);
            case "kingbase8", "kingbase" -> Optional.of(KINGBASE);
            default -> Optional.empty();
        };
    }

    /**
     * Resolves the dialect from {@link java.sql.DatabaseMetaData#getDatabaseProductName()}.
     *
     * <p>KingbaseES is matched before PostgreSQL because some builds report a product name that
     * mentions both.
     *
     * @return the dialect, or empty when the product is unknown
     */
    public static Optional<Dialect> fromProductName(String productName) {
        if (productName == null) {
            return Optional.empty();
        }
        String product = productName.toLowerCase(Locale.ROOT);
        if (product.contains("kingbase")) {
            return Optional.of(KINGBASE);
        }
        if (product.contains("postgres")) {
            return Optional.of(POSTGRESQL);
        }
        if (product.contains("mysql")) {
            return Optional.of(MYSQL);
        }
        return Optional.empty();
    }
}
