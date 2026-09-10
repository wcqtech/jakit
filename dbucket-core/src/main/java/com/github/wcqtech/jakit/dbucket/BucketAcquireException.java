package com.github.wcqtech.jakit.dbucket;

/**
 * Thrown by the annotation facade when acquiring tokens was rejected.
 *
 * <p>Carries enough context to build a response or a metric without re-deriving anything: the bucket,
 * the requested amount and the {@link AcquireResult} that caused the rejection. Only thrown for
 * {@link AcquireResult.Outcome#INSUFFICIENT} and {@link AcquireResult.Outcome#NOT_FOUND}; a fail-open
 * decision arrives as a successful, degraded result instead.
 */
public class BucketAcquireException extends DbucketException {

    private static final long serialVersionUID = 1L;

    private final String namespace;
    private final String name;
    private final long tokens;
    private final transient AcquireResult result;

    public BucketAcquireException(String namespace, String name, long tokens, AcquireResult result) {
        super("dbucket rejected acquiring " + tokens + " token(s) from " + namespace + "/" + name
                + ": " + result.outcome() + " after " + result.waited().toMillis() + " ms");
        this.namespace = namespace;
        this.name = name;
        this.tokens = tokens;
        this.result = result;
    }

    /** Namespace of the bucket that rejected the acquire. */
    public String getNamespace() {
        return namespace;
    }

    /** Name of the bucket that rejected the acquire. */
    public String getName() {
        return name;
    }

    /** Requested token amount. */
    public long getTokens() {
        return tokens;
    }

    /** The outcome that caused the rejection ({@code INSUFFICIENT} or {@code NOT_FOUND}). */
    public AcquireResult getResult() {
        return result;
    }
}
