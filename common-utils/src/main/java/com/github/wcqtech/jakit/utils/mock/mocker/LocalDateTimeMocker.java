package com.github.wcqtech.jakit.utils.mock.mocker;

import java.time.LocalDateTime;
import java.util.Objects;

import com.github.wcqtech.jakit.utils.mock.MockContext;
import com.github.wcqtech.jakit.utils.mock.Mocker;

/**
 * Produces {@link LocalDateTime} values inside the deterministic window
 * starting at {@code 2020-01-01T00:00:00} with an offset of at most
 * {@link #WINDOW_SECONDS} seconds (10 years), derived from the mock context
 * seed.
 */
public final class LocalDateTimeMocker implements Mocker<LocalDateTime> {

    /**
     * Start of the deterministic time window.
     */
    public static final LocalDateTime START = LocalDateTime.of(2020, 1, 1, 0, 0, 0);

    /**
     * Length of the deterministic time window in seconds (10 non-leap years).
     */
    public static final long WINDOW_SECONDS = 315_360_000L;

    @Override
    public LocalDateTime mock(MockContext context) {
        Objects.requireNonNull(context, "context must not be null");
        long offset = Math.floorMod(context.getSeed(), WINDOW_SECONDS);
        return START.plusSeconds(offset);
    }
}
