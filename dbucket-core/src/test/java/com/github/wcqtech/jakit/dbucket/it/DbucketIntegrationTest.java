package com.github.wcqtech.jakit.dbucket.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.wcqtech.jakit.dbucket.AcquireResult;
import com.github.wcqtech.jakit.dbucket.BucketSpec;
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.DbucketOptions;
import com.github.wcqtech.jakit.dbucket.store.JdbcBucketStore;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Opt-in end-to-end test of the facade on real databases: real lazy refill, the total wait budget,
 * auto-creation, exact remaining and delete-priority semantics.
 */
class DbucketIntegrationTest {

    private static final String TABLE = "dbucket_facade_it_bucket";
    private static final String NS = "it";

    @Test
    void facadeBehavesOnRealDatabases() throws Exception {
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

    private void verify(IntegrationConfig.Target target, DataSource dataSource) {
        JdbcBucketStore store = JdbcBucketStore.detect(dataSource, TABLE);
        store.createTable();
        Dbucket dbucket = Dbucket.create(store, DbucketOptions.builder().defaultNamespace(NS).build());

        // 1. blocking acquire waits for real refill: empty bucket, 50 tokens/s -> one token in ~20 ms
        dbucket.admin().delete(NS, "refill");
        dbucket.admin().create(new BucketSpec(NS, "refill", new BigDecimal("5"), new BigDecimal("50"),
                BucketSpec.InitialState.EMPTY));
        long start = System.nanoTime();
        assertTrue(dbucket.bucket("refill").acquire(1, Duration.ofSeconds(5)),
                target.key() + ": blocking acquire must wait for refill");
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 4000,
                target.key() + ": refill should be quick");

        // 2. the budget is honoured when nothing can ever arrive: empty bucket, zero rate
        dbucket.admin().delete(NS, "empty");
        dbucket.admin().create(new BucketSpec(NS, "empty", new BigDecimal("5"), BigDecimal.ZERO,
                BucketSpec.InitialState.EMPTY));
        start = System.nanoTime();
        assertFalse(dbucket.bucket("empty").acquire(1, Duration.ofMillis(150)),
                target.key() + ": an empty bucket with no refill cannot succeed");
        long waited = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertTrue(waited >= 140, target.key() + ": returned before the budget was spent (" + waited + " ms)");
        assertTrue(waited < 1500, target.key() + ": returned long after the budget (" + waited + " ms)");

        // 3. auto-creation followed by an immediate acquire
        Dbucket auto = Dbucket.create(store, DbucketOptions.builder().defaultNamespace(NS)
                .autoCreateSpec(new BigDecimal("3"), BigDecimal.ZERO).build());
        auto.admin().delete(NS, "auto");
        assertTrue(auto.bucket("auto").tryAcquire(1).isSuccess(),
                target.key() + ": auto-created bucket must serve the first acquire");
        assertEquals(0, auto.bucket("auto").snapshot().orElseThrow().tokens().compareTo(new BigDecimal("2")),
                target.key() + ": auto-created balance");

        // 4. exact remaining surfaced through the facade
        Dbucket exact = Dbucket.create(store, DbucketOptions.builder().defaultNamespace(NS)
                .exactRemaining(true).build());
        AcquireResult result = exact.bucket("auto").tryAcquire(1);
        assertTrue(result.isSuccess(), target.key() + ": exact acquire");
        assertTrue(result.hasRemaining(), target.key() + ": remaining surfaced");
        assertEquals(0, result.remaining().compareTo(new BigDecimal("1")),
                target.key() + ": remaining value was " + result.remaining());

        // 5. delete-priority: a waiting acquire stops as soon as the bucket is gone
        dbucket.admin().delete(NS, "auto");
        start = System.nanoTime();
        assertFalse(dbucket.bucket("auto").acquire(1, Duration.ofSeconds(1)),
                target.key() + ": deleted bucket must not be acquired");
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 1000,
                target.key() + ": deleted bucket must fail fast");
    }

    private static void dropTable(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + TABLE);
        }
    }
}
