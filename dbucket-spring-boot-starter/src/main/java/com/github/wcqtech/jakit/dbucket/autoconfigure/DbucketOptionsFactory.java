package com.github.wcqtech.jakit.dbucket.autoconfigure;

import com.github.wcqtech.jakit.dbucket.DbucketOptions;
import java.math.BigDecimal;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Turns {@link DbucketProperties} into {@link DbucketOptions}.
 *
 * <p>The one non-obvious rule: {@code auto-create=true} without {@code create.capacity} disables
 * auto-creation with a WARN rather than failing startup, because buckets can still be created through
 * {@link com.github.wcqtech.jakit.dbucket.DbucketAdmin} or by an explicit
 * {@code DbucketOptions.autoCreateSpec(...)} bean.
 */
final class DbucketOptionsFactory {

    private static final Log LOG = LogFactory.getLog(DbucketOptionsFactory.class);

    private DbucketOptionsFactory() {
    }

    static DbucketOptions toOptions(DbucketProperties properties) {
        DbucketOptions.Builder builder = DbucketOptions.builder()
                .defaultNamespace(properties.getNamespace())
                .failOpen(properties.isFailOpen())
                .exactRemaining(properties.isExactRemaining())
                .pollInterval(properties.getWait().getPollInterval())
                .jitter(properties.getWait().isJitter());

        if (properties.isAutoCreate()) {
            BigDecimal capacity = properties.getCreate().getCapacity();
            if (capacity == null) {
                LOG.warn("jakit.dbucket.auto-create is enabled but jakit.dbucket.create.capacity is not"
                        + " set: auto-creation is disabled for this application; create buckets through"
                        + " DbucketAdmin or configure jakit.dbucket.create.capacity");
            } else {
                builder.autoCreateSpec(capacity, properties.getCreate().getRate(),
                        properties.getCreate().getInitial());
            }
        }
        return builder.build();
    }
}
