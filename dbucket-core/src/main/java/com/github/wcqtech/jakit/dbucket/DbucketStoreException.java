package com.github.wcqtech.jakit.dbucket;

/**
 * Thrown when the underlying storage fails (connection, SQL, dialect or schema problems).
 *
 * <p>Policy layers decide what to do with it: the facade can degrade to fail-open, while the
 * default behaviour is to propagate it to the caller.
 *
 * <p>Consuming a token is <strong>at-most-once</strong>: when this exception escapes a
 * mutating call, it is unknown whether the statement committed, so callers that retry must be
 * idempotent at the business level.
 */
public class DbucketStoreException extends DbucketException {

    private static final long serialVersionUID = 1L;

    public DbucketStoreException(String message) {
        super(message);
    }

    public DbucketStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
