package com.github.wcqtech.jakit.dbucket.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.wcqtech.jakit.dbucket.BucketSnapshot;
import com.github.wcqtech.jakit.dbucket.BucketSpec;
import com.github.wcqtech.jakit.dbucket.ConsumeResult;
import com.github.wcqtech.jakit.dbucket.CreateResult;
import com.github.wcqtech.jakit.dbucket.it.IntegrationConfig;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Opt-in integration test of the full {@link JdbcBucketStore} SPI against real databases, including
 * dialect auto-detection, UTC/KingbaseES mode handling, the exact-remaining paths (RETURNING and the
 * MySQL local transaction) and the distributed consumption invariant.
 *
 * <p>See {@link IntegrationConfig} for configuration; databases that cannot be reached are skipped.
 */
class JdbcBucketStoreIntegrationTest {

    private static final String TABLE = "dbucket_store_it_bucket";
    private static final String NS = "it";
    private static final String NAME = "bucket";
    private static final String CONCURRENT = "concurrent";

    @Test
    void storeBehavesOnRealDatabases() throws Exception {
        List<IntegrationConfig.Target> targets = IntegrationConfig.targets();
        Assumptions.assumeFalse(targets.isEmpty(),
                "no integration config found (set -Ddbucket.it.config=<path>)");

        List<String> failures = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (IntegrationConfig.Target target : targets) {
            DataSource dataSource;
            try {
                dataSource = IntegrationConfig.dataSource(target);
                dropTable(dataSource);
            } catch (Exception e) {
                skipped.add(target.key() + " (unreachable: " + e.getMessage() + ")");
                continue;
            }
            try {
                verify(target, dataSource);
            } catch (AssertionError | Exception e) {
                failures.add(target.key() + " -> " + e);
            } finally {
                try {
                    dropTable(dataSource);
                } catch (Exception ignored) {
                    // cleanup is best effort
                }
            }
        }
        if (!skipped.isEmpty()) {
            System.out.println("skipped targets: " + String.join(", ", skipped));
        }
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    private void verify(IntegrationConfig.Target target, DataSource dataSource) throws Exception {
        JdbcBucketStore store = JdbcBucketStore.detect(dataSource, TABLE);

        // detection: product name plus, for KingbaseES, SHOW database_mode
        assertEquals(IntegrationConfig.expectedDialect(target), store.dialectSql().dialect(),
                target.key() + ": detected dialect");
        if (target.key().startsWith("kingbase")) {
            String expected = target.profile().contains("mysql") ? "kingbase(mysql)" : "kingbase(pg)";
            assertEquals(expected, store.dialectName(), target.key() + ": detected Kingbase mode");
        }

        store.ping();
        store.createTable();

        store.delete(NS, NAME);

        // create, then observe that stored configuration wins
        assertTrue(store.createIfAbsent(spec(new BigDecimal("10"), BigDecimal.ZERO,
                BucketSpec.InitialState.FULL)).isCreated(), target.key() + ": created");
        CreateResult again = store.createIfAbsent(spec(new BigDecimal("99"), new BigDecimal("5"),
                BucketSpec.InitialState.EMPTY));
        assertEquals(CreateResult.Outcome.EXISTS, again.outcome(), target.key() + ": exists");
        assertNotNull(again.snapshot(), target.key() + ": existing snapshot");
        assertEquals(0, again.snapshot().capacity().compareTo(new BigDecimal("10")),
                target.key() + ": stored capacity wins");

        // effective tokens and lastRefill (the latter also validates the UTC mapping)
        BucketSnapshot snapshot = store.get(NS, NAME).orElseThrow();
        assertEquals(0, snapshot.tokens().compareTo(new BigDecimal("10")),
                target.key() + ": initial effective tokens");
        assertNotNull(snapshot.lastRefill(), target.key() + ": lastRefill present");
        assertTrue(Duration.between(snapshot.lastRefill(), Instant.now()).abs().toMinutes() < 10,
                target.key() + ": lastRefill should be close to now but was " + snapshot.lastRefill());

        // consume: success, rejection, unchanged balance
        assertEquals(ConsumeResult.Outcome.SUCCESS, store.tryConsume(NS, NAME, 7, false).outcome(),
                target.key() + ": consume 7");
        assertEquals(ConsumeResult.Outcome.INSUFFICIENT, store.tryConsume(NS, NAME, 5, false).outcome(),
                target.key() + ": guard rejects 5");
        assertEquals(0, store.get(NS, NAME).orElseThrow().tokens().compareTo(new BigDecimal("3")),
                target.key() + ": balance unchanged after rejection");

        // exact remaining: RETURNING on PostgreSQL/KingbaseES, local transaction on MySQL
        ConsumeResult exact = store.tryConsume(NS, NAME, 1, true);
        assertEquals(ConsumeResult.Outcome.SUCCESS, exact.outcome(), target.key() + ": consume 1");
        assertTrue(exact.hasRemaining(), target.key() + ": remaining requested");
        assertEquals(0, exact.remaining().compareTo(new BigDecimal("2")),
                target.key() + ": remaining value was " + exact.remaining());

        // deposit, adjust rate, then shrink capacity (clamps the balance)
        assertTrue(store.deposit(NS, NAME, 5), target.key() + ": deposit");
        assertTrue(store.adjustRate(NS, NAME, BigDecimal.ONE), target.key() + ": adjust rate");
        assertTrue(store.adjustCapacity(NS, NAME, new BigDecimal("2")), target.key() + ": adjust capacity");
        assertTrue(store.get(NS, NAME).orElseThrow().tokens().compareTo(new BigDecimal("2")) <= 0,
                target.key() + ": balance clamped to capacity");

        // unknown buckets never throw, they report absence
        assertEquals(ConsumeResult.Outcome.NOT_FOUND, store.tryConsume(NS, "missing", 1, false).outcome(),
                target.key() + ": consume on missing bucket");
        assertTrue(store.get(NS, "missing").isEmpty(), target.key() + ": get missing");
        assertFalse(store.deposit(NS, "missing", 1), target.key() + ": deposit missing");
        assertFalse(store.adjustRate(NS, "missing", BigDecimal.ONE), target.key() + ": adjust rate missing");
        assertFalse(store.adjustCapacity(NS, "missing", BigDecimal.ONE),
                target.key() + ": adjust capacity missing");
        assertFalse(store.delete(NS, "missing"), target.key() + ": delete missing");

        // delete wins over reads
        assertTrue(store.delete(NS, NAME), target.key() + ": delete");
        assertTrue(store.get(NS, NAME).isEmpty(), target.key() + ": deleted bucket is gone");

        verifyConcurrency(store, target);
    }

    /**
     * Distributed invariant: with a full bucket of 20 tokens and a zero refill rate, exactly 20 of 80
     * concurrent single-token consume attempts may succeed, and the balance must land on zero.
     */
    private void verifyConcurrency(JdbcBucketStore store, IntegrationConfig.Target target) throws Exception {
        store.delete(NS, CONCURRENT);
        store.createIfAbsent(new BucketSpec(NS, CONCURRENT, new BigDecimal("20"), BigDecimal.ZERO,
                BucketSpec.InitialState.FULL));

        int threads = 8;
        int attemptsPerThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int thread = 0; thread < threads; thread++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int attempt = 0; attempt < attemptsPerThread; attempt++) {
                        ConsumeResult result = store.tryConsume(NS, CONCURRENT, 1, false);
                        if (result.isSuccess()) {
                            success.incrementAndGet();
                        } else if (result.outcome() == ConsumeResult.Outcome.INSUFFICIENT) {
                            insufficient.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(20, success.get(), target.key() + ": exactly capacity consumptions succeed");
        assertEquals(threads * attemptsPerThread - 20, insufficient.get(),
                target.key() + ": the rest is rejected");
        assertEquals(0, store.get(NS, CONCURRENT).orElseThrow().tokens().compareTo(BigDecimal.ZERO),
                target.key() + ": balance drained to zero");
        assertTrue(store.delete(NS, CONCURRENT), target.key() + ": concurrent bucket cleaned up");
    }

    private static BucketSpec spec(BigDecimal capacity, BigDecimal rate,
                                   BucketSpec.InitialState initialState) {
        return new BucketSpec(NS, NAME, capacity, rate, initialState);
    }

    private static void dropTable(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + TABLE);
        }
    }
}
