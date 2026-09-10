package com.github.wcqtech.jakit.dbucket.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DialectSqlTest {

    private static final String TABLE = "my_buckets";

    private final DialectSql mysql = DialectSqls.mysql(TABLE);
    private final DialectSql postgres = DialectSqls.postgresql(TABLE);
    private final DialectSql kingbasePg = DialectSqls.kingbase(TABLE, KingbaseMode.PG);
    private final DialectSql kingbaseMysql = DialectSqls.kingbase(TABLE, KingbaseMode.MYSQL);

    private static int placeholders(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }

    @Test
    void reportsDialectIdentity() {
        assertEquals(Dialect.MYSQL, mysql.dialect());
        assertEquals("mysql", mysql.dialectName());
        assertEquals(Dialect.POSTGRESQL, postgres.dialect());
        assertEquals("postgresql", postgres.dialectName());
        assertEquals(Dialect.KINGBASE, kingbasePg.dialect());
        assertEquals("kingbase(pg)", kingbasePg.dialectName());
        assertEquals(Dialect.KINGBASE, kingbaseMysql.dialect());
        assertEquals("kingbase(mysql)", kingbaseMysql.dialectName());
    }

    @Test
    void onlyPostgresFamiliesSupportReturning() {
        assertFalse(mysql.supportsReturning());
        assertTrue(postgres.supportsReturning());
        assertTrue(kingbasePg.supportsReturning());
        assertTrue(kingbaseMysql.supportsReturning());

        assertThrows(UnsupportedOperationException.class, mysql::tryConsumeReturning);
        for (DialectSql sql : List.of(postgres, kingbasePg, kingbaseMysql)) {
            assertTrue(sql.tryConsumeReturning().endsWith("RETURNING tokens"), sql.dialectName());
        }
    }

    @Test
    void mysqlUsesMysqlFragmentsOnly() {
        assertTrue(mysql.tryConsume().contains("TIMESTAMPDIFF(MICROSECOND, last_refill, NOW(6))"));
        assertTrue(mysql.tryConsume().contains("GREATEST(NOW(6), last_refill)"));
        assertTrue(mysql.tryConsume().contains("GREATEST(0,"));
        assertTrue(mysql.createIfAbsent().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(mysql.createTable().contains("ENGINE=InnoDB"));
        assertTrue(mysql.createTable().contains("DATETIME(6)"));

        assertFalse(mysql.tryConsume().contains("EXTRACT(EPOCH"));
        assertFalse(mysql.get().contains("RETURNING"));
        assertFalse(mysql.createTable().contains("TIMESTAMPTZ"));
        assertFalse(mysql.createTable().contains("ON CONFLICT"));
    }

    @Test
    void postgresUsesPostgresFragmentsOnly() {
        assertTrue(postgres.tryConsume().contains("EXTRACT(EPOCH FROM (NOW() - last_refill))"));
        assertTrue(postgres.tryConsume().contains("GREATEST(0,"));
        assertTrue(postgres.createIfAbsent().contains("ON CONFLICT (namespace, name) DO NOTHING"));
        assertTrue(postgres.createTable().contains("TIMESTAMPTZ"));
        assertTrue(postgres.createTable().contains("NUMERIC(20,6)"));

        assertFalse(postgres.tryConsume().contains("NOW(6)"));
        assertFalse(postgres.tryConsume().contains("TIMESTAMPDIFF"));
        assertFalse(postgres.createTable().contains("ENGINE"));
    }

    @Test
    void kingbasePgReusesPostgresStatements() {
        assertEquals(postgres.createTable(), kingbasePg.createTable());
        assertEquals(postgres.tryConsume(), kingbasePg.tryConsume());
        assertEquals(postgres.createIfAbsent(), kingbasePg.createIfAbsent());
        assertEquals(Dialect.KINGBASE, kingbasePg.dialect());
    }

    @Test
    void kingbaseMysqlAvoidsBothModeTraps() {
        assertTrue(kingbaseMysql.tryConsume().contains("TIMESTAMPDIFF(MICROSECOND, last_refill, NOW())"));
        assertTrue(kingbaseMysql.tryConsume().contains("GREATEST(NOW()::timestamptz, last_refill)"));
        assertTrue(kingbaseMysql.createIfAbsent().contains("NOW()::timestamptz"));
        assertTrue(kingbaseMysql.createIfAbsent().contains("ON CONFLICT (namespace, name) DO NOTHING"));
        assertTrue(kingbaseMysql.createTable().contains("TIMESTAMPTZ"));
        assertTrue(kingbaseMysql.createTable().contains("DECIMAL(20,6)"));
        assertTrue(kingbaseMysql.tryConsumeReturning().endsWith("RETURNING tokens"));

        // NOW(6) does not exist in KingbaseES MySQL mode.
        assertFalse(kingbaseMysql.tryConsume().contains("NOW(6)"));
        // Timestamp subtraction is numeric there, so EXTRACT(EPOCH ...) cannot be used.
        assertFalse(kingbaseMysql.tryConsume().contains("EXTRACT(EPOCH"));
        // MySQL table options are a syntax error on KingbaseES.
        assertFalse(kingbaseMysql.createTable().contains("ENGINE"));
        assertFalse(postgres.createTable().contains("ENGINE"));
    }

    @Test
    void unzonedLastRefillOnlyForMysql() {
        assertFalse(mysql.zonedLastRefill());
        assertTrue(postgres.zonedLastRefill());
        assertTrue(kingbasePg.zonedLastRefill());
        assertTrue(kingbaseMysql.zonedLastRefill());
    }

    @Test
    void existsAndPingAreDialectIndependent() {
        assertEquals("SELECT 1", mysql.ping());
        assertEquals(mysql.ping(), postgres.ping());
        assertEquals(mysql.exists(), kingbaseMysql.exists());
        assertTrue(mysql.exists().contains("WHERE namespace = ? AND name = ?"));
    }

    @Test
    void placeholderCountsMatchTheDocumentedOrder() {
        for (DialectSql sql : List.of(mysql, postgres, kingbasePg, kingbaseMysql)) {
            String name = sql.dialectName();
            assertEquals(5, placeholders(sql.createIfAbsent()), name + " createIfAbsent");
            assertEquals(2, placeholders(sql.get()), name + " get");
            assertEquals(2, placeholders(sql.exists()), name + " exists");
            assertEquals(4, placeholders(sql.tryConsume()), name + " tryConsume");
            assertEquals(3, placeholders(sql.deposit()), name + " deposit");
            assertEquals(4, placeholders(sql.adjustCapacity()), name + " adjustCapacity");
            assertEquals(3, placeholders(sql.adjustRate()), name + " adjustRate");
            assertEquals(2, placeholders(sql.delete()), name + " delete");
            assertEquals(0, placeholders(sql.ping()), name + " ping");
            if (sql.supportsReturning()) {
                assertEquals(4, placeholders(sql.tryConsumeReturning()), name + " tryConsumeReturning");
            }
        }
    }

    @Test
    void everyStatementUsesTheConfiguredTable() {
        DialectSql sql = DialectSqls.kingbase("myschema.buckets", KingbaseMode.MYSQL);

        assertEquals("myschema.buckets", sql.tableName());
        for (String statement : List.of(sql.createTable(), sql.createIfAbsent(), sql.get(),
                sql.exists(), sql.tryConsume(), sql.tryConsumeReturning(), sql.deposit(),
                sql.adjustCapacity(), sql.adjustRate(), sql.delete())) {
            assertTrue(statement.contains("myschema.buckets"), statement);
        }
    }

    @Test
    void rejectsUnsafeTableNames() {
        assertThrows(IllegalArgumentException.class, () -> DialectSqls.mysql("buckets; DROP TABLE users"));
        assertThrows(IllegalArgumentException.class, () -> DialectSqls.mysql("1buckets"));
        assertThrows(IllegalArgumentException.class, () -> DialectSqls.mysql("a.b.c"));
        assertThrows(IllegalArgumentException.class, () -> DialectSqls.mysql(""));
        assertThrows(IllegalArgumentException.class, () -> DialectSqls.mysql(null));
        assertThrows(IllegalArgumentException.class, () -> DialectSqls.mysql("buckets--"));
    }

    @Test
    void acceptsDefaultAndQualifiedTableNames() {
        assertEquals(DialectSql.DEFAULT_TABLE, DialectSqls.mysql(DialectSql.DEFAULT_TABLE).tableName());
        assertEquals("public.buckets", DialectSqls.postgresql(" public.buckets ").tableName());
    }

    @Test
    void factoryRequiresKingbaseMode() {
        assertThrows(IllegalArgumentException.class, () -> DialectSqls.forDialect(Dialect.KINGBASE, TABLE));
        assertThrows(NullPointerException.class, () -> DialectSqls.kingbase(TABLE, null));
        assertEquals(Dialect.MYSQL, DialectSqls.forDialect(Dialect.MYSQL, TABLE).dialect());
        assertEquals(Dialect.POSTGRESQL, DialectSqls.forDialect(Dialect.POSTGRESQL, TABLE).dialect());
    }
}
