package com.github.wcqtech.jakit.dbucket.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.wcqtech.jakit.dbucket.ConsumeResult;
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.DbucketStoreException;
import com.github.wcqtech.jakit.dbucket.test.FakeBucketStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MeteredDbucketTest {

    private final FakeBucketStore store = new FakeBucketStore();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Dbucket metered = MeteredDbucket.wrap(Dbucket.create(store), registry);
    private double acquireCount(String outcome) {
        var counter = registry.find("dbucket.acquire")
                .tags("namespace", "default", "bucket", "orders", "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void countsSuccessAndRecordsTheDuration() {
        store.consumeResults.add(ConsumeResult.success());

        metered.bucket("orders").tryAcquire(1);

        assertEquals(1.0, acquireCount("success"));
        var timer = registry.find("dbucket.acquire.duration")
                .tags("namespace", "default", "bucket", "orders", "outcome", "success").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void distinguishesEveryOutcome() {
        store.consumeResults.add(ConsumeResult.insufficient());
        store.consumeResults.add(ConsumeResult.notFound());
        store.consumeResults.add(new DbucketStoreException("database down"));

        metered.bucket("orders").tryAcquire(1);
        metered.bucket("orders").tryAcquire(1);
        metered.bucket("orders").tryAcquire(1);

        assertEquals(1.0, acquireCount("insufficient"));
        assertEquals(1.0, acquireCount("not_found"));
        assertEquals(1.0, acquireCount("degraded"));
        assertNull(registry.find("dbucket.acquire.errors").counter());
    }

    @Test
    void countsStorageErrorsWhenFailOpenIsDisabled() {
        store.consumeResults.add(new DbucketStoreException("database down"));
        Dbucket strict = MeteredDbucket.wrap(
                Dbucket.create(store, com.github.wcqtech.jakit.dbucket.DbucketOptions.builder()
                        .failOpen(false).build()),
                registry);

        assertThrows(DbucketStoreException.class, () -> strict.bucket("orders").tryAcquire(1));

        var errors = registry.find("dbucket.acquire.errors")
                .tags("namespace", "default", "bucket", "orders").counter();
        assertNotNull(errors);
        assertEquals(1.0, errors.count());
        assertEquals(0.0, acquireCount("success"));
    }

    @Test
    void recordsBlockingAcquiresOnce() {
        store.consumeResults.add(ConsumeResult.success());

        assertTrue(metered.bucket("orders").acquire(1, Duration.ofMillis(50)));

        assertEquals(1.0, acquireCount("success"));
    }

    @Test
    void doesNotMeterReadsDepositsOrAdminCalls() {
        metered.bucket("orders").snapshot();
        metered.bucket("orders").deposit(1);
        metered.admin().delete("default", "orders");

        assertTrue(registry.find("dbucket.acquire").counters().isEmpty());
    }
}
