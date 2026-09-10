package com.github.wcqtech.jakit.dbucket.autoconfigure;

import com.github.wcqtech.jakit.dbucket.BucketSpec;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

/**
 * Configuration of the dbucket starter. Prefix: {@code jakit.dbucket}.
 *
 * <p>Mirrors {@link com.github.wcqtech.jakit.dbucket.DbucketOptions} plus the wiring concerns
 * (datasource selection, dialect, DDL and startup checks).
 */
@ConfigurationProperties(prefix = "jakit.dbucket")
public class DbucketProperties {

    private boolean enabled = true;
    private String table = "dbucket";
    private String datasourceBeanName;
    private String dialect = "auto";
    private String namespace = "default";
    private boolean autoCreate = true;
    private boolean verifyOnStartup = true;
    private boolean failOpen = true;
    private boolean exactRemaining;
    private final Wait wait = new Wait();
    private final Create create = new Create();
    private final Ddl ddl = new Ddl();
    private final Metrics metrics = new Metrics();
    private final Kingbase kingbase = new Kingbase();
    private final Annotations annotations = new Annotations();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Bucket table name; may be {@code schema.table}. Defaults to {@code dbucket}. */
    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    /**
     * Bean name of the datasource to use. Empty means "the single datasource", honouring
     * {@code @Primary} when several exist.
     */
    public String getDatasourceBeanName() {
        return datasourceBeanName;
    }

    public void setDatasourceBeanName(String datasourceBeanName) {
        this.datasourceBeanName = datasourceBeanName;
    }

    /**
     * {@code auto} (detect from the connection), {@code mysql}, {@code postgresql} or {@code kingbase}.
     * An explicit value skips product detection but still verifies the MySQL session time zone.
     */
    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    /** Default namespace used by {@code Dbucket.bucket(name)} and by {@code @DBucket}. */
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Whether a missing bucket is created on the acquire path. Requires
     * {@link Create#getCapacity() create.capacity}; when it is missing, auto-creation is disabled with
     * a WARN instead of failing startup.
     */
    public boolean isAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    /** Whether a lightweight ping is executed once at startup. */
    public boolean isVerifyOnStartup() {
        return verifyOnStartup;
    }

    public void setVerifyOnStartup(boolean verifyOnStartup) {
        this.verifyOnStartup = verifyOnStartup;
    }

    /**
     * Whether acquire failures caused by storage errors are absorbed (traffic allowed through, ERROR
     * logged, {@code AcquireResult.degraded()} set). Reads, deposits and admin calls never fail-open.
     */
    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    /** Whether acquire asks the storage for the exact remaining balance. */
    public boolean isExactRemaining() {
        return exactRemaining;
    }

    public void setExactRemaining(boolean exactRemaining) {
        this.exactRemaining = exactRemaining;
    }

    public Wait getWait() {
        return wait;
    }

    public Create getCreate() {
        return create;
    }

    public Ddl getDdl() {
        return ddl;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public Kingbase getKingbase() {
        return kingbase;
    }

    public Annotations getAnnotations() {
        return annotations;
    }

    /** Polling behaviour of the blocking acquire. */
    public static class Wait {

        private Duration pollInterval = Duration.ofMillis(20);
        private boolean jitter = true;

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public boolean isJitter() {
            return jitter;
        }

        public void setJitter(boolean jitter) {
            this.jitter = jitter;
        }
    }

    /** Definition used when auto-creating a bucket. */
    public static class Create {

        private BigDecimal capacity;
        private BigDecimal rate = BigDecimal.ZERO;
        private BucketSpec.InitialState initial = BucketSpec.InitialState.FULL;

        /** Burst ceiling; required for auto-creation. */
        public BigDecimal getCapacity() {
            return capacity;
        }

        public void setCapacity(BigDecimal capacity) {
            this.capacity = capacity;
        }

        /** Refill rate in tokens per second; {@code 0} means manual filling only. */
        public BigDecimal getRate() {
            return rate;
        }

        public void setRate(BigDecimal rate) {
            this.rate = rate;
        }

        /** {@code FULL} or {@code EMPTY}. */
        public BucketSpec.InitialState getInitial() {
            return initial;
        }

        public void setInitial(BucketSpec.InitialState initial) {
            this.initial = initial;
        }
    }

    /** Schema management. */
    public static class Ddl {

        private boolean autoInit;

        /** Whether the embedded {@code CREATE TABLE IF NOT EXISTS} runs at startup. */
        public boolean isAutoInit() {
            return autoInit;
        }

        public void setAutoInit(boolean autoInit) {
            this.autoInit = autoInit;
        }
    }

    /** Optional Micrometer integration. */
    public static class Metrics {

        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** KingbaseES specifics: KingbaseES cannot be told apart from PostgreSQL by product name alone. */
    public static class Kingbase {

        private String sqlProfile = "auto";
        private boolean detectMode = true;

        /**
         * {@code auto} (query {@code SHOW database_mode} when the dialect is explicitly
         * {@code kingbase}), {@code pg} or {@code mysql}.
         */
        public String getSqlProfile() {
            return sqlProfile;
        }

        public void setSqlProfile(String sqlProfile) {
            this.sqlProfile = sqlProfile;
        }

        /**
         * Whether {@code SHOW database_mode} may be queried. When {@code false} the profile has to be
         * set explicitly; note that {@code dialect=auto} always probes, because it has to find out
         * whether the database is KingbaseES at all.
         */
        public boolean isDetectMode() {
            return detectMode;
        }

        public void setDetectMode(boolean detectMode) {
            this.detectMode = detectMode;
        }
    }

    /** {@code @DBucket} aspect. */
    public static class Annotations {

        private boolean enabled = true;
        private int order = Ordered.LOWEST_PRECEDENCE - 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Advice order; the default runs outside Spring's transaction advice so tokens are acquired
         * before a transaction starts.
         */
        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }
    }
}
