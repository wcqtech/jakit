package com.github.wcqtech.jakit.dbucket.store;

/**
 * SQL fragment provider for one concrete dialect profile.
 *
 * <p>The component keeps every dialect specific detail in implementations of this interface, so the
 * JDBC storage skeleton only deals with parameter binding, result mapping, connection handling and
 * error translation. Supporting a new database means implementing this interface - nothing in the
 * policy layer changes.
 *
 * <h2>Shared shape</h2>
 * All statements target the bucket table created by {@link #createTable()} and use the columns
 * {@code namespace}, {@code name}, {@code capacity}, {@code rate}, {@code tokens} and
 * {@code last_refill}. Statements are plain {@code ?} parameterised SQL; the table name is embedded
 * as a validated identifier.
 *
 * <p>Every mutating statement accrues lazily accrued tokens <em>inside the same statement</em> using
 * the database clock, caps the balance at {@code capacity} and preserves the fractional remainder.
 * The consume guard is evaluated against the <em>post-refill</em> effective balance, otherwise a
 * bucket that is temporarily empty but already refillable would be rejected incorrectly.
 *
 * <h2>Placeholder order</h2>
 * <ul>
 *   <li>{@link #createIfAbsent()}: namespace, name, capacity, rate, tokens</li>
 *   <li>{@link #get()}: namespace, name</li>
 *   <li>{@link #exists()}: namespace, name</li>
 *   <li>{@link #tryConsume()} / {@link #tryConsumeReturning()}: tokens, namespace, name, tokens</li>
 *   <li>{@link #deposit()}: tokens, namespace, name</li>
 *   <li>{@link #adjustCapacity()}: capacity, capacity, namespace, name</li>
 *   <li>{@link #adjustRate()}: rate, namespace, name</li>
 *   <li>{@link #delete()}: namespace, name</li>
 * </ul>
 */
public interface DialectSql {

    /** Default bucket table name; may be overridden per store. */
    String DEFAULT_TABLE = "dbucket";

    /** Product dialect this profile belongs to. */
    Dialect dialect();

    /** Human readable profile name for logs, for example {@code mysql} or {@code kingbase(mysql)}. */
    String dialectName();

    /** Validated table name embedded in every statement. */
    String tableName();

    /**
     * Whether the {@code last_refill} column carries a time zone ({@code TIMESTAMPTZ}) or stores a
     * wall-clock value ({@code DATETIME(6)} / {@code TIMESTAMP(6)}).
     *
     * <p>The storage layer uses this to convert the column into an {@link java.time.Instant} without
     * silently depending on the JVM default time zone: zoned columns are read as an instant, unzoned
     * ones as UTC wall clock, which relies on the session time zone being pinned to UTC for those
     * dialects.
     */
    boolean zonedLastRefill();

    /** Idempotent DDL creating the bucket table, including its composite primary key. */
    String createTable();

    /**
     * Race safe insert used by {@code createIfAbsent}: a unique key plus the dialect upsert, so
     * concurrent creators converge on one row.
     */
    String createIfAbsent();

    /**
     * Read-only statement returning the effective token count without writing the row; the result
     * exposes {@code effective_tokens}, {@code stored_tokens} and {@code last_refill}.
     */
    String get();

    /**
     * Existence probe used to tell {@code INSUFFICIENT} from {@code NOT_FOUND}, and to keep deposit
     * and adjust results correct on drivers whose affected-row semantics are configurable. It returns
     * a row exactly when the bucket exists.
     */
    String exists();

    /**
     * Atomic accrue + guard + consume. The affected row count decides the outcome: {@code 1} means
     * the tokens were consumed, {@code 0} means either not enough tokens or no such bucket.
     */
    String tryConsume();

    /**
     * Whether {@link #tryConsumeReturning()} is available for this profile.
     *
     * <p>{@code true} for PostgreSQL and both KingbaseES profiles, {@code false} for MySQL 8, which
     * has no {@code UPDATE ... RETURNING}.
     */
    boolean supportsReturning();

    /**
     * {@link #tryConsume()} extended with {@code RETURNING tokens} for an exact remaining balance.
     *
     * @throws UnsupportedOperationException when {@link #supportsReturning()} is {@code false}
     */
    String tryConsumeReturning();

    /**
     * Manual deposit: accrues, adds the deposit and caps the balance at capacity (overflow is
     * discarded).
     */
    String deposit();

    /** Replaces capacity and atomically clamps the balance ({@code tokens = LEAST(tokens, capacity)}). */
    String adjustCapacity();

    /** Replaces the refill rate without touching the current balance. */
    String adjustRate();

    /** Deletes the bucket row. */
    String delete();

    /** Lightweight connectivity and schema probing statement, for example {@code SELECT 1}. */
    String ping();
}
