package com.github.wcqtech.jakit.dbucket.aop;

import com.github.wcqtech.jakit.dbucket.AcquireResult;
import com.github.wcqtech.jakit.dbucket.Bucket;
import com.github.wcqtech.jakit.dbucket.BucketAcquireException;
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.annotation.DBucket;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * Implements {@link DBucket}: acquires tokens before invoking the method and throws
 * {@link BucketAcquireException} when the acquire is rejected.
 *
 * <p>The default order is {@code Ordered.LOWEST_PRECEDENCE - 100}, i.e. the advice runs
 * <em>outside</em> Spring's transaction advice: tokens are acquired before a transaction is opened, so
 * the bucket row lock never overlaps a business transaction.
 *
 * <p>Only Spring-proxied calls are gated: self-invocation inside the same bean bypasses the aspect.
 */
@Aspect
public class DbucketAspect implements Ordered {

    private static final Log LOG = LogFactory.getLog(DbucketAspect.class);
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();

    private final Dbucket dbucket;
    private final int order;
    private final Map<Method, CompiledAnnotation> cache = new ConcurrentHashMap<>();

    /**
     * @param dbucket facade used to acquire tokens
     * @param order   advice order; lower values run earlier (outside the transaction advice)
     */
    public DbucketAspect(Dbucket dbucket, int order) {
        this.dbucket = Objects.requireNonNull(dbucket, "dbucket must not be null");
        this.order = order;
    }

    @Around("@annotation(com.github.wcqtech.jakit.dbucket.annotation.DBucket)")
    public Object gate(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        DBucket annotation = AnnotatedElementUtils.findMergedAnnotation(method, DBucket.class);
        if (annotation == null) {
            return joinPoint.proceed();
        }

        EvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(), method, joinPoint.getArgs(), PARAMETER_NAMES);
        CompiledAnnotation compiled = cache.computeIfAbsent(method,
                key -> CompiledAnnotation.of(annotation));

        String namespace = stringValue(compiled.namespace(), context, annotation.namespace());
        String name = stringValue(compiled.name(), context, annotation.value());
        long tokens = longValue(compiled.tokens(), context, annotation.tokens());
        Bucket bucket = namespace == null || namespace.isBlank()
                ? dbucket.bucket(name)
                : dbucket.bucket(namespace, name);

        AcquireResult result = annotation.timeoutMs() > 0
                ? bucket.acquireResult(tokens, Duration.ofMillis(annotation.timeoutMs()))
                : bucket.tryAcquire(tokens);
        if (result.isSuccess()) {
            return joinPoint.proceed();
        }
        if (annotation.ignoreFailure()) {
            LOG.warn("dbucket let " + method.getName() + " proceed although acquiring " + tokens
                    + " token(s) from " + bucket.namespace() + "/" + bucket.name() + " was rejected ("
                    + result.outcome() + ")"
                    + (result.degraded() ? " [degraded]" : ""));
            return joinPoint.proceed();
        }
        throw new BucketAcquireException(bucket.namespace(), bucket.name(), tokens, result);
    }

    @Override
    public int getOrder() {
        return order;
    }

    private static Method resolveMethod(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        return target == null ? method : AopUtils.getMostSpecificMethod(method, target.getClass());
    }

    private static String stringValue(Expression expression, EvaluationContext context, String literal) {
        if (expression == null) {
            return literal;
        }
        Object value = expression.getValue(context);
        if (value == null) {
            throw new IllegalArgumentException(expressionHint(expression)
                    + " evaluated to null; check the expression and note that parameter names are only"
                    + " available when the application is compiled with '-parameters' (Spring Boot's"
                    + " parent enables it), otherwise use '#a0', '#a1', ... instead");
        }
        return String.valueOf(value);
    }

    private static long longValue(Expression expression, EvaluationContext context, String literal) {
        if (expression == null) {
            try {
                return Long.parseLong(literal.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("dbucket @DBucket tokens must be a whole number or a"
                        + " SpEL expression starting with '#', but was '" + literal + "'", e);
            }
        }
        Object value = expression.getValue(context);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            throw new IllegalArgumentException(expressionHint(expression)
                    + " evaluated to null; check the expression and note that parameter names are only"
                    + " available when the application is compiled with '-parameters' (Spring Boot's"
                    + " parent enables it), otherwise use '#a0', '#a1', ... instead");
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("dbucket @DBucket tokens expression did not evaluate to a"
                    + " number but to '" + value + "'", e);
        }
    }

    private static String expressionHint(Expression expression) {
        return "dbucket @DBucket expression '" + expression.getExpressionString() + "'";
    }

    private record CompiledAnnotation(Expression name, Expression namespace, Expression tokens) {

        static CompiledAnnotation of(DBucket annotation) {
            return new CompiledAnnotation(
                    expression(annotation.value()),
                    expression(annotation.namespace()),
                    expression(annotation.tokens()));
        }

        /** {@code null} means "literal": only expressions need SpEL parsing. */
        private static Expression expression(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String value = raw.trim();
            if (value.startsWith("#{") && value.endsWith("}")) {
                return PARSER.parseExpression(value.substring(2, value.length() - 1));
            }
            return value.startsWith("#") ? PARSER.parseExpression(value) : null;
        }
    }
}
