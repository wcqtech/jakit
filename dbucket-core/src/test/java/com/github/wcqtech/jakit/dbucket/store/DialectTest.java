package com.github.wcqtech.jakit.dbucket.store;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DialectTest {

    @Test
    void resolvesJdbcUrls() {
        assertEquals(Optional.of(Dialect.MYSQL),
                Dialect.fromJdbcUrl("jdbc:mysql://localhost:3306/test?connectionTimeZone=UTC"));
        assertEquals(Optional.of(Dialect.POSTGRESQL),
                Dialect.fromJdbcUrl("jdbc:postgresql://localhost:54323/testdb"));
        assertEquals(Optional.of(Dialect.KINGBASE),
                Dialect.fromJdbcUrl("jdbc:kingbase8://localhost:54321/test"));
        assertEquals(Optional.of(Dialect.KINGBASE),
                Dialect.fromJdbcUrl("  JDBC:Kingbase8://localhost:54322/test  "));
    }

    @Test
    void doesNotGuessUnknownSubProtocols() {
        assertEquals(Optional.empty(), Dialect.fromJdbcUrl("jdbc:mariadb://localhost/test"));
        assertEquals(Optional.empty(), Dialect.fromJdbcUrl("mysql://localhost/test"));
        assertEquals(Optional.empty(), Dialect.fromJdbcUrl(null));
        assertEquals(Optional.empty(), Dialect.fromJdbcUrl(" "));
    }

    @Test
    void resolvesProductNames() {
        assertEquals(Optional.of(Dialect.MYSQL), Dialect.fromProductName("MySQL 8.0.42"));
        assertEquals(Optional.of(Dialect.POSTGRESQL), Dialect.fromProductName("PostgreSQL 18.6"));
        assertEquals(Optional.of(Dialect.KINGBASE), Dialect.fromProductName("KingbaseES 12.1"));
    }

    @Test
    void prefersKingbaseWhenProductNameMentionsBoth() {
        assertEquals(Optional.of(Dialect.KINGBASE),
                Dialect.fromProductName("KingbaseES (PostgreSQL compatible)"));
    }

    @Test
    void ignoresUnknownOrMissingProductNames() {
        assertEquals(Optional.empty(), Dialect.fromProductName("H2"));
        assertEquals(Optional.empty(), Dialect.fromProductName(null));
    }
}
