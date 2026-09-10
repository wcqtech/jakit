# dbucket

[中文](README.md) | [English](README.en.md)

[![](https://img.shields.io/badge/GitHub-wcqtech/jakit-blue?logo=github)](https://github.com/wcqtech/jakit)
[![](https://jitpack.io/v/wcqtech/jakit.svg)](https://jitpack.io/#wcqtech/jakit)

dbucket is a lightweight distributed token bucket backed by a relational database.

Features:

- **No new middleware**: reuse the database you already have - MySQL / PostgreSQL / KingbaseES are supported out of the box - with no extra distributed store and no separate rate-limit service.
- **Exact across instances**: all arithmetic happens in the database, so any number of instances and threads share one quota without local state.
- **Lazy refill**: no background scheduler; tokens are accrued from the database clock when a consume, read or deposit happens, capped at capacity.
- **Atomic consumption**: a single `UPDATE ... WHERE effectiveTokens >= n` with the guard evaluated inside the database.
- **Database clock**: `NOW()` and friends only, never application or container time, so clock drift cannot skew the bucket.
- **High-precision rates**: tokens are stored as fixed-point decimals, remainders are kept.
- **Blocking acquire**: fail immediately, or poll until a timeout.
- **Dynamic buckets**: capacity and rate can be changed while running.
- **Custom storage**: `BucketStore` is an SPI, so the storage medium can be replaced.
- **Simple annotations**: the starter provides a `@DBucket` method gate and optional Micrometer metrics.

## Requirements

- Java 17+
- Spring Boot 3.0+ (build baseline 3.2.7; `dbucket-core` only needs `spring-jdbc` and works standalone)
- MySQL 8.x / PostgreSQL / KingbaseES V8R6 (`pg` or `mysql` compatibility mode)

## Install

Published through JitPack. Spring Boot applications use the starter:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.wcqtech.jakit</groupId>
        <artifactId>dbucket-spring-boot-starter</artifactId>
        <version>${version}</version>
    </dependency>
</dependencies>
```

Use the released Git tag as the version.

Non-Boot applications (or when only the facade and SPI are needed) use core:

```xml
<dependency>
    <groupId>com.github.wcqtech.jakit</groupId>
    <artifactId>dbucket-core</artifactId>
    <version>${version}</version>
</dependency>
```

## Schema

Table layout (unique key `(namespace, name)`):

| Column        | MySQL           | PostgreSQL / KingbaseES | Notes                                          |
| ------------- | --------------- | ----------------------- | ---------------------------------------------- |
| `namespace`   | `VARCHAR(64)`   | `VARCHAR(64)`           | Namespace                                      |
| `name`        | `VARCHAR(128)`  | `VARCHAR(128)`          | Bucket name                                    |
| `capacity`    | `DECIMAL(20,6)` | `NUMERIC(20,6)`         | Burst ceiling                                  |
| `rate`        | `DECIMAL(20,6)` | `NUMERIC(20,6)`         | Tokens refilled per second                     |
| `tokens`      | `DECIMAL(20,6)` | `NUMERIC(20,6)`         | Current balance (may lag the effective value)  |
| `last_refill` | `DATETIME(6)`   | `TIMESTAMPTZ`           | Last refill time (written by the database)     |

```sql
-- MySQL
CREATE TABLE IF NOT EXISTS dbucket (
  namespace   VARCHAR(64)   NOT NULL,
  name        VARCHAR(128)  NOT NULL,
  capacity    DECIMAL(20,6) NOT NULL,
  rate        DECIMAL(20,6) NOT NULL,
  tokens      DECIMAL(20,6) NOT NULL,
  last_refill DATETIME(6)   NOT NULL,
  PRIMARY KEY (namespace, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- PostgreSQL / KingbaseES
CREATE TABLE IF NOT EXISTS dbucket (
  namespace   VARCHAR(64)   NOT NULL,
  name        VARCHAR(128)  NOT NULL,
  capacity    NUMERIC(20,6) NOT NULL,
  rate        NUMERIC(20,6) NOT NULL,
  tokens      NUMERIC(20,6) NOT NULL,
  last_refill TIMESTAMPTZ   NOT NULL,
  PRIMARY KEY (namespace, name)
);
```

Both KingbaseES compatibility modes accept `TIMESTAMPTZ` (the `mysql` mode uses an explicit
`NOW()::timestamptz` cast internally, so the session time zone does not matter), and neither supports
MySQL's `ENGINE` / `DEFAULT CHARSET` table options.

Manage the schema with Flyway or Liquibase in production; `jakit.dbucket.ddl.auto-init=true` (or
`JdbcBucketStore.createTable()`) is meant for development and tests.

## Quick start

### Configure

```yaml
jakit:
  dbucket:
    enabled: true
    namespace: default
    auto-create: true
    create:
      capacity: 100
      rate: 10
      initial-state: FULL
    wait:
      poll-interval: 200ms
    ddl:
      auto-init: true   # development and tests only
```

A MySQL connection must use the UTC time zone or startup fails fast (see
"MySQL must run with a UTC session"):

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/demo?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
```

### Annotation

```java
import com.github.wcqtech.jakit.dbucket.annotation.DBucket;

@Service
public class OrderService {

    @DBucket("orders")                       // one token per call
    public void placeOrder(Order order) {
        // runs only when a token was acquired
    }

    @DBucket(value = "orders", tokens = "5", timeoutMs = 200)
    public void batchImport(Order order) {
        // waits up to 200 ms for five tokens
    }

    @DBucket(value = "#order.tenantId + ':orders'", tokens = "#order.items.size()")
    public void submit(Order order) {
        // bucket name and amount come from the arguments (SpEL)
    }

    @DBucket(value = "reports", ignoreFailure = true)
    public void exportReport() {
        // proceeds with a WARN even when no token was acquired (non-critical paths)
    }
}
```

When tokens cannot be acquired the method is skipped and a `BucketAcquireException` is thrown; it
carries `namespace`, `name`, `tokens` and the `AcquireResult`.

SpEL notes:

- Parameter names (`#order`, `#tenant`) require the **`-parameters` compiler flag** (enabled by
  Spring Boot's parent; plain Maven builds must configure `<parameters>true</parameters>` on
  `maven-compiler-plugin`). Spring 6.1 removed the local-variable-table discoverer.
- Positional aliases `#a0`, `#a1` work without that flag.
- The namespace may be empty (then `jakit.dbucket.namespace` applies) or a SpEL expression. An
  expression evaluating to `null` fails loudly with a hint about the flag above.
- Method-level annotations only, and only proxied calls are gated: **self-invocation inside the same
  bean bypasses the aspect**.

### Programmatic API

```java
import com.github.wcqtech.jakit.dbucket.AcquireResult;
import com.github.wcqtech.jakit.dbucket.Bucket;
import com.github.wcqtech.jakit.dbucket.BucketSnapshot;
import com.github.wcqtech.jakit.dbucket.BucketSpec;
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.DbucketAdmin;

@Service
public class OrderService {

    private final Dbucket dbucket;

    public OrderService(Dbucket dbucket) {
        this.dbucket = dbucket;
    }

    public void placeOrder(Order order) {
        Bucket orders = dbucket.bucket("orders");

        AcquireResult result = orders.tryAcquire(1);   // immediate attempt
        if (!result.isSuccess()) {
            throw new IllegalStateException("too many requests: " + result.outcome());
        }

        // blocking wait: 200 ms timeout
        boolean acquired = orders.acquire(1, Duration.ofMillis(200));

        // read-only snapshot
        BucketSnapshot snapshot = orders.snapshot().orElseThrow();

        orders.deposit(10);                            // manual deposit
    }

    public void tune(DbucketAdmin admin) {
        admin.create(BucketSpec.of("default", "orders", new BigDecimal("100"), new BigDecimal("10")));
        admin.adjustRate("default", "orders", new BigDecimal("20"));      // change the rate
        admin.adjustCapacity("default", "orders", new BigDecimal("50"));  // shrinking clamps the balance
        admin.delete("default", "orders");
    }
}
```

`Bucket` handles hold no connection and can be cached or shared across threads; every call is one
independent atomic database operation.

Polling in `acquire(n, Duration)` / `acquireResult(n, Duration)`: the total budget starts when the
call is entered, the interval defaults to 20 ms with 50%..150% jitter, and when the remaining budget
is smaller than one interval the sleep is clamped to it; an interrupted thread keeps its interrupt
flag and fails.

### Without Spring Boot

With nothing but a `DataSource`, build the facade directly. `JdbcBucketStore.detect` resolves the
dialect (verifying the MySQL session time zone and reading `SHOW database_mode` for KingbaseES):

```java
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.DbucketOptions;
import com.github.wcqtech.jakit.dbucket.store.JdbcBucketStore;

DataSource dataSource = ...;                      // any DataSource
JdbcBucketStore store = JdbcBucketStore.detect(dataSource);

Dbucket dbucket = Dbucket.create(store, DbucketOptions.builder()
        .defaultNamespace("default")
        .autoCreateSpec(new BigDecimal("100"), new BigDecimal("10"))   // capacity 100, 10 tokens/s
        .pollInterval(Duration.ofMillis(20))
        .failOpen(true)
        .build());
```

## Configuration

| Property                             | Default                   | Description                                                                                |
| ------------------------------------ | ------------------------- | ------------------------------------------------------------------------------------------ |
| `jakit.dbucket.enabled`              | `true`                    | Master switch                                                                              |
| `jakit.dbucket.table`                | `dbucket`                 | Table name, optionally `schema.table` (for example `myschema.dbucket`)                     |
| `jakit.dbucket.datasource-bean-name` | empty                     | Datasource bean to use when several exist; otherwise the single one (honouring `@Primary`)  |
| `jakit.dbucket.dialect`              | `auto`                    | `auto` detection, or explicitly `mysql` / `postgresql` / `kingbase`                         |
| `jakit.dbucket.kingbase.sql-profile` | `auto`                    | KingbaseES fragments: `auto` (`SHOW database_mode`), `pg` or `mysql`                        |
| `jakit.dbucket.kingbase.detect-mode` | `true`                    | Whether `SHOW database_mode` may be queried                                                |
| `jakit.dbucket.namespace`            | `default`                 | Default namespace                                                                          |
| `jakit.dbucket.auto-create`          | `true`                    | Create missing buckets (needs `create.capacity`, otherwise a WARN and auto-creation is off) |
| `jakit.dbucket.create.capacity`      | —                         | Auto-create capacity (required to enable auto-creation)                                    |
| `jakit.dbucket.create.rate`          | `0`                       | Auto-create rate in tokens per second                                                      |
| `jakit.dbucket.create.initial-state` | `FULL`                    | `FULL` / `EMPTY`                                                                           |
| `jakit.dbucket.wait.poll-interval`   | `20ms`                    | Blocking poll interval                                                                     |
| `jakit.dbucket.wait.jitter`          | `true`                    | Randomize the poll interval                                                                |
| `jakit.dbucket.fail-open`            | `true`                    | Let acquires through on storage failure (reads / deposits / admin never downgrade)          |
| `jakit.dbucket.exact-remaining`      | `false`                   | Request the exact remaining balance (costs a short transaction on MySQL)                   |
| `jakit.dbucket.ddl.auto-init`        | `false`                   | Run the embedded DDL at startup (idempotent `IF NOT EXISTS`)                               |
| `jakit.dbucket.verify-on-startup`    | `true`                    | Ping once at startup, failing fast when unreachable                                        |
| `jakit.dbucket.annotations.enabled`  | `true`                    | Whether the `@DBucket` aspect is registered                                                |
| `jakit.dbucket.annotations.order`    | `LOWEST_PRECEDENCE - 100` | Advice order; the default runs outside the transaction advice                              |
| `jakit.dbucket.metrics.enabled`      | `false`                   | Whether the facade is wrapped with Micrometer meters                                       |

Defining your own `BucketStore` bean makes the whole JDBC store back off, so another storage
implementation can be plugged in.

## Metrics

With `jakit.dbucket.metrics.enabled=true` and `micrometer-core` on the classpath:

| Meter                      | Type    | Tags                            | Description                                                       |
| -------------------------- | ------- | ------------------------------- | ----------------------------------------------------------------- |
| `dbucket.acquire`          | counter | `namespace`, `bucket`, `outcome` | `outcome` ∈ `success` / `insufficient` / `not_found` / `degraded` |
| `dbucket.acquire.duration` | timer   | same                            | Time spent by the facade, including a blocking wait                |
| `dbucket.acquire.errors`   | counter | `namespace`, `bucket`           | Storage errors that were not absorbed                              |

Bucket names become tag values, so keep their cardinality bounded or leave metrics disabled.

## Dialects

| Database                       | Status   | Notes                                                                                                                                                                                                                          |
| ------------------------------ | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| MySQL 8.0.x                    | Verified | Measured on 8.0.42; `NOW(6)` + `TIMESTAMPDIFF` + `ON DUPLICATE KEY`; no `RETURNING`                                                                                                                                             |
| PostgreSQL                     | Verified | Measured on 18.6; `EXTRACT(EPOCH ...)` + `TIMESTAMPTZ` + `ON CONFLICT` + `RETURNING`                                                                                                                                            |
| KingbaseES V8R6 (`pg` mode)    | Verified | Measured on V008R006C009B0014; identical fragments to PostgreSQL                                                                                                                                                               |
| KingbaseES V8R6 (`mysql` mode) | Verified | Same build; `NOW(6)` does not exist and timestamp subtraction is numeric (no `EXTRACT(EPOCH ...)`), so `TIMESTAMPDIFF` is required; columns are `TIMESTAMPTZ` with an explicit `NOW()::timestamptz`, so the session time zone does not matter |

`JdbcDialectResolver` resolves the dialect at startup: product name first, JDBC URL as fallback.
Unknown products (H2, MariaDB, ...) fail loudly instead of guessing, and the dialect can also be set
explicitly:

```java
import com.github.wcqtech.jakit.dbucket.store.Dialect;
import com.github.wcqtech.jakit.dbucket.store.DialectSqls;
import com.github.wcqtech.jakit.dbucket.store.JdbcBucketStore;
import com.github.wcqtech.jakit.dbucket.store.JdbcDialectResolver;
import com.github.wcqtech.jakit.dbucket.store.KingbaseMode;

// explicit MySQL (the session is still verified to be UTC)
JdbcDialectResolver.resolve(dataSource, "dbucket", Dialect.MYSQL, null);

// explicit KingbaseES, skipping SHOW database_mode
JdbcBucketStore.of(dataSource, DialectSqls.kingbase("dbucket", KingbaseMode.MYSQL));
```

### MySQL must run with a UTC session

`DATETIME` carries no time zone, so pin the connection:

```properties
jdbc:mysql://host:3306/db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
```

Why this is a hard requirement:

- **Refill math assumes one session time zone**: `NOW(6)` and the stored `last_refill` are both
  wall-clock values, and only sessions in the same zone make their difference a true elapsed time.
- **Mixing zones breaks it in both directions**: when `NOW()` is eight hours ahead of `last_refill`
  the bucket instantly refills to capacity; when it is eight hours behind, `GREATEST(0, elapsed)`
  clamps to zero and the bucket **stops refilling entirely**.
- **`lastRefill` shifts**: unzoned columns are interpreted as UTC by contract, so a UTC+8 session
  makes `BucketSnapshot.lastRefill` eight hours early (diagnostics only; `tokens` is unaffected).
- **DST zones add another skew**: wall-clock differences across a DST transition are off by one hour.
  UTC and fixed-offset zones (for example +08:00) do not have this problem.

The resolver compares `NOW()` with `UTC_TIMESTAMP()` at startup (only a zero difference passes) and
fails fast with the parameter to add. PostgreSQL and KingbaseES use `TIMESTAMPTZ` and need no such
setup.

## Custom storage (SPI)

`BucketStore` has seven methods, all shaped as "one atomic statement, minimal result":

```java
public interface BucketStore {
    CreateResult createIfAbsent(BucketSpec spec);                     // race safe: unique key + upsert
    Optional<BucketSnapshot> get(String namespace, String name);      // read-only, computes effective tokens, no writes
    ConsumeResult tryConsume(String ns, String name, long n, boolean withRemaining);
    boolean deposit(String ns, String name, long n);
    boolean adjustCapacity(String ns, String name, BigDecimal capacity);
    boolean adjustRate(String ns, String name, BigDecimal rate);
    boolean delete(String ns, String name);
}
```

Contract (fully documented in the Javadoc):

- The guard (whether tokens are available) and the refill must be executed atomically by the storage.
- No statement joins a caller-managed transaction.
- Storage errors are wrapped and thrown as `DbucketStoreException`.
- A missing bucket is reported through the return value (`false`, `NOT_FOUND`, `Optional.empty()`),
  never as an exception.

A new dialect means implementing `DialectSql` and injecting it through
`JdbcBucketStore.of(dataSource, dialectSql)`.

### Example: implementing BucketStore with Redis

The example below shows that the SPI is enough for a non-JDBC store (complete and runnable, built on
`StringRedisTemplate`). It is not part of this repository and is not covered by its tests; harden it
as needed.

One Lua script serves every read and write: atomicity comes from Redis executing scripts
single-threaded, and time comes from the Redis server (`TIME`).

```lua
-- KEYS[1] = bucket key; ARGV[1] = mode, further arguments depend on the mode
-- returns {status, ...}: 1=OK 0=NOT_FOUND 2=EXISTS 3=INSUFFICIENT
local function nowMicros()
  local t = redis.call('TIME')
  return tonumber(t[1]) * 1000000 + tonumber(t[2])
end

-- computes the post-refill balance; persist writes it back (capped at capacity, remainder kept)
local function accrue(key, now, persist)
  local capacity = tonumber(redis.call('HGET', key, 'capacity'))
  local rate = tonumber(redis.call('HGET', key, 'rate'))
  local tokens = tonumber(redis.call('HGET', key, 'tokens'))
  local elapsed = (now - tonumber(redis.call('HGET', key, 'last_refill'))) / 1000000
  if elapsed > 0 then tokens = math.min(capacity, tokens + elapsed * rate) end
  if persist then redis.call('HSET', key, 'tokens', tokens, 'last_refill', now) end
  return capacity, tokens
end

local key, mode = KEYS[1], ARGV[1]
local now = nowMicros()

if mode == 'create' then
  if redis.call('EXISTS', key) == 1 then return {2} end
  redis.call('HSET', key, 'capacity', ARGV[2], 'rate', ARGV[3], 'tokens', ARGV[4],
             'last_refill', now)
  return {1}
end

if redis.call('EXISTS', key) == 0 then return {0} end

if mode == 'get' then                                   -- compute only, no write
  local stored = tonumber(redis.call('HGET', key, 'tokens'))
  local capacity, tokens = accrue(key, now, false)
  return {1, string.format('%.6f', tokens), string.format('%.6f', stored),
          redis.call('HGET', key, 'capacity'), redis.call('HGET', key, 'rate'), now}
end

local capacity, tokens = accrue(key, now, true)

if mode == 'consume' then
  local want = tonumber(ARGV[2])
  if tokens < want then return {3} end
  tokens = tokens - want
  redis.call('HSET', key, 'tokens', tokens, 'last_refill', now)
  return {1, string.format('%.6f', tokens)}
end

if mode == 'deposit' then
  tokens = math.min(capacity, tokens + tonumber(ARGV[2]))
elseif mode == 'capacity' then
  capacity = tonumber(ARGV[2])
  tokens = math.min(tokens, capacity)                   -- shrinking clamps the balance
  redis.call('HSET', key, 'capacity', capacity)
elseif mode == 'rate' then
  redis.call('HSET', key, 'rate', ARGV[2])
end
redis.call('HSET', key, 'tokens', tokens, 'last_refill', now)
return {1}
```

Java side (`LUA` is the script above; it needs the extra dependency
`org.springframework.data:spring-data-redis`):

```java
public final class RedisBucketStore implements BucketStore {

    private static final String LUA = """
            ... the script above ...
            """;

    private static final RedisScript<List> SCRIPT = RedisScript.of(LUA, List.class);

    private final StringRedisTemplate redis;

    public RedisBucketStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public CreateResult createIfAbsent(BucketSpec spec) {
        List<?> result = execute("create", key(spec.namespace(), spec.name()),
                spec.capacity().toPlainString(), spec.rate().toPlainString(),
                spec.initialTokens().toPlainString());
        return status(result) == 1
                ? CreateResult.created()
                : CreateResult.exists(get(spec.namespace(), spec.name()).orElseThrow());
    }

    @Override
    public Optional<BucketSnapshot> get(String namespace, String name) {
        List<?> result = execute("get", key(namespace, name));
        if (status(result) == 0) {
            return Optional.empty();
        }
        return Optional.of(new BucketSnapshot(namespace, name,
                decimal(result.get(3)),                       // capacity
                decimal(result.get(4)),                       // rate
                decimal(result.get(1)),                       // effective tokens
                decimal(result.get(2)),                       // raw column value
                Instant.ofEpochSecond(0, ((Number) result.get(5)).longValue() * 1000L)));
    }

    @Override
    public ConsumeResult tryConsume(String namespace, String name, long tokens, boolean withRemaining) {
        List<?> result = execute("consume", key(namespace, name), Long.toString(tokens));
        return switch ((int) status(result)) {
            case 1 -> withRemaining
                    ? ConsumeResult.success(decimal(result.get(1)))
                    : ConsumeResult.success();
            case 3 -> ConsumeResult.insufficient();
            default -> ConsumeResult.notFound();
        };
    }

    @Override
    public boolean deposit(String namespace, String name, long tokens) {
        return status(execute("deposit", key(namespace, name), Long.toString(tokens))) == 1;
    }

    @Override
    public boolean adjustCapacity(String namespace, String name, BigDecimal capacity) {
        return status(execute("capacity", key(namespace, name), capacity.toPlainString())) == 1;
    }

    @Override
    public boolean adjustRate(String namespace, String name, BigDecimal rate) {
        return status(execute("rate", key(namespace, name), rate.toPlainString())) == 1;
    }

    @Override
    public boolean delete(String namespace, String name) {
        return Boolean.TRUE.equals(redis.delete(key(namespace, name)));
    }

    private List<?> execute(String mode, String key, String... args) {
        String[] argv = new String[args.length + 1];
        argv[0] = mode;
        System.arraycopy(args, 0, argv, 1, args.length);
        try {
            List<?> result = redis.execute(SCRIPT, List.of(key), argv);
            return result == null ? List.of(0L) : result;
        } catch (RuntimeException e) {                        // SPI contract: wrap, never swallow
            throw new DbucketStoreException("redis bucket " + mode + " failed: " + e.getMessage(), e);
        }
    }

    private static long status(List<?> result) {
        return result.isEmpty() ? 0L : ((Number) result.get(0)).longValue();
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    private static String key(String namespace, String name) {
        return "dbucket:" + namespace + ":" + name;
    }
}
```

Wire it up by declaring it as a bean:

```java
@Bean
BucketStore bucketStore(StringRedisTemplate redis) {
    return new RedisBucketStore(redis);
}
```

Notes:

- Tokens are stored as Lua doubles.
- `get` computes without writing, so the stored `tokens` value may lag the effective one.
- Keys never expire: refresh a bucket with `EXPIRE` on writes (renewing it every time) if buckets are
  ephemeral, or call `delete` from business code.
- Exceptions from `StringRedisTemplate` are converted to `DbucketStoreException` as the SPI requires;
  the policy layer decides whether to fail open.
- One key per bucket suits Redis Cluster naturally; just avoid multi-key commands.

## Behaviour

| Operation                                  | Missing bucket                                                    | Storage failure                                                                                              |
| ------------------------------------------ | ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `tryAcquire` / `acquire` / `acquireResult` | `NOT_FOUND` (with auto-create enabled the bucket is created and the acquire retried once) | with `failOpen=true` a degraded `SUCCESS + degraded=true` plus an ERROR log, otherwise `DbucketStoreException` |
| `snapshot()`                               | `Optional.empty()`                                                | `DbucketStoreException` (reads never downgrade)                                                               |
| `deposit()`                                | `false`                                                           | `DbucketStoreException` (writes never downgrade)                                                              |
| `admin().*`                                | `false` / `EXISTS`                                                | `DbucketStoreException`                                                                                       |

More semantics:

- **Refill happens inside the atomic operation**: the guard uses the post-refill effective balance.
- **Snapshots are computed**: `snapshot().tokens()` is calculated by the SQL (balance plus accrual,
  capped at capacity) without writing, so queries do not compete for the row lock; `storedTokens()`
  and `lastRefill()` are raw column values for diagnostics only.
- **Shrinking clamps the balance** (`tokens = LEAST(tokens, capacity)`); growing needs nothing extra.
- **Delete wins**: deletion and concurrent operations are serialized by the row lock; afterwards calls
  return `NOT_FOUND` and blocked waiters fail immediately without compensation.
- **Exact remaining**: when enabled, PostgreSQL / KingbaseES return it in the same statement via
  `RETURNING tokens`; MySQL has no `RETURNING` and uses a short update-then-select transaction, which
  is why it is off by default.

## Semantics and limits

- **At-most-once** on storage failures: it is unknown whether a failed call committed, the component
  never retries, so callers that retry must be idempotent.
- **fail-open applies to acquire only**: a degraded acquire returns `SUCCESS + degraded=true`; reads,
  deposits and admin calls always throw `DbucketStoreException`.
- **Not fair**: waiters poll, no FIFO guarantee.
- **Per-bucket throughput ceiling** around `1 / (database round trip + lock queueing)`, far below
  in-memory limiters; shard by business key for high rates.
- **Write amplification**: N waiters produce `N / pollInterval` writes per second; raise
  `poll-interval` when many callers wait.
- **Clock**: the database clock is the only time source, which assumes reads and writes hit the same
  instance or primary. Multi-writer, multi-primary or cross-datacenter topologies share no single
  clock and need their own guarantees.
- **Clock rollback**: accrual is clamped with `GREATEST(0, elapsed)` (never subtracting) and
  `last_refill` never moves backwards; refill pauses during the rollback window.
- **Rounding**: the refill expression is evaluated as floating point and written back to
  `NUMERIC(20,6)`; each operation is off by at most `0.5e-6`, with no systematic bias.
- `BucketSnapshot.lastRefill` is diagnostic and may lag; use `tokens` (the effective count) for
  decisions.

## FAQ

**Why not Redis / bucket4j?**
Reusing the existing database gives exact cross-instance quotas without another moving part. The
trade-off is throughput and latency; dbucket fits moderate rates that need precise shared quotas.

**What happens if MySQL runs in UTC+8?**
The starter fails at startup with the URL parameter in the message. If you bypass the check
(`JdbcBucketStore.of(dataSource, DialectSqls.mysql("dbucket"))`) and keep every related session in
+08:00, consumption still works, but `BucketSnapshot.lastRefill` is eight hours early.

To really run in +8, take over both the connection and the store bean (keep the table name in sync
with `jakit.dbucket.table`):

```properties
# fixed offset; the + must be encoded as %2B in a URL
spring.datasource.url=jdbc:mysql://host:3306/db?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true
# a named zone also works, but requires MySQL to have its time zone tables loaded (mysql.time_zone_name)
# connectionTimeZone=Asia/Shanghai
```

```java
@Bean
BucketStore bucketStore(DataSource dataSource) {
    // explicit dialect: skips the UTC check in JdbcDialectResolver
    JdbcBucketStore store = JdbcBucketStore.of(dataSource, DialectSqls.mysql("dbucket"));
    store.ping();       // previously handled by jakit.dbucket.verify-on-startup
    return store;       // a custom bean makes the starter's JDBC store back off
}
```

- **Every session must agree**: one connection pool, instance, application or scheduled job using UTC
  against the same database is enough to skew refill math.
- Other time-zone dependent behaviour on that connection (`NOW()`, `DATETIME` to `java.time`
  conversion) is interpreted as +8 too; if business code assumes UTC, assess it as well.
- Replacing the `BucketStore` bean disables `jakit.dbucket.ddl.auto-init` and `verify-on-startup`, so
  call `createTable()` / `ping()` yourself when needed.
- For DST regions prefer a zone id (for example `America/New_York`) over a fixed offset.

**Can the bucket table live in another schema?**
Yes: `jakit.dbucket.table=myschema.dbucket`. Identifiers must match
`[A-Za-z_][A-Za-z0-9_$]*` with an optional schema prefix; quoted or exotic names are rejected.

## Modules

| Module                        | Responsibility                                                                                              | Dependencies                   |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------- | ------------------------------ |
| `dbucket-core`                | Facade API, `BucketStore` SPI, four dialect profiles, JDBC store, waiting and failure policy, embedded DDL   | spring-jdbc                    |
| `dbucket-spring-boot-starter` | Auto-configuration, `@DBucket` aspect, property binding, optional Micrometer metrics                         | `dbucket-core` + Spring Boot 3 |
