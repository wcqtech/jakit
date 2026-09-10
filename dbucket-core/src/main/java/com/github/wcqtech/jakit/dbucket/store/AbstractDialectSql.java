package com.github.wcqtech.jakit.dbucket.store;

/**
 * Base class for SQL fragment providers: validates and stores the table identifier.
 *
 * <p>The table name is embedded into SQL text, so it cannot be bound as a parameter. It is therefore
 * validated strictly instead of quoted: an optional {@code schema.} qualifier plus identifiers
 * matching {@code [A-Za-z_][A-Za-z0-9_$]*}, each part at most
 * {@value #MAX_IDENTIFIER_LENGTH} characters. Quoting is intentionally unsupported, so a table name
 * that needs quoting (mixed case, spaces) is rejected rather than silently misused.
 */
abstract class AbstractDialectSql implements DialectSql {

    /** Maximum length of one identifier part. */
    static final int MAX_IDENTIFIER_LENGTH = 64;

    private final String table;

    protected AbstractDialectSql(String table) {
        this.table = requireTable(table);
    }

    @Override
    public final String tableName() {
        return table;
    }

    /** The validated table name, for building statements. */
    protected final String table() {
        return table;
    }

    /**
     * Existence probe, identical on every dialect; placeholders: namespace, name.
     */
    @Override
    public final String exists() {
        return "SELECT 1 FROM " + table + " WHERE namespace = ? AND name = ?";
    }

    /** Connectivity probe; identical on every dialect. */
    @Override
    public final String ping() {
        return "SELECT 1";
    }

    private static String requireTable(String table) {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("table must not be blank");
        }
        String trimmed = table.trim();
        String[] parts = trimmed.split("\\.", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException(
                    "table must be 'name' or 'schema.name' but was '" + table + "'");
        }
        for (String part : parts) {
            if (!isIdentifier(part)) {
                throw new IllegalArgumentException(
                        "table contains an invalid identifier: '" + table + "'");
            }
        }
        return trimmed;
    }

    private static boolean isIdentifier(String part) {
        if (part.isEmpty() || part.length() > MAX_IDENTIFIER_LENGTH) {
            return false;
        }
        if (!isIdentifierStart(part.charAt(0))) {
            return false;
        }
        for (int i = 1; i < part.length(); i++) {
            char c = part.charAt(i);
            if (!isIdentifierStart(c) && !(c >= '0' && c <= '9') && c != '$') {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }
}
