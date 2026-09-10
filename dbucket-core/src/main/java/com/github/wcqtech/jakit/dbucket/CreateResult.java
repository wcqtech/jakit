package com.github.wcqtech.jakit.dbucket;

/**
 * Outcome of {@link BucketStore#createIfAbsent(BucketSpec)}.
 *
 * <p>Creation is race safe: concurrent callers may all "create" the same bucket, but only one row
 * exists and every caller is told whether it created the row or observed an existing one.
 *
 * <p>When the bucket already exists, its stored configuration wins: the {@code capacity} and
 * {@code rate} from the request are ignored. The current state is returned in {@link #snapshot()} so
 * callers can log or align with the effective configuration.
 *
 * @param outcome  {@code CREATED} or {@code EXISTS}, never {@code null}
 * @param snapshot current bucket state; mandatory for {@code EXISTS}, optional for {@code CREATED}
 */
public record CreateResult(Outcome outcome, BucketSnapshot snapshot) {

    /** Result kinds of a create attempt. */
    public enum Outcome {

        /** This call inserted the bucket row. */
        CREATED,

        /** The bucket already existed; stored configuration wins. */
        EXISTS
    }

    public CreateResult {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (outcome == Outcome.EXISTS && snapshot == null) {
            throw new IllegalArgumentException("EXISTS requires the current snapshot");
        }
    }

    /** The bucket was created by this call. */
    public static CreateResult created() {
        return new CreateResult(Outcome.CREATED, null);
    }

    /** The bucket was created by this call, with the state it was created with. */
    public static CreateResult created(BucketSnapshot snapshot) {
        return new CreateResult(Outcome.CREATED, snapshot);
    }

    /** The bucket already existed; {@code snapshot} is its current state. */
    public static CreateResult exists(BucketSnapshot snapshot) {
        return new CreateResult(Outcome.EXISTS, snapshot);
    }

    public boolean isCreated() {
        return outcome == Outcome.CREATED;
    }
}
