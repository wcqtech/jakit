package com.github.wcqtech.jakit.dbucket.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.wcqtech.jakit.dbucket.BucketSpec;
import com.github.wcqtech.jakit.dbucket.DbucketOptions;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DbucketOptionsFactoryTest {

    @Test
    void mapsEveryOption() {
        DbucketProperties properties = new DbucketProperties();
        properties.setNamespace("tenant-a");
        properties.setFailOpen(false);
        properties.setExactRemaining(true);
        properties.getWait().setPollInterval(Duration.ofMillis(50));
        properties.getWait().setJitter(false);
        properties.setAutoCreate(false);

        DbucketOptions options = DbucketOptionsFactory.toOptions(properties);

        assertEquals("tenant-a", options.defaultNamespace());
        assertFalse(options.failOpen());
        assertTrue(options.exactRemaining());
        assertEquals(Duration.ofMillis(50), options.pollInterval());
        assertFalse(options.jitter());
        assertFalse(options.autoCreate());
    }

    @Test
    void enablesAutoCreateWhenCapacityIsConfigured() {
        DbucketProperties properties = new DbucketProperties();
        properties.getCreate().setCapacity(new BigDecimal("10"));
        properties.getCreate().setRate(new BigDecimal("2"));
        properties.getCreate().setInitial(BucketSpec.InitialState.EMPTY);

        DbucketOptions options = DbucketOptionsFactory.toOptions(properties);

        assertTrue(options.autoCreate());
        BucketSpec spec = options.specFactory().create("ns", "b");
        assertEquals("ns", spec.namespace());
        assertEquals(0, spec.capacity().compareTo(new BigDecimal("10")));
        assertEquals(0, spec.rate().compareTo(new BigDecimal("2")));
        assertEquals(BucketSpec.InitialState.EMPTY, spec.initialState());
    }

    @Test
    void disablesAutoCreateWhenCapacityIsMissing() {
        DbucketProperties properties = new DbucketProperties();

        DbucketOptions options = DbucketOptionsFactory.toOptions(properties);

        assertFalse(options.autoCreate());
        assertNull(options.specFactory());
    }

    @Test
    void respectsAutoCreateDisabledWithCapacityConfigured() {
        DbucketProperties properties = new DbucketProperties();
        properties.setAutoCreate(false);
        properties.getCreate().setCapacity(new BigDecimal("10"));

        assertFalse(DbucketOptionsFactory.toOptions(properties).autoCreate());
    }

    @Test
    void rejectsAnInvalidCapacityAtStartup() {
        DbucketProperties properties = new DbucketProperties();
        properties.getCreate().setCapacity(BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class, () -> DbucketOptionsFactory.toOptions(properties));
    }

    @Test
    void defaultsMatchTheDesignDocument() {
        DbucketOptions options = DbucketOptionsFactory.toOptions(new DbucketProperties());

        assertEquals("default", options.defaultNamespace());
        assertTrue(options.failOpen());
        assertFalse(options.exactRemaining());
        assertEquals(Duration.ofMillis(20), options.pollInterval());
        assertTrue(options.jitter());
    }
}
