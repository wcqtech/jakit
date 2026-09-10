package com.github.wcqtech.jakit.dbucket.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcBucketStoreTest {

    private final JdbcBucketStore store = new JdbcBucketStore(new UnreachableDataSource(),
            DialectSqls.mysql(DialectSql.DEFAULT_TABLE));

    @Test
    void exposesDialectMetadata() {
        assertEquals("mysql", store.dialectName());
        assertEquals(DialectSql.DEFAULT_TABLE, store.tableName());
        assertEquals(Dialect.MYSQL, store.dialectSql().dialect());
    }

    @Test
    void validatesArgumentsBeforeTouchingTheDatabase() {
        assertThrows(IllegalArgumentException.class, () -> store.tryConsume("ns", "b", 0, false));
        assertThrows(IllegalArgumentException.class, () -> store.tryConsume("ns", "b", -1, true));
        assertThrows(IllegalArgumentException.class, () -> store.deposit("ns", "b", 0));
        assertThrows(IllegalArgumentException.class,
                () -> store.adjustCapacity("ns", "b", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> store.adjustCapacity("ns", "b", null));
        assertThrows(IllegalArgumentException.class,
                () -> store.adjustRate("ns", "b", new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> store.adjustCapacity("ns", "b", new BigDecimal("1.0000001")));
        assertThrows(NullPointerException.class, () -> store.createIfAbsent(null));
    }

    @Test
    void wrapsConnectionFailuresInStoreExceptions() {
        DbucketStoreFailure failure = null;
        try {
            store.get("ns", "b");
        } catch (com.github.wcqtech.jakit.dbucket.DbucketStoreException e) {
            failure = new DbucketStoreFailure(e.getMessage(), e.getCause());
        }
        assertNotNull(failure, "expected a DbucketStoreException");
        assertTrue(failure.message().contains("read bucket"), failure.message());
        assertTrue(failure.message().contains("connection refused"), failure.message());
        assertNotNull(failure.cause());
    }

    @Test
    void rejectsNullDependencies() {
        assertThrows(NullPointerException.class,
                () -> new JdbcBucketStore(null, DialectSqls.mysql(DialectSql.DEFAULT_TABLE)));
        assertThrows(NullPointerException.class,
                () -> new JdbcBucketStore(new UnreachableDataSource(), null));
    }

    private record DbucketStoreFailure(String message, Throwable cause) {
    }

    /** Minimal datasource that never hands out a connection. */
    private static final class UnreachableDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("connection refused");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("connection refused");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // no-op
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // no-op
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
