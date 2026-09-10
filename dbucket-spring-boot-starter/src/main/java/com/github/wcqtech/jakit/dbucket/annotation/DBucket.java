package com.github.wcqtech.jakit.dbucket.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gates a method on a token bucket: the method only runs when the tokens could be acquired.
 *
 * <pre>{@code
 * @DBucket("orders")
 * public void placeOrder(Order order) { ... }
 *
 * @DBucket(value = "#order.tenantId + ':orders'", tokens = "#order.items.size()", timeoutMs = 200)
 * public void submit(Order order) { ... }
 * }
 * </pre>
 *
 * <p>When the acquire is rejected the method is not invoked and a
 * {@link com.github.wcqtech.jakit.dbucket.BucketAcquireException} is thrown, unless
 * {@link #ignoreFailure()} is set.
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Only works on Spring beans called through the proxy: self-invocation bypasses the aspect.</li>
 *   <li>Parameter names in SpEL ({@code #order}, {@code #tenant}) require the application to be compiled
 *       with {@code -parameters}; spring-boot-starter-parent enables it, plain Maven builds need
 *       {@code <parameters>true</parameters>} in the compiler plugin. Otherwise use the positional
 *       aliases {@code #a0}, {@code #a1}, ...</li>
 *   <li>Auto-creation follows the global {@code jakit.dbucket.auto-create} policy; the annotation does
 *       not carry a bucket definition. Define buckets through {@code DbucketAdmin} or the global
 *       {@code jakit.dbucket.create.*} properties.</li>
 *   <li>Only method-level annotations are supported.</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DBucket {

    /**
     * Bucket name. Either a literal or a SpEL expression when it starts with {@code #}
     * ({@code #{...}} is also accepted), for example {@code "#order.tenantId + ':orders'"}.
     */
    String value();

    /**
     * Bucket namespace; empty means the configured default namespace
     * ({@code jakit.dbucket.namespace}). Literal or SpEL, as with {@link #value()}.
     */
    String namespace() default "";

    /**
     * Tokens to consume: a whole-number literal or a SpEL expression that evaluates to a number.
     */
    String tokens() default "1";

    /**
     * Total wait budget in milliseconds. {@code 0} (the default) tries once and fails immediately;
     * a positive value blocks up to that budget. Blocking occupies the calling thread, so use it
     * deliberately.
     */
    long timeoutMs() default 0;

    /**
     * When {@code true} the method proceeds even if the tokens could not be acquired (the rejection is
     * logged at WARN). Use it for non-critical paths that must not be blocked by the limiter.
     */
    boolean ignoreFailure() default false;
}
