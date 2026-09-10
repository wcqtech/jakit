package com.github.wcqtech.jakit.dbucket.store;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class KingbaseModeTest {

    @Test
    void mapsSupportedDatabaseModes() {
        assertEquals(Optional.of(KingbaseMode.MYSQL), KingbaseMode.fromDatabaseMode("mysql"));
        assertEquals(Optional.of(KingbaseMode.MYSQL), KingbaseMode.fromDatabaseMode(" MySQL "));
        assertEquals(Optional.of(KingbaseMode.PG), KingbaseMode.fromDatabaseMode("pg"));
        assertEquals(Optional.of(KingbaseMode.PG), KingbaseMode.fromDatabaseMode("postgres"));
        assertEquals(Optional.of(KingbaseMode.PG), KingbaseMode.fromDatabaseMode("PostgreSQL"));
    }

    @Test
    void rejectsUnsupportedDatabaseModes() {
        assertEquals(Optional.empty(), KingbaseMode.fromDatabaseMode("oracle"));
        assertEquals(Optional.empty(), KingbaseMode.fromDatabaseMode("sqlserver"));
        assertEquals(Optional.empty(), KingbaseMode.fromDatabaseMode(""));
        assertEquals(Optional.empty(), KingbaseMode.fromDatabaseMode(null));
    }
}
