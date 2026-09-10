package com.github.wcqtech.jakit.dbucket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SpiValueTypesTest {

    private static BucketSnapshot snapshot(BigDecimal tokens) {
        return new BucketSnapshot("ns", "b", new BigDecimal("10"), new BigDecimal("1.5"),
                tokens, new BigDecimal("3"), Instant.parse("2026-09-10T13:20:19Z"));
    }

    @Test
    void snapshotExposesEffectiveTokensForConsumptionChecks() {
        BucketSnapshot snapshot = snapshot(new BigDecimal("4.5"));

        assertTrue(snapshot.canConsume(4));
        assertFalse(snapshot.canConsume(5));
    }

    @Test
    void snapshotCanonicalizesDecimals() {
        BucketSnapshot snapshot = snapshot(new BigDecimal("4.5000000"));

        assertEquals("4.5", snapshot.tokens().toPlainString());
    }

    @Test
    void snapshotRejectsMissingFields() {
        assertThrows(IllegalArgumentException.class, () -> new BucketSnapshot(
                "ns", "b", new BigDecimal("10"), new BigDecimal("1"),
                BigDecimal.ONE, BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> new BucketSnapshot(
                "ns", "b", BigDecimal.ZERO, new BigDecimal("1"),
                BigDecimal.ONE, BigDecimal.ONE, Instant.EPOCH));
    }

    @Test
    void consumeSuccessWithoutRemaining() {
        ConsumeResult result = ConsumeResult.success();

        assertTrue(result.isSuccess());
        assertFalse(result.hasRemaining());
        assertNull(result.remaining());
    }

    @Test
    void consumeSuccessWithRemaining() {
        ConsumeResult result = ConsumeResult.success(new BigDecimal("2.5000000"));

        assertTrue(result.isSuccess());
        assertTrue(result.hasRemaining());
        assertEquals("2.5", result.remaining().toPlainString());
    }

    @Test
    void consumeFailureFactoriesCarryNoRemaining() {
        assertEquals(ConsumeResult.Outcome.INSUFFICIENT, ConsumeResult.insufficient().outcome());
        assertEquals(ConsumeResult.Outcome.NOT_FOUND, ConsumeResult.notFound().outcome());
        assertFalse(ConsumeResult.insufficient().hasRemaining());
        assertFalse(ConsumeResult.notFound().hasRemaining());
    }

    @Test
    void remainingIsRejectedOutsideSuccess() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumeResult(ConsumeResult.Outcome.INSUFFICIENT, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumeResult(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> ConsumeResult.success(new BigDecimal("-1")));
    }

    @Test
    void acquireExceptionCarriesTheRejectionContext() {
        AcquireResult rejection = AcquireResult.insufficient(Duration.ofMillis(150));

        BucketAcquireException exception = new BucketAcquireException("ns", "orders", 3, rejection);

        assertEquals("ns", exception.getNamespace());
        assertEquals("orders", exception.getName());
        assertEquals(3, exception.getTokens());
        assertEquals(rejection, exception.getResult());
        assertTrue(exception.getMessage().contains("ns/orders"), exception.getMessage());
        assertTrue(exception.getMessage().contains("INSUFFICIENT"), exception.getMessage());
        assertTrue(exception.getMessage().contains("150"), exception.getMessage());
    }

    @Test
    void createResultRequiresSnapshotWhenBucketExists() {
        assertThrows(IllegalArgumentException.class,
                () -> new CreateResult(CreateResult.Outcome.EXISTS, null));

        CreateResult exists = CreateResult.exists(snapshot(BigDecimal.TEN));
        assertFalse(exists.isCreated());
        assertEquals(CreateResult.Outcome.EXISTS, exists.outcome());
    }

    @Test
    void createResultAllowsCreatedWithoutSnapshot() {
        CreateResult created = CreateResult.created();

        assertTrue(created.isCreated());
        assertNull(created.snapshot());
    }
}
