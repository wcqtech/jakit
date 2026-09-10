package com.github.wcqtech.jakit.dbucket.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.wcqtech.jakit.dbucket.AcquireResult;
import com.github.wcqtech.jakit.dbucket.Bucket;
import com.github.wcqtech.jakit.dbucket.BucketSnapshot;
import com.github.wcqtech.jakit.dbucket.BucketSpec;
import com.github.wcqtech.jakit.dbucket.BucketStore;
import com.github.wcqtech.jakit.dbucket.ConsumeResult;
import com.github.wcqtech.jakit.dbucket.CreateResult;
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.DbucketOptions;
import com.github.wcqtech.jakit.dbucket.DbucketStoreException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultDbucketTest {

    private static final BigDecimal CAPACITY = new BigDecimal("10");
    private static final BigDecimal RATE = new BigDecimal("2");
    private static final Instant NOW = Instant.parse("2026-09-10T13:00:00Z");

    private final FakeStore store = new FakeStore();
    private final FakeWait wait = new FakeWait();

    private Dbucket dbucket(DbucketOptions options) {
        return DbucketFactory.create(store, options, wait);
    }

    // ------------------------------------------------------------- addressing

    @Test
    void bucketUsesTheConfiguredDefaultNamespace() {
        store.consumeResults.add(ConsumeResult.success());

        dbucket(DbucketOptions.defaults()).bucket("orders").tryAcquire(1);

        assertEquals(List.of("default/orders"), store.attempts);
    }

    @Test
    void bucketKeepsTheExplicitNamespaceAndName() {
        store.consumeResults.add(ConsumeResult.success());
        Bucket bucket = dbucket(DbucketOptions.builder().defaultNamespace("tenant-a").build())
                .bucket("ns", "orders");

        assertEquals("ns", bucket.namespace());
        assertEquals("orders", bucket.name());

        bucket.tryAcquire(1);
        assertEquals(List.of("ns/orders"), store.attempts);
    }

    @Test
    void rejectsInvalidIdentifiersAndAmounts() {
        Dbucket dbucket = dbucket(DbucketOptions.defaults());

        assertThrows(IllegalArgumentException.class, () -> dbucket.bucket(" "));
        assertThrows(IllegalArgumentException.class, () -> dbucket.bucket("ns", null));
        assertThrows(IllegalArgumentException.class, () -> dbucket.bucket("ns", "b").tryAcquire(0));
        assertThrows(IllegalArgumentException.class, () -> dbucket.bucket("ns", "b").tryAcquire(-1));
        assertThrows(IllegalArgumentException.class, () -> dbucket.bucket("ns", "b").deposit(0));
        assertThrows(NullPointerException.class, () -> dbucket.bucket("ns", "b").acquire(1, null));
    }

    // ---------------------------------------------------------- outcome mapping

    @Test
    void mapsStoreOutcomes() {
        Dbucket dbucket = dbucket(DbucketOptions.defaults());

        store.consumeResults.add(ConsumeResult.success());
        assertEquals(AcquireResult.Outcome.SUCCESS, dbucket.bucket("b").tryAcquire(1).outcome());

        store.consumeResults.add(ConsumeResult.insufficient());
        AcquireResult insufficient = dbucket.bucket("b").tryAcquire(1);
        assertEquals(AcquireResult.Outcome.INSUFFICIENT, insufficient.outcome());
        assertFalse(insufficient.isSuccess());
        assertFalse(insufficient.degraded());

        store.consumeResults.add(ConsumeResult.notFound());
        assertEquals(AcquireResult.Outcome.NOT_FOUND, dbucket.bucket("b").tryAcquire(1).outcome());
    }

    @Test
    void exactRemainingIsOffByDefault() {
        store.consumeResults.add(ConsumeResult.success());

        dbucket(DbucketOptions.defaults()).bucket("b").tryAcquire(1);

        assertEquals(List.of(false), store.remainingFlags);
    }

    @Test
    void passesAndSurfacesTheExactRemainingBalance() {
        store.consumeResults.add(ConsumeResult.success(new BigDecimal("7.5")));

        AcquireResult result = dbucket(DbucketOptions.builder().exactRemaining(true).build())
                .bucket("b").tryAcquire(1);

        assertEquals(List.of(true), store.remainingFlags);
        assertTrue(result.hasRemaining());
        assertEquals(0, result.remaining().compareTo(new BigDecimal("7.5")));
    }

    // --------------------------------------------------------------- auto create

    @Test
    void autoCreatesAMissingBucketAndRetries() {
        DbucketOptions options = DbucketOptions.builder().autoCreateSpec(CAPACITY, RATE).build();
        store.consumeResults.add(ConsumeResult.notFound());
        store.consumeResults.add(ConsumeResult.success());

        AcquireResult result = dbucket(options).bucket("orders").tryAcquire(3);

        assertTrue(result.isSuccess());
        assertEquals(1, store.created.size());
        BucketSpec spec = store.created.get(0);
        assertEquals("default", spec.namespace());
        assertEquals("orders", spec.name());
        assertEquals(0, spec.capacity().compareTo(CAPACITY));
        assertEquals(0, spec.rate().compareTo(RATE));
        assertEquals(BucketSpec.InitialState.FULL, spec.initialState());
        assertEquals(List.of("default/orders", "default/orders"), store.attempts);
    }

    @Test
    void autoCreateCanStartEmpty() {
        DbucketOptions options = DbucketOptions.builder()
                .autoCreateSpec(CAPACITY, RATE, BucketSpec.InitialState.EMPTY).build();
        store.consumeResults.add(ConsumeResult.notFound());
        store.consumeResults.add(ConsumeResult.insufficient());

        AcquireResult result = dbucket(options).bucket("b").tryAcquire(1);

        assertEquals(AcquireResult.Outcome.INSUFFICIENT, result.outcome());
        assertEquals(BucketSpec.InitialState.EMPTY, store.created.get(0).initialState());
    }

    @Test
    void missingBucketIsNotCreatedWhenAutoCreateIsOff() {
        store.consumeResults.add(ConsumeResult.notFound());

        AcquireResult result = dbucket(DbucketOptions.defaults()).bucket("b").tryAcquire(1);

        assertEquals(AcquireResult.Outcome.NOT_FOUND, result.outcome());
        assertTrue(store.created.isEmpty());
    }

    @Test
    void autoCreateToleratesAnExistingBucketWithDifferentConfiguration() {
        DbucketOptions options = DbucketOptions.builder().autoCreateSpec(CAPACITY, RATE).build();
        BucketSnapshot existing = new BucketSnapshot("default", "b", new BigDecimal("99"),
                new BigDecimal("9"), new BigDecimal("99"), new BigDecimal("99"), NOW);
        store.createResult = CreateResult.exists(existing);
        store.consumeResults.add(ConsumeResult.notFound());
        store.consumeResults.add(ConsumeResult.success());

        assertTrue(dbucket(options).bucket("b").tryAcquire(1).isSuccess());
        assertEquals(1, store.created.size());
    }

    @Test
    void autoCreateRejectsASpecForAnotherBucket() {
        DbucketOptions options = DbucketOptions.builder()
                .autoCreateSpec((namespace, name) -> BucketSpec.of("other", name, CAPACITY, RATE))
                .build();
        store.consumeResults.add(ConsumeResult.notFound());

        assertThrows(IllegalStateException.class, () -> dbucket(options).bucket("b").tryAcquire(1));
    }

    // ---------------------------------------------------------------- fail open

    @Test
    void failOpenTurnsAStorageFailureIntoLoudSuccess() {
        store.consumeResults.add(new DbucketStoreException("database down"));

        AcquireResult result = dbucket(DbucketOptions.defaults()).bucket("b").tryAcquire(1);

        assertTrue(result.isSuccess());
        assertTrue(result.degraded());
        assertFalse(result.hasRemaining());
    }

    @Test
    void failClosedPropagatesTheStorageFailure() {
        store.consumeResults.add(new DbucketStoreException("database down"));
        Dbucket dbucket = dbucket(DbucketOptions.builder().failOpen(false).build());

        assertThrows(DbucketStoreException.class, () -> dbucket.bucket("b").tryAcquire(1));
    }

    @Test
    void failOpenAlsoAppliesToTheBlockingAcquire() {
        store.consumeResults.add(new DbucketStoreException("database down"));

        assertTrue(dbucket(DbucketOptions.defaults()).bucket("b").acquire(1, Duration.ofMillis(50)));
        assertTrue(wait.sleeps.isEmpty());
    }

    @Test
    void readsAndDepositsNeverFailOpen() {
        store.failGet = true;
        store.failDeposit = true;
        Dbucket dbucket = dbucket(DbucketOptions.defaults());

        assertThrows(DbucketStoreException.class, () -> dbucket.bucket("b").snapshot());
        assertThrows(DbucketStoreException.class, () -> dbucket.bucket("b").deposit(1));
    }

    // ----------------------------------------------------------------- blocking

    @Test
    void blockingAcquireReturnsImmediatelyOnSuccess() {
        store.consumeResults.add(ConsumeResult.success());

        assertTrue(dbucket(DbucketOptions.defaults()).bucket("b").acquire(1, Duration.ofMillis(100)));
        assertTrue(wait.sleeps.isEmpty());
        assertEquals(1, store.attempts.size());
    }

    @Test
    void blockingAcquireSpendsTheWholeBudgetWithoutResettingIt() {
        DbucketOptions options = DbucketOptions.builder()
                .jitter(false).pollInterval(Duration.ofMillis(20)).build();

        boolean acquired = dbucket(options).bucket("b").acquire(1, Duration.ofMillis(100));

        assertFalse(acquired);
        assertEquals(Duration.ofMillis(100).toNanos(), wait.now);
        assertEquals(List.of(20L, 20L, 20L, 20L, 20L), wait.sleepMillis());
        assertEquals(6, store.attempts.size());
    }

    @Test
    void blockingAcquireClampsTheLastSleepToTheRemainingBudget() {
        DbucketOptions options = DbucketOptions.builder()
                .jitter(false).pollInterval(Duration.ofMillis(30)).build();

        dbucket(options).bucket("b").acquire(1, Duration.ofMillis(50));

        assertEquals(List.of(30L, 20L), wait.sleepMillis());
        assertEquals(Duration.ofMillis(50).toNanos(), wait.now);
    }

    @Test
    void blockingAcquireStopsImmediatelyOnAMissingBucket() {
        store.consumeResults.add(ConsumeResult.notFound());

        assertFalse(dbucket(DbucketOptions.defaults()).bucket("b").acquire(1, Duration.ofSeconds(1)));
        assertTrue(wait.sleeps.isEmpty());
        assertEquals(1, store.attempts.size());
    }

    @Test
    void blockingAcquireJittersThePollIntervalBetweenHalfAndOneAndAHalf() {
        DbucketOptions options = DbucketOptions.builder()
                .jitter(true).pollInterval(Duration.ofMillis(20)).build();

        assertFalse(dbucket(options).bucket("b").acquire(1, Duration.ofMillis(200)));

        assertTrue(wait.sleeps.size() > 1, "expected several polls");
        long budget = Duration.ofMillis(200).toNanos();
        long elapsed = 0;
        for (Duration sleep : wait.sleeps) {
            assertTrue(sleep.toNanos() > 0, "the poll must still sleep: " + sleep);
            assertTrue(sleep.toNanos() <= Duration.ofMillis(30).toNanos(), "too long: " + sleep);
            // The 50%..150% jitter bound only applies while a full interval still fits in the budget;
            // afterwards the sleep is clamped to whatever is left.
            if (budget - elapsed >= Duration.ofMillis(20).toNanos()) {
                assertTrue(sleep.toNanos() >= Duration.ofMillis(10).toNanos(), "too short: " + sleep);
            }
            elapsed += sleep.toNanos();
        }
        assertEquals(budget, wait.now, "the whole budget is spent");
    }

    @Test
    void blockingAcquireRestoresTheInterruptFlag() {
        store.consumeResults.add(ConsumeResult.insufficient());
        wait.interruptOnFirstSleep = true;
        try {
            assertFalse(dbucket(DbucketOptions.defaults()).bucket("b")
                    .acquire(1, Duration.ofSeconds(1)));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void blockingAcquireWorksTogetherWithAutoCreate() {
        DbucketOptions options = DbucketOptions.builder().autoCreateSpec(CAPACITY, RATE).build();
        store.consumeResults.add(ConsumeResult.notFound());
        store.consumeResults.add(ConsumeResult.insufficient());
        store.consumeResults.add(ConsumeResult.success());

        assertTrue(dbucket(options).bucket("b").acquire(1, Duration.ofMillis(100)));
        assertEquals(1, store.created.size());
    }

    @Test
    void blockingAcquireWithZeroTimeoutStillTriesOnce() {
        store.consumeResults.add(ConsumeResult.success());

        assertTrue(dbucket(DbucketOptions.defaults()).bucket("b").acquire(1, Duration.ZERO));
        assertEquals(1, store.attempts.size());
        assertTrue(wait.sleeps.isEmpty());
    }

    @Test
    void acquireResultReportsWhyTheWaitEnded() {
        DbucketOptions options = DbucketOptions.builder()
                .jitter(false).pollInterval(Duration.ofMillis(20)).build();

        AcquireResult result = dbucket(options).bucket("b").acquireResult(1, Duration.ofMillis(60));

        assertEquals(AcquireResult.Outcome.INSUFFICIENT, result.outcome());
        assertEquals(Duration.ofMillis(60), result.waited());
        assertFalse(result.degraded());
    }

    @Test
    void acquireResultReportsTheTotalWaitWhenItEventuallySucceeds() {
        store.consumeResults.add(ConsumeResult.insufficient());
        store.consumeResults.add(ConsumeResult.success(new BigDecimal("4")));
        DbucketOptions options = DbucketOptions.builder()
                .jitter(false).pollInterval(Duration.ofMillis(20)).build();

        AcquireResult result = dbucket(options).bucket("b").acquireResult(1, Duration.ofSeconds(1));

        assertTrue(result.isSuccess());
        assertEquals(Duration.ofMillis(20), result.waited());
        assertEquals(0, result.remaining().compareTo(new BigDecimal("4")));
    }

    @Test
    void acquireResultReportsAMissingBucketImmediately() {
        store.consumeResults.add(ConsumeResult.notFound());

        AcquireResult result = dbucket(DbucketOptions.defaults()).bucket("b")
                .acquireResult(1, Duration.ofSeconds(1));

        assertEquals(AcquireResult.Outcome.NOT_FOUND, result.outcome());
        assertTrue(wait.sleeps.isEmpty());
    }

    @Test
    void acquireResultReportsTheFailedOpenDecision() {
        store.consumeResults.add(new DbucketStoreException("database down"));

        AcquireResult result = dbucket(DbucketOptions.defaults()).bucket("b")
                .acquireResult(1, Duration.ofMillis(50));

        assertTrue(result.isSuccess());
        assertTrue(result.degraded());
    }

    // ------------------------------------------------------------ admin / reads

    @Test
    void adminDelegatesToTheStore() {
        Dbucket dbucket = dbucket(DbucketOptions.defaults());
        BucketSpec spec = BucketSpec.of("ns", "b", CAPACITY, RATE);

        assertTrue(dbucket.admin().create(spec).isCreated());
        assertTrue(dbucket.admin().adjustCapacity("ns", "b", new BigDecimal("5")));
        assertTrue(dbucket.admin().adjustRate("ns", "b", new BigDecimal("3")));
        assertTrue(dbucket.admin().delete("ns", "b"));

        assertEquals(List.of(spec), store.created);
    }

    @Test
    void snapshotAndDepositDelegateToTheStore() {
        Dbucket dbucket = dbucket(DbucketOptions.defaults());
        store.snapshot = Optional.of(new BucketSnapshot("ns", "b", CAPACITY, RATE,
                new BigDecimal("4"), new BigDecimal("4"), NOW));

        Bucket bucket = dbucket.bucket("ns", "b");

        assertEquals(0, bucket.snapshot().orElseThrow().tokens().compareTo(new BigDecimal("4")));
        assertTrue(bucket.deposit(2));
    }

    // ----------------------------------------------------------------- options

    @Test
    void optionsMatchTheDesignDocumentDefaults() {
        DbucketOptions defaults = DbucketOptions.defaults();

        assertEquals("default", defaults.defaultNamespace());
        assertFalse(defaults.autoCreate());
        assertTrue(defaults.failOpen());
        assertFalse(defaults.exactRemaining());
        assertEquals(Duration.ofMillis(20), defaults.pollInterval());
        assertTrue(defaults.jitter());
    }

    @Test
    void optionsValidateTheirInput() {
        assertThrows(IllegalStateException.class, () -> DbucketOptions.builder().autoCreate(true).build());
        assertThrows(IllegalArgumentException.class,
                () -> DbucketOptions.builder().pollInterval(Duration.ZERO).build());
        assertThrows(IllegalArgumentException.class,
                () -> DbucketOptions.builder().pollInterval(Duration.ofMillis(-5)).build());
        assertThrows(IllegalArgumentException.class,
                () -> DbucketOptions.builder().defaultNamespace(" ").build());
        assertThrows(IllegalArgumentException.class,
                () -> DbucketOptions.builder().autoCreateSpec(BigDecimal.ZERO, RATE));
        assertThrows(NullPointerException.class, () -> DbucketOptions.builder().pollInterval(null).build());
    }

    // ----------------------------------------------------------------- fakes

    private static final class FakeStore implements BucketStore {

        final List<String> attempts = new ArrayList<>();
        final List<Boolean> remainingFlags = new ArrayList<>();
        final List<BucketSpec> created = new ArrayList<>();
        final Deque<Object> consumeResults = new ArrayDeque<>();
        Optional<BucketSnapshot> snapshot = Optional.empty();
        CreateResult createResult = CreateResult.created();
        boolean failGet;
        boolean failDeposit;

        @Override
        public CreateResult createIfAbsent(BucketSpec spec) {
            created.add(spec);
            if (createResult.isCreated()) {
                snapshot = Optional.of(new BucketSnapshot(spec.namespace(), spec.name(), spec.capacity(),
                        spec.rate(), spec.initialTokens(), spec.initialTokens(), NOW));
            }
            return createResult;
        }

        @Override
        public Optional<BucketSnapshot> get(String namespace, String name) {
            if (failGet) {
                throw new DbucketStoreException("get failed");
            }
            return snapshot;
        }

        @Override
        public ConsumeResult tryConsume(String namespace, String name, long tokens,
                                        boolean withRemaining) {
            attempts.add(namespace + "/" + name);
            remainingFlags.add(withRemaining);
            Object next = consumeResults.poll();
            if (next instanceof DbucketStoreException failure) {
                throw failure;
            }
            return next == null ? ConsumeResult.insufficient() : (ConsumeResult) next;
        }

        @Override
        public boolean deposit(String namespace, String name, long tokens) {
            if (failDeposit) {
                throw new DbucketStoreException("deposit failed");
            }
            return true;
        }

        @Override
        public boolean adjustCapacity(String namespace, String name, BigDecimal capacity) {
            return true;
        }

        @Override
        public boolean adjustRate(String namespace, String name, BigDecimal rate) {
            return true;
        }

        @Override
        public boolean delete(String namespace, String name) {
            return true;
        }
    }

    /** Virtual clock: sleeping advances time instead of blocking the test. */
    private static final class FakeWait implements WaitSupport {

        final List<Duration> sleeps = new ArrayList<>();
        long now;
        boolean interruptOnFirstSleep;

        @Override
        public long nanoTime() {
            return now;
        }

        @Override
        public void sleep(Duration duration) throws InterruptedException {
            if (interruptOnFirstSleep) {
                interruptOnFirstSleep = false;
                throw new InterruptedException("test interrupt");
            }
            sleeps.add(duration);
            now += duration.toNanos();
        }

        List<Long> sleepMillis() {
            return sleeps.stream().map(Duration::toMillis).toList();
        }
    }
}
