package com.github.wcqtech.jakit.dbucket.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.wcqtech.jakit.dbucket.store.Dialect;
import com.github.wcqtech.jakit.dbucket.store.KingbaseMode;
import org.junit.jupiter.api.Test;

class DialectSettingsTest {

    private static DbucketProperties properties(String dialect) {
        DbucketProperties properties = new DbucketProperties();
        properties.setDialect(dialect);
        return properties;
    }

    @Test
    void autoMeansDetectFromTheConnection() {
        DialectSettings settings = DialectSettings.parse(properties("auto"));

        assertNull(settings.dialect());
        assertNull(settings.kingbaseMode());
    }

    @Test
    void mapsExplicitProducts() {
        assertEquals(Dialect.MYSQL, DialectSettings.parse(properties("mysql")).dialect());
        assertEquals(Dialect.POSTGRESQL, DialectSettings.parse(properties("postgresql")).dialect());
        assertEquals(Dialect.POSTGRESQL, DialectSettings.parse(properties("pg")).dialect());
        assertNull(DialectSettings.parse(properties("mysql")).kingbaseMode());
    }

    @Test
    void mapsKingbaseProfiles() {
        DbucketProperties kingbasePg = properties("kingbase");
        kingbasePg.getKingbase().setSqlProfile("pg");
        assertEquals(Dialect.KINGBASE, DialectSettings.parse(kingbasePg).dialect());
        assertEquals(KingbaseMode.PG, DialectSettings.parse(kingbasePg).kingbaseMode());

        DbucketProperties kingbaseMysql = properties("kingbase8");
        kingbaseMysql.getKingbase().setSqlProfile("mysql");
        assertEquals(KingbaseMode.MYSQL, DialectSettings.parse(kingbaseMysql).kingbaseMode());

        DbucketProperties kingbaseAuto = properties("kingbase");
        assertNull(DialectSettings.parse(kingbaseAuto).kingbaseMode());
    }

    @Test
    void kingbaseAutoProfileRequiresDetectMode() {
        DbucketProperties properties = properties("kingbase");
        properties.getKingbase().setDetectMode(false);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DialectSettings.parse(properties));
        assertEquals(true, failure.getMessage().contains("detect-mode"));
    }

    @Test
    void rejectsUnknownValues() {
        assertThrows(IllegalStateException.class, () -> DialectSettings.parse(properties("h2")));

        DbucketProperties oracle = properties("kingbase");
        oracle.getKingbase().setSqlProfile("oracle");
        assertThrows(IllegalStateException.class, () -> DialectSettings.parse(oracle));
    }
}
