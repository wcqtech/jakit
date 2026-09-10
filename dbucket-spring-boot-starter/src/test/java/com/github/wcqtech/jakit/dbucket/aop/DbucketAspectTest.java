package com.github.wcqtech.jakit.dbucket.aop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.wcqtech.jakit.dbucket.AcquireResult;
import com.github.wcqtech.jakit.dbucket.BucketAcquireException;
import com.github.wcqtech.jakit.dbucket.BucketSpec;
import com.github.wcqtech.jakit.dbucket.ConsumeResult;
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.annotation.DBucket;
import com.github.wcqtech.jakit.dbucket.autoconfigure.DbucketAutoConfiguration;
import com.github.wcqtech.jakit.dbucket.test.FakeBucketStore;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

class DbucketAspectTest {

    private final FakeBucketStore store = new FakeBucketStore();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DbucketAutoConfiguration.class))
            .withUserConfiguration(AopConfiguration.class)
            .withBean("bucketStore", FakeBucketStore.class, () -> store)
            .withPropertyValues("jakit.dbucket.wait.poll-interval=5ms", "jakit.dbucket.wait.jitter=false");

    @Test
    void proceedsWhenTokensAreAcquired() {
        store.consumeResults.add(ConsumeResult.success());

        runner.run(context -> assertEquals("placed", context.getBean(GateService.class).placeOrder()));
        assertEquals(List.of("default/orders:1"), store.consumeCalls);
    }

    @Test
    void throwsWhenTheAcquireIsRejected() {
        store.consumeResults.add(ConsumeResult.insufficient());

        runner.run(context -> {
            GateService service = context.getBean(GateService.class);
            BucketAcquireException failure = assertThrows(BucketAcquireException.class,
                    service::placeOrder);
            assertEquals("default", failure.getNamespace());
            assertEquals("orders", failure.getName());
            assertEquals(1, failure.getTokens());
            assertEquals(AcquireResult.Outcome.INSUFFICIENT, failure.getResult().outcome());
        });
    }

    @Test
    void throwsOnAMissingBucket() {
        store.consumeResults.add(ConsumeResult.notFound());

        runner.run(context -> {
            GateService service = context.getBean(GateService.class);
            BucketAcquireException failure = assertThrows(BucketAcquireException.class,
                    service::placeOrder);
            assertEquals(AcquireResult.Outcome.NOT_FOUND, failure.getResult().outcome());
        });
    }

    @Test
    void ignoreFailureLetsTheMethodRun() {
        store.consumeResults.add(ConsumeResult.insufficient());

        runner.run(context -> assertEquals("tolerant", context.getBean(GateService.class).tolerant()));
    }

    @Test
    void resolvesSpelBucketNameAndTokens() {
        store.consumeResults.add(ConsumeResult.success());

        runner.run(context -> assertEquals("spel", context.getBean(GateService.class).spel("tenant-a", 3)));
        assertEquals(List.of("default/tenant-a:orders:3"), store.consumeCalls);
    }

    @Test
    void resolvesSpelNamespace() {
        store.consumeResults.add(ConsumeResult.success());

        runner.run(context -> assertEquals("namespaced", context.getBean(GateService.class).namespaced("tenant-b")));
        assertEquals(List.of("tenant-b/orders:1"), store.consumeCalls);
    }

    @Test
    void resolvesTheBraceExpressionForm() {
        store.consumeResults.add(ConsumeResult.success());

        runner.run(context -> assertEquals("brace", context.getBean(GateService.class).brace()));
        assertEquals(List.of("default/orders:2"), store.consumeCalls);
    }

    @Test
    void supportsPositionalParameterAliases() {
        store.consumeResults.add(ConsumeResult.success());

        runner.run(context -> assertEquals("positional", context.getBean(GateService.class).positional(4)));
        assertEquals(List.of("default/orders:4"), store.consumeCalls);
    }

    @Test
    void failsLoudlyWhenAnExpressionEvaluatesToNull() {
        runner.run(context -> {
            GateService service = context.getBean(GateService.class);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    service::brokenSpel);
            assertTrue(failure.getMessage().contains("missingName"), failure.getMessage());
            assertTrue(failure.getMessage().contains("-parameters"), failure.getMessage());
        });
        assertTrue(store.consumeCalls.isEmpty());
    }

    @Test
    void blocksUntilTheConfiguredTimeoutExpires() {
        store.consumeResults.add(ConsumeResult.insufficient());
        store.consumeResults.add(ConsumeResult.success());

        runner.run(context -> assertEquals("blocking", context.getBean(GateService.class).blocking()));
        assertTrue(store.consumeCalls.size() >= 2, "the blocking acquire must poll again");
    }

    @Test
    void autoCreateFollowsTheGlobalPolicy() {
        store.consumeResults.add(ConsumeResult.notFound());
        store.consumeResults.add(ConsumeResult.success());

        runner.withPropertyValues("jakit.dbucket.create.capacity=10", "jakit.dbucket.create.rate=0")
                .run(context -> assertEquals("placed", context.getBean(GateService.class).placeOrder()));
        assertEquals(1, store.created.size());
        BucketSpec spec = store.created.get(0);
        assertEquals("default", spec.namespace());
        assertEquals("orders", spec.name());
        assertEquals(0, spec.capacity().compareTo(new BigDecimal("10")));
    }

    @Test
    void aMethodWithoutTheAnnotationIsUntouched() {
        runner.run(context -> assertEquals("plain", context.getBean(GateService.class).plain()));
        assertTrue(store.consumeCalls.isEmpty());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class AopConfiguration {

        @Bean
        GateService gateService() {
            return new GateService();
        }
    }

    /** Test service: every gated method is public so the proxy can advise it. */
    public static class GateService {

        @DBucket("orders")
        public String placeOrder() {
            return "placed";
        }

        @DBucket(value = "#tenant + ':orders'", tokens = "#items")
        public String spel(String tenant, int items) {
            return "spel";
        }

        @DBucket(value = "orders", namespace = "#tenant")
        public String namespaced(String tenant) {
            return "namespaced";
        }

        @DBucket(value = "orders", tokens = "#{1 + 1}")
        public String brace() {
            return "brace";
        }

        @DBucket(value = "orders", tokens = "#a0")
        public String positional(int items) {
            return "positional";
        }

        @DBucket(value = "orders", tokens = "#missingName")
        public String brokenSpel() {
            return "broken";
        }

        @DBucket(value = "orders", timeoutMs = 500)
        public String blocking() {
            return "blocking";
        }

        @DBucket(value = "orders", ignoreFailure = true)
        public String tolerant() {
            return "tolerant";
        }

        public String plain() {
            return "plain";
        }
    }
}
