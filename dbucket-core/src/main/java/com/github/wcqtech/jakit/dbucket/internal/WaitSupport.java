package com.github.wcqtech.jakit.dbucket.internal;

import java.time.Duration;

/**
 * Time source and sleep seam of the blocking acquire.
 *
 * <p>Not part of the public API. Injecting it keeps the polling loop deterministic in tests: a fake
 * implementation can advance a virtual clock instead of really sleeping.
 */
public interface WaitSupport {

    /** Monotonic nanoseconds, used for the total timeout budget. */
    long nanoTime();

    /** Sleeps for the given duration; never called with a zero or negative duration. */
    void sleep(Duration duration) throws InterruptedException;

    /** Real clock and thread sleep. */
    static WaitSupport system() {
        return SystemWaitSupport.INSTANCE;
    }
}

/**
 * Default {@link WaitSupport} on {@link System#nanoTime()} and {@link Thread#sleep(long, int)}.
 */
final class SystemWaitSupport implements WaitSupport {

    static final SystemWaitSupport INSTANCE = new SystemWaitSupport();

    private SystemWaitSupport() {
    }

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }

    @Override
    public void sleep(Duration duration) throws InterruptedException {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        long millis = duration.toMillis();
        int nanos = (int) (duration.toNanos() - millis * 1_000_000L);
        Thread.sleep(millis, Math.max(nanos, 0));
    }
}
