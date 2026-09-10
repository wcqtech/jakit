import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * dbucket 方言能力探针（临时验证工具，不参与构建）。
 *
 * 目的：在真实实例上验证设计文档 §6 的 SQL 规格，尤其是 KingbaseES V8R6 在
 * MySQL / PG 两种兼容模式下对 PG profile 语法（ON CONFLICT / RETURNING /
 * EXTRACT(EPOCH ...)）与 MySQL profile 语法（TIMESTAMPDIFF / ON DUPLICATE KEY）
 * 的接受程度。
 *
 * 用法（单文件源码模式，JDK 11+）：
 *   java -cp &lt;mysql.jar;pg.jar;kingbase8.jar&gt; dbucket-probe/KingbaseProbe.java [probe.properties]
 *
 * 凭据放在 dbucket-probe/probe.properties（已 gitignore），示例见 probe.properties.example。
 */
public final class KingbaseProbe {

    private static final String TABLE = "dbucket_probe_bucket";
    private static final String NS = "ns";
    private static final String NAME = "b";

    private static int pass = 0;
    private static int fail = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path cfg = Path.of(args.length > 0 ? args[0] : "dbucket-probe/probe.properties");
        if (!Files.exists(cfg)) {
            System.out.println("缺少配置文件: " + cfg.toAbsolutePath());
            System.out.println("请复制 dbucket-probe/probe.properties.example 并填写账号密码。");
            return;
        }
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(cfg)) {
            props.load(r);
        }

        String[] keys = {"mysql", "pg", "kingbase-mysql", "kingbase-pg"};
        int found = 0;
        for (String key : keys) {
            String url = props.getProperty(key + ".url");
            if (url == null || url.isBlank()) {
                continue;
            }
            found++;
            Target t = new Target(key, url.trim(),
                    props.getProperty(key + ".user", "").trim(),
                    props.getProperty(key + ".password", ""),
                    props.getProperty(key + ".driver", "").trim(),
                    props.getProperty(key + ".profile", "mysql").trim().toLowerCase());
            run(t);
        }
        if (found == 0) {
            System.out.println("probe.properties 中没有配置任何 target");
            return;
        }
        System.out.printf("%n================ summary: pass=%d fail=%d ================%n", pass, fail);
        failures.forEach(f -> System.out.println("  FAIL " + f));
        if (fail > 0) {
            System.exit(1);
        }
    }

    private record Target(String key, String url, String user, String password, String driver, String profile) {}

    private static void run(Target t) {
        System.out.printf("%n================ %s (%s profile) ================%n", t.key, t.profile);
        System.out.println("url = " + t.url);
        if (!t.driver.isBlank()) {
            try {
                Class.forName(t.driver);
            } catch (ClassNotFoundException e) {
                record(t.key, "load driver", false, "driver not found: " + t.driver);
                return;
            }
        }
        try (Connection c = DriverManager.getConnection(t.url, t.user, t.password)) {
            printMetadata(c, t);
            capabilityProbes(c, t);
            scenario(c, t);
        } catch (Exception e) {
            record(t.key, "connect", false, e.getClass().getSimpleName() + ": " + msg(e));
        }
    }

    private static void printMetadata(Connection c, Target t) {
        try {
            var md = c.getMetaData();
            System.out.printf("product = %s %s | driver = %s %s%n",
                    md.getDatabaseProductName(), md.getDatabaseProductVersion(),
                    md.getDriverName(), md.getDriverVersion());
        } catch (Exception e) {
            record(t.key, "metadata", false, msg(e));
        }
        // 版本 / 兼容模式（Kingbase）：诊断信息，失败不计数
        if (t.key.startsWith("kingbase")) {
            infoQuery(c, t.key, "SHOW database_mode", "SHOW database_mode");
        }
        infoQuery(c, t.key, "SELECT version()", "SELECT version()");
        infoQuery(c, t.key, "SELECT current_setting('TimeZone')", "SELECT current_setting('TimeZone')");
    }

    /** 函数/语法能力探针；expect=null 表示仅记录结果（用于 Kingbase 的未知项）。 */
    private static void capabilityProbes(Connection c, Target t) {
        boolean mysql = mysqlFamily(t.profile);

        probe(c, t.key, "LEAST/GREATEST/FLOOR",
                "SELECT LEAST(1,2), GREATEST(0,-1), FLOOR(1.9)", true);

        if (mysql) {
            probe(c, t.key, "TIMESTAMPDIFF + " + nowExpr(t.profile),
                    "SELECT TIMESTAMPDIFF(MICROSECOND, " + nowExpr(t.profile) + ", " + nowExpr(t.profile) + ")", true);
            probe(c, t.key, "EXTRACT(EPOCH ...) [MySQL 家族无关，信息项]",
                    "SELECT EXTRACT(EPOCH FROM (NOW() - NOW()))", null);
            probe(c, t.key, "SET time_zone='+00:00' / SET TIME ZONE 'UTC'",
                    "SET time_zone = '+00:00'", true);
        } else {
            probe(c, t.key, "EXTRACT(EPOCH FROM interval)", "SELECT EXTRACT(EPOCH FROM (NOW() - NOW()))", true);
            probe(c, t.key, "TIMESTAMPDIFF [PG 家族无关，信息项]",
                    "SELECT TIMESTAMPDIFF(MICROSECOND, NOW(6), NOW(6))", null);
            probe(c, t.key, "SET TIME ZONE 'UTC'", "SET TIME ZONE 'UTC'", true);
        }

        // Kingbase MySQL 模式下两个家族的函数都可能存在，重点观察
        if (t.key.startsWith("kingbase")) {
            probe(c, t.key, "[KB] TIMESTAMPDIFF(MICROSECOND, NOW(6), NOW(6))",
                    "SELECT TIMESTAMPDIFF(MICROSECOND, NOW(6), NOW(6))", null);
            probe(c, t.key, "[KB] EXTRACT(EPOCH FROM (NOW()-NOW()))",
                    "SELECT EXTRACT(EPOCH FROM (NOW() - NOW()))", null);
            probe(c, t.key, "[KB] DATETIME(6) 类型",
                    "CREATE TEMPORARY TABLE probe_dt (t DATETIME(6))", null);
            probe(c, t.key, "[KB] TIMESTAMPTZ 类型",
                    "CREATE TEMPORARY TABLE probe_tz (t TIMESTAMPTZ)", null);
            probe(c, t.key, "[KB] MySQL 表选项 ENGINE/CHARSET",
                    "CREATE TEMPORARY TABLE probe_opt (id INT) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", null);
        }

        // 时间源 / 时区 / 区间语法矩阵（跨方言对比，信息项）
        timeSourceMatrix(c, t);

        if (t.key.startsWith("kingbase")) {
            timestampTypeMatrix(c, t);
            upsertAndReturningMatrix(c, t);
        }
    }

    /** 时间列类型 × GREATEST / 时间差表达式 的组合矩阵（定位 Kingbase 模式差异）。 */
    private static void timestampTypeMatrix(Connection c, Target t) {
        System.out.println("  --- 时间列类型矩阵（信息项） ---");
        execQuiet(c, "DROP TABLE IF EXISTS probe_ts");
        execQuiet(c, "CREATE TEMPORARY TABLE probe_ts (t_ts TIMESTAMP(6), t_tstz TIMESTAMPTZ)");
        execQuiet(c, "INSERT INTO probe_ts VALUES (NOW(), NOW())");
        infoQuery(c, t.key, "GREATEST(NOW(), TIMESTAMP(6) 列)",
                "SELECT GREATEST(NOW(), t_ts) FROM probe_ts");
        infoQuery(c, t.key, "GREATEST(NOW(), TIMESTAMPTZ 列)",
                "SELECT GREATEST(NOW(), t_tstz) FROM probe_ts");
        infoQuery(c, t.key, "GREATEST(NOW()::timestamptz, TIMESTAMPTZ 列)",
                "SELECT GREATEST(NOW()::timestamptz, t_tstz) FROM probe_ts");
        infoQuery(c, t.key, "TIMESTAMPDIFF(MICROSECOND, TIMESTAMP(6) 列, NOW())",
                "SELECT TIMESTAMPDIFF(MICROSECOND, t_ts, NOW()) FROM probe_ts");
        infoQuery(c, t.key, "TIMESTAMPDIFF(MICROSECOND, TIMESTAMPTZ 列, NOW())",
                "SELECT TIMESTAMPDIFF(MICROSECOND, t_tstz, NOW()) FROM probe_ts");
        infoQuery(c, t.key, "EXTRACT(EPOCH FROM AGE(NOW(), TIMESTAMPTZ 列))",
                "SELECT EXTRACT(EPOCH FROM AGE(NOW(), t_tstz)) FROM probe_ts");
        infoQuery(c, t.key, "EXTRACT(EPOCH FROM AGE(NOW(), TIMESTAMP(6) 列))",
                "SELECT EXTRACT(EPOCH FROM AGE(NOW(), t_ts)) FROM probe_ts");
    }

    /** upsert / RETURNING 在各方言（尤其 Kingbase 两种模式）的实际支持情况。 */
    private static void upsertAndReturningMatrix(Connection c, Target t) {
        System.out.println("  --- upsert / RETURNING 矩阵（信息项） ---");
        execQuiet(c, "DROP TABLE IF EXISTS probe_dup");
        execQuiet(c, "CREATE TEMPORARY TABLE probe_dup (k INT PRIMARY KEY, v INT)");
        execQuiet(c, "INSERT INTO probe_dup VALUES (1,1)");
        infoExec(c, t.key, "INSERT ... ON CONFLICT DO NOTHING",
                "INSERT INTO probe_dup VALUES (1,100) ON CONFLICT (k) DO NOTHING");
        infoExec(c, t.key, "INSERT ... ON DUPLICATE KEY UPDATE",
                "INSERT INTO probe_dup VALUES (1,200) ON DUPLICATE KEY UPDATE v = 200");
        infoQuery(c, t.key, "UPDATE ... RETURNING v",
                "UPDATE probe_dup SET v = v + 1 WHERE k = 1 RETURNING v");
    }

    /** 关键未知项：各方言可用的"当前时间"与时间差表达式。 */
    private static void timeSourceMatrix(Connection c, Target t) {
        System.out.println("  --- 时间源 / 时区 / 区间语法矩阵（信息项，不计入 pass/fail） ---");
        String[] selects = {
                "SELECT NOW()",
                "SELECT pg_typeof(NOW())",
                "SELECT CURRENT_TIMESTAMP",
                "SELECT CURRENT_TIMESTAMP(6)",
                "SELECT LOCALTIMESTAMP",
                "SELECT SYSTIMESTAMP",
                "SELECT NOW(6)",
                "SELECT TIMESTAMPDIFF(MICROSECOND, NOW(), NOW())",
                "SELECT TIMESTAMPDIFF(MICROSECOND, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "SELECT EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - CURRENT_TIMESTAMP))",
                "SELECT EXTRACT(EPOCH FROM (NOW() - NOW()))",
                "SELECT CURRENT_TIMESTAMP - INTERVAL '2 second'",
                "SELECT CURRENT_TIMESTAMP - INTERVAL 2 SECOND",
                "SELECT DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 SECOND)"
        };
        for (String sql : selects) {
            infoQuery(c, t.key, sql, sql);
        }
        execQuiet(c, "SET TIME ZONE 'UTC'");
        infoQuery(c, t.key, "[tz] SET TIME ZONE 'UTC' 后", "SELECT current_setting('TimeZone')");
        execQuiet(c, "SET time_zone='+00:00'");
        infoQuery(c, t.key, "[tz] SET time_zone='+00:00' 后", "SELECT current_setting('TimeZone')");
    }

    private static void execQuiet(Connection c, String sql) {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (Exception ignored) {
            // 信息性探测，忽略失败
        }
    }

    /** 信息项：执行 DML/DDL，记录影响行数或 UNSUPPORTED。 */
    private static void infoExec(Connection c, String target, String label, String sql) {
        try (Statement st = c.createStatement()) {
            boolean hasRs = st.execute(sql);
            recordInfo(target, label, hasRs ? "ok(resultset)" : "ok(affected=" + st.getUpdateCount() + ")");
        } catch (Exception e) {
            recordInfo(target, label, "UNSUPPORTED: " + msg(e));
        }
    }

    /** 设计文档 §6 的 SQL 规格冒烟测试（临时表，session 级）。 */
    private static void scenario(Connection c, Target t) {
        String numType = numType(t.profile);
        String tsType = tsType(t.profile);
        String now = nowExpr(t.profile);
        String consumeSql = consumeSql(t.profile);

        String ddl = "CREATE TEMPORARY TABLE " + TABLE + " ("
                + " namespace VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,"
                + " capacity " + numType + " NOT NULL, rate " + numType + " NOT NULL,"
                + " tokens " + numType + " NOT NULL, last_refill " + tsType + " NOT NULL,"
                + " PRIMARY KEY (namespace, name))";
        exec(c, t.key, "S1 DDL 建表(" + tsType + ")", "DROP TABLE IF EXISTS " + TABLE, true);
        exec(c, t.key, "S1 DDL 建表(" + tsType + ")", ddl, true);

        String insert = "INSERT INTO " + TABLE + " (namespace,name,capacity,rate,tokens,last_refill)"
                + " VALUES ('" + NS + "','" + NAME + "',10,0,10," + now + ")";
        exec(c, t.key, "S2 建桶 tokens=10, rate=0", insert, true);

        // S3: 复合 UPDATE 消耗 7（rate=0，纯扣减）
        int rows = update(c, t.key, "S3 tryConsume(7) 守卫+扣减", consumeSql, 7L, NS, NAME, 7L);
        checkScalar(c, t.key, "S4 剩余 tokens == 3",
                "SELECT tokens FROM " + TABLE + " WHERE namespace='" + NS + "' AND name='" + NAME + "'",
                new BigDecimal("3"));

        // S5: 存量不足 → 0 行
        int rows2 = update(c, t.key, "S5 tryConsume(5) 应被守卫拒绝", consumeSql, 5L, NS, NAME, 5L);
        record(t.key, "S5 守卫生效(0 行)", rows2 == 0, "affected=" + rows2);
        checkScalar(c, t.key, "S6 拒绝后 tokens 仍为 3",
                "SELECT tokens FROM " + TABLE + " WHERE namespace='" + NS + "' AND name='" + NAME + "'",
                new BigDecimal("3"));

        // S7: 惰性补充：清空并将 last_refill 回拨 2 秒，rate=1 → 可补 2，消耗 1 后剩 1
        String backdated = backdatedExpr(t.profile);
        exec(c, t.key, "S7 构造惰性补充场景(空桶+回拨2s+rate=1)",
                "UPDATE " + TABLE + " SET tokens=0, rate=1, last_refill=" + backdated
                        + " WHERE namespace='" + NS + "' AND name='" + NAME + "'", true);
        update(c, t.key, "S7 tryConsume(1) 惰性补充后成功", consumeSql, 1L, NS, NAME, 1L);
        checkBetween(c, t.key, "S8 补充后剩余 ∈ [1, 1.2) 且保留小数(D2)",
                "SELECT tokens FROM " + TABLE + " WHERE namespace='" + NS + "' AND name='" + NAME + "'",
                new BigDecimal("1"), new BigDecimal("1.2"));

        // S9: 手动投放
        String deposit = "UPDATE " + TABLE
                + " SET tokens = LEAST(capacity, tokens + GREATEST(0, " + elapsed(t.profile) + ") + ?),"
                + " last_refill = GREATEST(" + now + ", last_refill)"
                + " WHERE namespace=? AND name=?";
        update(c, t.key, "S9 deposit(5)", deposit, 5L, NS, NAME);

        // S10: 建桶竞态 upsert
        String upsert = "INSERT INTO " + TABLE + " (namespace,name,capacity,rate,tokens,last_refill)"
                + " VALUES ('" + NS + "','" + NAME + "',10,1,0," + now + ")"
                + upsertTail(t.profile);
        exec(c, t.key, "S10 createIfAbsent upsert", upsert, true);

        // S11: 查询有效令牌（纯 SELECT，不写行）
        query(c, t.key, "S11 get 有效令牌",
                "SELECT LEAST(capacity, tokens + GREATEST(0, " + elapsed(t.profile) + ")) AS effective_tokens,"
                        + " tokens AS stored_tokens, last_refill FROM " + TABLE
                        + " WHERE namespace='" + NS + "' AND name='" + NAME + "'");

        // S12: 删除
        exec(c, t.key, "S12 delete", "DELETE FROM " + TABLE
                + " WHERE namespace='" + NS + "' AND name='" + NAME + "'", true);

        // S12b: RETURNING 支持（对应“精确剩余值”开关）
        exec(c, t.key, "S12b 建 b2 tokens=5",
                "INSERT INTO " + TABLE + " (namespace,name,capacity,rate,tokens,last_refill)"
                        + " VALUES ('" + NS + "','b2',10,0,5," + now + ")", true);
        probeReturning(c, t.key, consumeSql, t.profile);
        exec(c, t.key, "S12c 清理 b2",
                "DELETE FROM " + TABLE + " WHERE namespace='" + NS + "' AND name='b2'", true);

        exec(c, t.key, "S13 清理", "DROP TABLE IF EXISTS " + TABLE, true);
        System.out.printf("  (S3 affected=%d)%n", rows);
    }

    /** MySQL 家族（含 Kingbase MySQL 模式）；两者的差异由 nowExpr / tsType 等收敛。 */
    private static boolean mysqlFamily(String profile) {
        return profile.startsWith("mysql") || profile.startsWith("kb-mysql");
    }

    /** Kingbase MySQL 模式不支持 NOW(6)（function NOW(integer) does not exist）。 */
    private static String nowExpr(String profile) {
        return "mysql".equals(profile) ? "NOW(6)" : "NOW()";
    }

    private static String numType(String profile) {
        return mysqlFamily(profile) ? "DECIMAL(20,6)" : "NUMERIC(20,6)";
    }

    private static String tsType(String profile) {
        if ("mysql".equals(profile)) {
            return "DATETIME(6)";
        }
        if (profile.startsWith("kb-mysql")) {
            return "TIMESTAMP(6)";
        }
        return "TIMESTAMPTZ";
    }

    private static String elapsed(String profile) {
        return mysqlFamily(profile)
                ? "TIMESTAMPDIFF(MICROSECOND, last_refill, " + nowExpr(profile) + ") / 1000000.0 * rate"
                : "EXTRACT(EPOCH FROM (" + nowExpr(profile) + " - last_refill)) * rate";
    }

    private static String backdatedExpr(String profile) {
        return "mysql".equals(profile)
                ? "DATE_SUB(NOW(6), INTERVAL 2 SECOND)"
                : "NOW() - INTERVAL '2 second'";
    }

    private static String upsertTail(String profile) {
        return "mysql".equals(profile)
                ? " ON DUPLICATE KEY UPDATE name = name"
                : " ON CONFLICT (namespace, name) DO NOTHING";
    }

    private static String consumeSql(String profile) {
        String eff = "LEAST(capacity, tokens + GREATEST(0, " + elapsed(profile) + "))";
        return "UPDATE " + TABLE + " SET tokens = " + eff + " - ?,"
                + " last_refill = GREATEST(" + nowExpr(profile) + ", last_refill)"
                + " WHERE namespace = ? AND name = ? AND " + eff + " >= ?";
    }

    // ---------- helpers ----------

    private static void probeReturning(Connection c, String target, String consumeSql, String profile) {
        Boolean expect = target.equals("mysql") ? Boolean.FALSE
                : ("pg".equals(profile) ? Boolean.TRUE : null);
        try (PreparedStatement ps = c.prepareStatement(consumeSql + " RETURNING tokens")) {
            ps.setObject(1, 1L);
            ps.setObject(2, NS);
            ps.setObject(3, "b2");
            ps.setObject(4, 1L);
            try (ResultSet rs = ps.executeQuery()) {
                String v = rs.next() ? rs.getString(1) : "<no row>";
                record(target, "S12b RETURNING tokens（精确剩余）", expect == null || expect, "tokens=" + v);
            }
        } catch (Exception e) {
            boolean ok = expect != null && !expect;
            record(target, "S12b RETURNING tokens（精确剩余）", ok,
                    e.getClass().getSimpleName() + ": " + msg(e));
        }
    }

    private static void probe(Connection c, String target, String label, String sql, Boolean expect) {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
            if (expect == null) {
                recordInfo(target, label, "ok");
            } else {
                record(target, label, expect, "ok");
            }
        } catch (Exception e) {
            if (expect == null) {
                recordInfo(target, label, "UNSUPPORTED: " + msg(e));
            } else {
                record(target, label, !expect, e.getClass().getSimpleName() + ": " + msg(e));
            }
        }
    }

    /** 信息项：打印查询结果或 UNSUPPORTED，不计入 pass/fail。 */
    private static void infoQuery(Connection c, String target, String label, String sql) {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            StringBuilder sb = new StringBuilder();
            if (rs.next()) {
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    if (i > 1) {
                        sb.append(' ');
                    }
                    sb.append(md.getColumnLabel(i)).append('=').append(rs.getString(i));
                }
            } else {
                sb.append("<no row>");
            }
            recordInfo(target, label, sb.toString());
        } catch (Exception e) {
            recordInfo(target, label, "UNSUPPORTED: " + msg(e));
        }
    }

    private static void query(Connection c, String target, String label, String sql) {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            StringBuilder sb = new StringBuilder();
            if (rs.next()) {
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    if (i > 1) {
                        sb.append(", ");
                    }
                    sb.append(md.getColumnLabel(i)).append('=').append(rs.getString(i));
                }
            } else {
                sb.append("<no row>");
            }
            record(target, label, true, sb.toString());
        } catch (Exception e) {
            record(target, label, false, e.getClass().getSimpleName() + ": " + msg(e));
        }
    }

    private static void exec(Connection c, String target, String label, String sql, boolean expectOk) {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
            record(target, label, expectOk, "ok");
        } catch (Exception e) {
            record(target, label, !expectOk, e.getClass().getSimpleName() + ": " + msg(e));
        }
    }

    private static int update(Connection c, String target, String label, String sql, Object... params) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            int rows = ps.executeUpdate();
            record(target, label, true, "affected=" + rows);
            return rows;
        } catch (Exception e) {
            record(target, label, false, e.getClass().getSimpleName() + ": " + msg(e));
            return -1;
        }
    }

    private static void checkScalar(Connection c, String target, String label, String sql, BigDecimal expected) {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            BigDecimal actual = rs.next() ? rs.getBigDecimal(1) : null;
            boolean ok = actual != null && actual.compareTo(expected) == 0;
            record(target, label, ok, "actual=" + actual + " expected=" + expected);
        } catch (Exception e) {
            record(target, label, false, e.getClass().getSimpleName() + ": " + msg(e));
        }
    }

    private static void checkBetween(Connection c, String target, String label, String sql,
                                     BigDecimal min, BigDecimal maxExclusive) {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            BigDecimal actual = rs.next() ? rs.getBigDecimal(1) : null;
            boolean ok = actual != null && actual.compareTo(min) >= 0 && actual.compareTo(maxExclusive) < 0;
            record(target, label, ok, "actual=" + actual + " expected=[" + min + ", " + maxExclusive + ")");
        } catch (Exception e) {
            record(target, label, false, e.getClass().getSimpleName() + ": " + msg(e));
        }
    }

    private static void recordInfo(String target, String label, String detail) {
        System.out.printf("  [ INFO ] %-46s %s%n", label, detail);
    }

    private static void record(String target, String label, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.printf("  [ PASS ] %-46s %s%n", label, detail);
        } else {
            fail++;
            failures.add(target + " / " + label + " -> " + detail);
            System.out.printf("  [ FAIL ] %-46s %s%n", label, detail);
        }
    }

    private static String msg(Exception e) {
        String m = e.getMessage();
        if (m == null) {
            return "";
        }
        m = m.replace('\n', ' ').replace('\r', ' ').trim();
        return m.length() > 160 ? m.substring(0, 160) + "..." : m;
    }
}
