package com.github.wcqtech.jakit.dbucket;

/**
 * Base type for unchecked exceptions raised by dbucket.
 */
public class DbucketException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DbucketException(String message) {
        super(message);
    }

    public DbucketException(String message, Throwable cause) {
        super(message, cause);
    }
}
