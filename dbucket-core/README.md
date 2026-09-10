# dbucket

[中文](README.md) | [English](README.en.md)

[![](https://img.shields.io/badge/GitHub-wcqtech/jakit-blue?logo=github)](https://github.com/wcqtech/jakit)
[![](https://jitpack.io/v/wcqtech/jakit.svg)](https://jitpack.io/#wcqtech/jakit)

dbucket 是基于数据库存储的轻量分布式令牌桶。

特性：

- **不引入新中间件**：复用现有数据，默认支持MySQL / PostgreSQL / KingbaseES，无需额外分布式存储或独立限流服务。
- **跨实例精确**：所有计算在数据库内完成，多实例、多线程共享同一额度，不依赖本地内存。
- **懒补充**：没有后台定时任务；消耗、查询、投放时才按数据库时间计算应补令牌，桶满即止。
- **原子扣减**：单条 `UPDATE ... WHERE 有效令牌 >= n`，守卫在数据库内求值。
- **数据库时钟**：采用 `NOW()` 等数据库时间，不依赖应用/容器时间，避免时钟漂移。
- **高精度频率**：令牌以定点小数存储，余数保留。
- **阻塞等待**：立即失败或超时轮询等待。
- **动态桶**：运行时可动态调整容量与频率。
- **自定义存储**：`BucketStore` 是 SPI，可换成其他存储介质。
- **易用注解**：starter 提供 `@DBucket` 方法级限流与可选 Micrometer 指标。

## 环境要求

- Java 17+
- Spring Boot 3.0+（构建基准 3.2.7；`dbucket-core` 只需 `spring-jdbc`，可独立使用）
- MySQL 8.x / PostgreSQL / KingbaseES V8R6（`pg` 或 `mysql` 兼容模式）

## 引入

通过 JitPack 引入。Spring Boot 项目直接引入 starter：

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

版本号以实际发布的 Git tag 为准。

非 Spring Boot 应用（或只需要门面与 SPI）只引 core：

```xml
<dependency>
    <groupId>com.github.wcqtech.jakit</groupId>
    <artifactId>dbucket-core</artifactId>
    <version>${version}</version>
</dependency>
```

## 建表

表结构（唯一键 `(namespace, name)`）：

| 列             | MySQL           | PostgreSQL / KingbaseES | 说明             |
| ------------- | --------------- | ----------------------- | -------------- |
| `namespace`   | `VARCHAR(64)`   | `VARCHAR(64)`           | 命名空间           |
| `name`        | `VARCHAR(128)`  | `VARCHAR(128)`          | 桶名             |
| `capacity`    | `DECIMAL(20,6)` | `NUMERIC(20,6)`         | 突发上限           |
| `rate`        | `DECIMAL(20,6)` | `NUMERIC(20,6)`         | 每秒补充令牌数        |
| `tokens`      | `DECIMAL(20,6)` | `NUMERIC(20,6)`         | 当前余额（可能落后于有效值） |
| `last_refill` | `DATETIME(6)`   | `TIMESTAMPTZ`           | 上次补充时间（由数据库写入） |

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

KingbaseES 两种兼容模式的列类型都可用 `TIMESTAMPTZ`（`mysql` 模式下内部使用显式 `NOW()::timestamptz` 转换，因此不依赖会话时区）；两种模式都不支持 MySQL 的 `ENGINE` / `DEFAULT CHARSET` 表选项。

生产环境建议用 Flyway / Liquibase 管理 DDL；`jakit.dbucket.ddl.auto-init=true`（或 `JdbcBucketStore.createTable()`）适合开发与测试。

## 快速开始

### 配置

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
      auto-init: true   # 仅开发/测试
```

MySQL 连接串必须采用UTC时区，否则 fail-fast（原因见下文"MySQL 必须使用 UTC 会话"）：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/demo?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
```

### 注解方式

```java
import com.github.wcqtech.jakit.dbucket.annotation.DBucket;

@Service
public class OrderService {

    @DBucket("orders")                       // 每次调用消耗 1 个令牌
    public void placeOrder(Order order) {
        // 只有取到令牌才会执行
    }

    @DBucket(value = "orders", tokens = "5", timeoutMs = 200)
    public void batchImport(Order order) {
        // 最多阻塞 200ms 等待 5 个令牌
    }

    @DBucket(value = "#order.tenantId + ':orders'", tokens = "#order.items.size()")
    public void submit(Order order) {
        // 桶名与数量都从参数取值（SpEL）
    }

    @DBucket(value = "reports", ignoreFailure = true)
    public void exportReport() {
        // 取不到令牌也放行，只打 WARN（用于非关键路径）
    }
}
```

取不到令牌时抛 `BucketAcquireException`，异常中带有 `namespace`、`name`、`tokens` 与 `AcquireResult`。

SpEL 注意事项：

- 参数名（`#order`、`#tenant`）需要**编译时开启 `-parameters`**（Spring Boot 的 parent 默认开启；裸 Maven 需在 `maven-compiler-plugin` 中配置 `<parameters>true</parameters>`）。Spring 6.1 已移除局部变量表发现器。
- 也可用位置别名 `#a0`、`#a1`，不依赖编译标志。
- 命名空间可空（用 `jakit.dbucket.namespace`），也可用 SpEL；表达式求值为 null 会直接报错并提示上述标志。
- 方法级注解只对 Spring bean 的代理调用生效，**同类自调用不经过切面**。

### 编程方式

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

        AcquireResult result = orders.tryAcquire(1);   // 立即尝试
        if (!result.isSuccess()) {
            throw new IllegalStateException("too many requests: " + result.outcome());
        }

        // 阻塞等待：超时时间 200ms
        boolean acquired = orders.acquire(1, Duration.ofMillis(200));

        // 只读快照
        BucketSnapshot snapshot = orders.snapshot().orElseThrow();

        orders.deposit(10);                            // 手动投放
    }

    public void tune(DbucketAdmin admin) {
        admin.create(BucketSpec.of("default", "orders", new BigDecimal("100"), new BigDecimal("10")));
        admin.adjustRate("default", "orders", new BigDecimal("20"));      // 动态调频
        admin.adjustCapacity("default", "orders", new BigDecimal("50"));  // 缩容会钳制余额
        admin.delete("default", "orders");
    }
}
```

`Bucket` 句柄本身不持有连接，可以缓存或跨线程共享；每次调用都是一次独立的原子数据库操作。

`acquire(n, Duration)` / `acquireResult(n, Duration)` 的轮询：总预算自调用时刻起算，间隔默认 20ms 并带 50%~150% 随机抖动，剩余预算不足一个间隔时按剩余量 sleep；线程被中断时恢复中断标志并返回失败。

### 非 Spring Boot 使用

只有 `DataSource` 时直接构建门面；`JdbcBucketStore.detect` 会探测方言（MySQL 校验会话 UTC、KingbaseES 读 `SHOW database_mode`）：

```java
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.DbucketOptions;
import com.github.wcqtech.jakit.dbucket.store.JdbcBucketStore;

DataSource dataSource = ...;                      // 任意 DataSource
JdbcBucketStore store = JdbcBucketStore.detect(dataSource);

Dbucket dbucket = Dbucket.create(store, DbucketOptions.builder()
        .defaultNamespace("default")
        .autoCreateSpec(new BigDecimal("100"), new BigDecimal("10"))   // 容量 100，10 令牌/秒
        .pollInterval(Duration.ofMillis(20))
        .failOpen(true)
        .build());
```

## 配置

| 配置项                                  | 默认值                       | 说明                                                      |
| ------------------------------------ | ------------------------- | ------------------------------------------------------- |
| `jakit.dbucket.enabled`              | `true`                    | 总开关                                                     |
| `jakit.dbucket.table`                | `dbucket`                 | 表名，可带 schema（如 `myschema.dbucket`）                      |
| `jakit.dbucket.datasource-bean-name` | 空                         | 多数据源时指定 bean 名；为空时用单一 DataSource（多个时尊重 `@Primary`）      |
| `jakit.dbucket.dialect`              | `auto`                    | `auto` 探测；可显式 `mysql` / `postgresql` / `kingbase`       |
| `jakit.dbucket.kingbase.sql-profile` | `auto`                    | Kingbase 片段：`auto`（查 `SHOW database_mode`）、`pg`、`mysql` |
| `jakit.dbucket.kingbase.detect-mode` | `true`                    | 是否允许 `SHOW database_mode`                               |
| `jakit.dbucket.namespace`            | `default`                 | 缺省命名空间                                                  |
| `jakit.dbucket.auto-create`          | `true`                    | 桶不存在时自动建桶（需配 `create.capacity`，否则只打 WARN 并关闭自动建桶）       |
| `jakit.dbucket.create.capacity`      | —                         | 自动建桶容量（必填才能启用自动建桶）                                      |
| `jakit.dbucket.create.rate`          | `0`                       | 自动建桶频率（令牌/秒）                                            |
| `jakit.dbucket.create.initial-state` | `FULL`                    | `FULL` / `EMPTY`                                        |
| `jakit.dbucket.wait.poll-interval`   | `20ms`                    | 阻塞轮询间隔                                                  |
| `jakit.dbucket.wait.jitter`          | `true`                    | 轮询间隔随机抖动                                                |
| `jakit.dbucket.fail-open`            | `true`                    | 存储故障时 acquire 放行（read / deposit / admin 不降级）            |
| `jakit.dbucket.exact-remaining`      | `false`                   | acquire 是否取精确剩余（MySQL 代价为短事务）                           |
| `jakit.dbucket.ddl.auto-init`        | `false`                   | 启动时执行内嵌 DDL（幂等 `IF NOT EXISTS`）                         |
| `jakit.dbucket.verify-on-startup`    | `true`                    | 启动时 ping 一次，失败 fail-fast                                |
| `jakit.dbucket.annotations.enabled`  | `true`                    | 是否注册 `@DBucket` 切面                                      |
| `jakit.dbucket.annotations.order`    | `LOWEST_PRECEDENCE - 100` | 切面顺序；默认小于事务切面，即先取令牌后进业务事务                               |
| `jakit.dbucket.metrics.enabled`      | `false`                   | 是否用 Micrometer 包装门面                                     |

自定义 `BucketStore` Bean 会让 JDBC 存储整体退让，可替换为其他存储实现。

## 指标

开启 `jakit.dbucket.metrics.enabled=true` 且 classpath 存在 `micrometer-core` 时：

| 指标                         | 类型      | 标签                             | 说明                                                                |
| -------------------------- | ------- | ------------------------------ | ----------------------------------------------------------------- |
| `dbucket.acquire`          | counter | `namespace`、`bucket`、`outcome` | `outcome` ∈ `success` / `insufficient` / `not_found` / `degraded` |
| `dbucket.acquire.duration` | timer   | 同上                             | 门面耗时（含阻塞等待）                                                       |
| `dbucket.acquire.errors`   | counter | `namespace`、`bucket`           | 存储异常且未降级                                                          |

`bucket` 作为标签值需控制基数，否则请保持指标关闭。

## 方言支持

| 数据库                         | 状态  | 说明                                                                                                                               |
| --------------------------- | --- | -------------------------------------------------------------------------------------------------------------------------------- |
| MySQL 8.0.x                 | 已验证 | 8.0.42 实测；`NOW(6)` + `TIMESTAMPDIFF` + `ON DUPLICATE KEY`；无 `RETURNING`                                                          |
| PostgreSQL                  | 已验证 | 18.6 实测；`EXTRACT(EPOCH ...)` + `TIMESTAMPTZ` + `ON CONFLICT` + `RETURNING`                                                       |
| KingbaseES V8R6（`pg` 模式）    | 已验证 | V008R006C009B0014 实测，与 PostgreSQL 同片段                                                                                            |
| KingbaseES V8R6（`mysql` 模式） | 已验证 | 同版本实测；`NOW(6)` 不存在、时间戳相减是数值语义（不能用 `EXTRACT(EPOCH ...)`），必须用 `TIMESTAMPDIFF`；列用 `TIMESTAMPTZ` + 显式 `NOW()::timestamptz`，因此不依赖会话时区 |

方言由 `JdbcDialectResolver` 在启动时解析：productName 优先、JDBC URL 兜底；无法识别的产品（如 H2、MariaDB）直接报错，也可以显式指定：

```java
import com.github.wcqtech.jakit.dbucket.store.Dialect;
import com.github.wcqtech.jakit.dbucket.store.DialectSqls;
import com.github.wcqtech.jakit.dbucket.store.JdbcBucketStore;
import com.github.wcqtech.jakit.dbucket.store.JdbcDialectResolver;
import com.github.wcqtech.jakit.dbucket.store.KingbaseMode;

// 显式指定 MySQL（仍会校验会话 UTC）
JdbcDialectResolver.resolve(dataSource, "dbucket", Dialect.MYSQL, null);

// 显式指定 KingbaseES 并跳过 SHOW database_mode
JdbcBucketStore.of(dataSource, DialectSqls.kingbase("dbucket", KingbaseMode.MYSQL));
```

### MySQL 必须使用 UTC 会话

`DATETIME` 没有时区。请固定连接串：

```properties
jdbc:mysql://host:3306/db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
```

原因（硬性要求）：

- **补充计算依赖"会话时区一致"**：`NOW(6)` 与已写入的 `last_refill` 都是无时区墙上时间，只有所有触碰该表的连接处于同一时区，二者相减才是真实经过时间。
- **时区不一致会双向出错**：`NOW()` 比 `last_refill` 晚 8 小时时瞬间补满到容量；早 8 小时时被 `GREATEST(0, elapsed)` 钳到 0，桶在这段时间**完全停止补充**。
- **`lastRefill` 会整体偏移**：无时区列在 Java 侧按契约当作 UTC 解释，用 UTC+8 会话写入的值会让 `BucketSnapshot.lastRefill` 早 8 小时（只是诊断字段，`tokens` 不受影响）。
- **带夏令时的时区另有偏差**：DST 切换点前后的墙上时间差会偏差 1 小时；UTC 与固定偏移时区（如中国 +08:00）没有这个问题。

`JdbcDialectResolver` 启动时用 `NOW()` 与 `UTC_TIMESTAMP()` 的差值校验（0 秒才通过），不满足直接 fail-fast 并提示该参数。PostgreSQL 与 KingbaseES 使用 `TIMESTAMPTZ`，不依赖会话时区。

## 自定义存储（SPI）

`BucketStore` 只有七个方法，全部要求"单条原子语句 + 最小结果"：

```java
public interface BucketStore {
    CreateResult createIfAbsent(BucketSpec spec);                     // 竞态安全：唯一键 + upsert
    Optional<BucketSnapshot> get(String namespace, String name);      // 纯 SELECT，计算有效令牌，不写行
    ConsumeResult tryConsume(String ns, String name, long n, boolean withRemaining);
    boolean deposit(String ns, String name, long n);
    boolean adjustCapacity(String ns, String name, BigDecimal capacity);
    boolean adjustRate(String ns, String name, BigDecimal rate);
    boolean delete(String ns, String name);
}
```

实现约定（Javadoc 中有完整说明）：

- 守卫（能否拿到令牌）、补充必须在存储层原子执行。
- 所有变更不合并调用方事务。
- 存储层异常包装成 `DbucketStoreException` 抛出。
- 桶不存在通过返回值表达（`false`、`NOT_FOUND`、`Optional.empty()`），不抛异常。

新增方言需实现 `DialectSql` 并通过 `JdbcBucketStore.of(dataSource, dialectSql)` 注入。

### 示例：用 Redis 实现 BucketStore

以下示例说明 SPI 足以承载非 JDBC 存储（完整可运行，围绕 `StringRedisTemplate`）。它不是本仓库的产物，也未纳入测试，请按需加固。

一份 Lua 脚本服务全部读写：原子性由 Redis 单线程执行保证，时间取自 Redis 服务端（`TIME`）。

```lua
-- KEYS[1] = 桶 key；ARGV[1] = mode，其余参数随模式变化
-- 返回 {status, ...}：1=OK 0=NOT_FOUND 2=EXISTS 3=INSUFFICIENT
local function nowMicros()
  local t = redis.call('TIME')
  return tonumber(t[1]) * 1000000 + tonumber(t[2])
end

-- 计算“补充后”的令牌数；persist 为真时写回（补充量截断到容量，小数余数保留）
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

if mode == 'get' then                                   -- 只算不写
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
  tokens = math.min(tokens, capacity)                   -- 缩容钳制余额
  redis.call('HSET', key, 'capacity', capacity)
elseif mode == 'rate' then
  redis.call('HSET', key, 'rate', ARGV[2])
end
redis.call('HSET', key, 'tokens', tokens, 'last_refill', now)
return {1}
```

Java 侧（需额外依赖 `org.springframework.data:spring-data-redis`）：

```java
public final class RedisBucketStore implements BucketStore {

    private static final String LUA = """
            ... 上面的脚本 ...
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
                decimal(result.get(1)),                       // 有效令牌
                decimal(result.get(2)),                       // 原始列值
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
        } catch (RuntimeException e) {                        // SPI 要求：包装后抛出，不吞
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

接入方式：声明成 Bean 即可：

```java
@Bean
BucketStore bucketStore(StringRedisTemplate redis) {
    return new RedisBucketStore(redis);
}
```

注意点：

- 令牌用 Lua 双精度数存储。
- `get` 只算不写，`tokens` 列可能落后于有效值。
- 键不会自动过期：若桶是临时的，需要在写入时补 `EXPIRE`（注意写入时续期），或由业务调用 `delete`。
- `StringRedisTemplate` 的异常会按 SPI 约定转成 `DbucketStoreException`，由策略层决定是否 fail-open。
- 单桶单 key，天然适配 Redis Cluster；但不要跨 key 组合命令。

## 行为约定

| 操作                                         | 不存在桶                                   | 存储异常                                                                                |
| ------------------------------------------ | -------------------------------------- | ----------------------------------------------------------------------------------- |
| `tryAcquire` / `acquire` / `acquireResult` | `NOT_FOUND`（开启 auto-create 时会先建桶再重试一次） | `failOpen=true` 时降级为 `SUCCESS + degraded=true` 并打 ERROR，否则抛 `DbucketStoreException` |
| `snapshot()`                               | 返回 `Optional.empty()`                  | 抛 `DbucketStoreException`（读不降级）                                                     |
| `deposit()`                                | 返回 `false`                             | 抛 `DbucketStoreException`（写不降级）                                                     |
| `admin().*`                                | 返回 `false` / `EXISTS`                  | 抛 `DbucketStoreException`                                                           |

其他语义：

- **惰性补充在原子操作内完成**：守卫按"补充后"的有效令牌判断。
- **快照是计算值**：`snapshot().tokens()` 由 SQL 计算（余额 + 应补，截断到容量），不落库，查询不与消耗竞争行锁；`storedTokens()` 与 `lastRefill()` 是原始列值，仅用于诊断。
- **缩容钳制余额**（`tokens = LEAST(tokens, capacity)`）；扩容无需额外动作。
- **删除优先**：删除与并发操作由行锁串行化；删除后调用返回 `NOT_FOUND`，等待中的线程立刻失败，不做补偿。
- **精确剩余**：开启后 PostgreSQL / KingbaseES 用 `RETURNING tokens` 单语句返回；MySQL 无 `RETURNING`，用"UPDATE + SELECT"短事务返回，故默认关闭。

## 语义与限制

- **at-most-once**：存储异常时"本次消耗是否已提交"未知，组件不会重试；调用方重试需业务幂等。
- **fail-open 只作用于 acquire**：降级返回 `SUCCESS + degraded=true`；读、投放与管理操作始终抛 `DbucketStoreException`。
- **非公平**：等待者靠轮询竞争，不保证 FIFO。
- **单桶吞吐上限**：同一行的操作串行化，极限约为 `1 / (数据库往返 + 锁排队)`，远低于内存方案；高频场景建议按业务维度拆桶。
- **写放大**：N 个等待者按轮询间隔产生 `N / pollInterval` 次写。等待者多时请调大 `poll-interval`。
- **时钟**：只使用数据库时间，隐含"读写落在同一实例/主库"；多写、多主或跨机房双写场景时钟源不唯一，需要自行保证。
- **时钟回拨**：补充量按 `GREATEST(0, 已过时间)` 钳制（不会倒扣），`last_refill` 用 `GREATEST(NOW(), last_refill)` 防倒退；回拨窗口内桶暂停补充。
- **浮点舍入**：补充表达式在数据库内以浮点计算后写回 `NUMERIC(20,6)`，每次操作误差不超过 `0.5e-6`，无系统性偏差。
- `BucketSnapshot.lastRefill` 是诊断字段，可能落后于"当前"；业务判断请用 `tokens`（有效令牌）。

## 常见问题

**为什么不用 Redis / bucket4j？**
复用现有数据库即可获得跨实例的精确额度，省一套中间件；代价是吞吐与延迟不如内存方案。中低频、需要精确共享额度的场景更合适。

**MySQL 用 UTC+8 会怎样？**
starter 路径下会被启动校验拦下（失败信息给出要加的连接参数）。若绕过校验（`JdbcBucketStore.of(dataSource, DialectSqls.mysql("dbucket"))`）并保证所有相关连接统一为 +8，令牌计算仍然正确，但 `BucketSnapshot.lastRefill` 会早 8 小时。

确实要用 +8 时，把连接串与存储 Bean 都自己接管（表名与 `jakit.dbucket.table` 保持一致）：

```properties
# 固定偏移；URL 里的 + 需编码为 %2B
spring.datasource.url=jdbc:mysql://host:3306/db?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true
# 命名时区也可以，但要求 MySQL 已加载时区表（mysql.time_zone_name）
# connectionTimeZone=Asia/Shanghai
```

```java
@Bean
BucketStore bucketStore(DataSource dataSource) {
    // 显式指定方言：跳过 JdbcDialectResolver 的 UTC 校验
    JdbcBucketStore store = JdbcBucketStore.of(dataSource, DialectSqls.mysql("dbucket"));
    store.ping();       // 原本由 jakit.dbucket.verify-on-startup 负责
    return store;       // 自定义 Bean 让 starter 的 JDBC 存储整体退让
}
```

- **必须全链路一致**：连接池、其他实例、其他应用、定时任务只要有一条用 UTC 连接同一个库，补充计算就会失真。
- 这条连接上其他依赖时区的行为（`NOW()`、`DATETIME` 与 `java.time` 的互转）也会按 +8 解释；若业务代码原本假定连接是 UTC，需要一并评估。
- 替换 `BucketStore` 后 `jakit.dbucket.ddl.auto-init` 与 `verify-on-startup` 失效，需要手动 `createTable()` / `ping()`。
- 带夏令时的地区建议写时区 ID（如 `America/New_York`）而不是固定偏移。

**桶表能改到别的 schema 吗？**
可以，`jakit.dbucket.table=myschema.dbucket`；表名只允许 `[A-Za-z_][A-Za-z0-9_$]*`（可带一个 schema 前缀），带引号或特殊字符的写法不支持。

## 模块

| 模块                            | 职责                                                          | 依赖                             |
| ----------------------------- | ----------------------------------------------------------- | ------------------------------ |
| `dbucket-core`                | 门面 API、`BucketStore` SPI、四种方言 SQL 片段、JDBC 存储、等待与失败策略、内嵌 DDL | spring-jdbc                    |
| `dbucket-spring-boot-starter` | 自动装配、`@DBucket` 切面、配置绑定、可选 Micrometer 指标                    | `dbucket-core` + Spring Boot 3 |
