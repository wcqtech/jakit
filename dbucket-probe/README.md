# dbucket 方言探针（临时验证工具）

用于在真实数据库上验证设计文档 `.docs/dbucket-design.md` §6 的 SQL 规格，以及各方言
（尤其 KingbaseES V8R6 的 MySQL / PG 兼容模式）的能力边界。

不参与 Maven 构建（单文件源码模式运行，JDK 11+）。

## 1. 准备凭据

```bash
cp dbucket-probe/probe.properties.example dbucket-probe/probe.properties
# 编辑 probe.properties 填写账号密码（该文件已 gitignore）
```

`profile` 取值：`mysql`（真实 MySQL 8）、`pg`（PostgreSQL 或 Kingbase PG 模式）、
`kb-mysql`（Kingbase MySQL 模式）。三者共享同一套 S1–S13 冒烟用例，差异只在 SQL 片段。

## 2. 运行

本机 Maven 仓库中已有驱动，直接拼 classpath（Windows 用 `;` 分隔）：

```bash
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
  -cp "C:/Users/WCQCN/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar;C:/Users/WCQCN/.m2/repository/org/postgresql/postgresql/42.7.8/postgresql-42.7.8.jar;C:/Users/WCQCN/.m2/repository/cn/com/kingbase/kingbase8/8.6.0/kingbase8-8.6.0.jar" \
  dbucket-probe/KingbaseProbe.java dbucket-probe/probe.properties
```

## 3. 探针内容

**能力/语法信息项**（不计入 pass/fail）：`LEAST`/`GREATEST`/`FLOOR`、两套时间差表达式、
`SHOW database_mode`、时间列类型矩阵（`TIMESTAMP(6)` / `DATETIME(6)` / `TIMESTAMPTZ` 与
`GREATEST(NOW(), col)` 的组合）、upsert 矩阵（`ON CONFLICT` / `ON DUPLICATE KEY UPDATE`）、
`UPDATE ... RETURNING`、时区会话设置（`SET TIME ZONE 'UTC'` / `SET time_zone='+00:00'`）、
区间字面量写法（`INTERVAL '2 second'` vs `INTERVAL 2 SECOND` / `DATE_SUB`）。

**SQL 规格冒烟**（临时表，session 级，跑完自动清理）：

| 步骤 | 断言 |
| --- | --- |
| S1 | 建临时桶表（`DECIMAL`/`NUMERIC` + `DATETIME(6)`/`TIMESTAMP(6)`/`TIMESTAMPTZ`） |
| S2 | 建桶 tokens=10, rate=0 |
| S3 | 复合 `tryConsume(7)` 成功（affected=1） |
| S4 | 剩余 tokens == 3 |
| S5 | `tryConsume(5)` 被守卫拒绝（affected=0） |
| S6 | 拒绝后 tokens 仍为 3（未误扣） |
| S7/S8 | 空桶 + 回拨 2s + rate=1 → 惰性补充后 `tryConsume(1)` 成功，剩余 ∈ [1, 1.2)（验证小数残留保留） |
| S9 | `deposit(5)` 先补后加、LEAST 封顶 |
| S10 | `createIfAbsent` upsert |
| S11 | 纯 SELECT 计算有效令牌（不写行） |
| S12/S12b/S12c | `delete`、`RETURNING tokens`、清理 |

退出码：有 FAIL 时为 1。

## 4. 实测结论（2026-09，四实例 pass=88 / fail=0）

| 能力 | MySQL 8.0.42 | PostgreSQL 18.6 | Kingbase PG 模式 | Kingbase MySQL 模式 |
| --- | --- | --- | --- | --- |
| 时间差表达式 | `TIMESTAMPDIFF` + `NOW(6)` | `EXTRACT(EPOCH ...)` + `NOW()` | 同 PG | `TIMESTAMPDIFF` + `NOW()`（**无 `NOW(6)`**） |
| `EXTRACT(EPOCH FROM (NOW()-col))` | ❌ | ✅ | ✅ | ❌（时间戳相减为数值语义） |
| `last_refill` 类型 | `DATETIME(6)` | `TIMESTAMPTZ` | `TIMESTAMPTZ` | `TIMESTAMPTZ` + `NOW()::timestamptz`（裸 `GREATEST(NOW(), TIMESTAMPTZ)` 会类型冲突，显式 cast 已解决） |
| upsert | `ON DUPLICATE KEY UPDATE` | `ON CONFLICT` | `ON CONFLICT` | 两者均可 |
| `RETURNING` | ❌（预期） | ✅ | ✅ | ✅ |
| `SET TIME ZONE 'UTC'` | `SET time_zone` | ✅ | ✅ | ✅ |
| `ENGINE`/`CHARSET` 表选项 | ✅ | ❌ | ❌ | ❌ |

结论：设计文档 D19 成立——KingbaseES 独立方言 + 两套能力片段（`pg` / `mysql`），
运行时按 `SHOW database_mode` 自动选择；不要复用 MySQL 或 PG 实现。

## 5. 结果解读

- 全绿 → 对应片段可直接作为该实例的实现。
- Kingbase 实例若出现 PG 语法项失败（如 `EXTRACT(EPOCH ...)`），把 `profile` 改为
  `kb-mysql`；反之亦然。
- 真实 MySQL / PG 实例出现失败 → 说明方言假设有误，需回到 §6 规格调整。
