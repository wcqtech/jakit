package com.github.wcqtech.jakit.utils.mock;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import com.github.wcqtech.jakit.utils.mock.mocker.BigDecimalMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.BigIntegerMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.BooleanMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.ByteMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.CharacterMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.DoubleMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.FloatMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.IntegerMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.LocalDateTimeMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.LongMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.ShortMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.StringMocker;

/**
 * Registry of the type-to-{@link Mocker} rules used when a field has no
 * explicit {@code @MockWith} annotation.
 *
 * <p>The registry is pre-populated with the built-in mockers for the common
 * JDK types (String, the numeric wrappers, BigDecimal, BigInteger and
 * LocalDateTime). Additional rules can be registered for any type, including
 * application domain types and JDK types without a built-in mocker; a
 * registered rule always beats the recursive fallback that mocks plain
 * user-defined classes field by field.
 *
 * <p>Precedence: rules are scanned from the most recently registered to the
 * oldest (later registration wins, which also allows overriding a built-in).
 * A rule registered for type {@code T} also matches any subtype of {@code T},
 * so registering a mocker for {@code Number.class} covers {@code Integer}
 * fields unless a more specific or later rule applies. Primitive types are
 * matched through their wrapper type ({@code int} resolves to the
 * {@link IntegerMocker}).
 *
 * <p>The registry is safe for concurrent registration and lookup.
 */
public final class Mockers {

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    static {
        registerDefaults();
    }

    private Mockers() {
    }

    /**
     * Registers a mocker for the given type; later registrations take
     * precedence over earlier ones for overlapping types.
     *
     * @param type the type handled by the mocker; must not be null
     * @param mocker the mocker to use; must not be null
     */
    public static void register(Class<?> type, Mocker<?> mocker) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(mocker, "mocker must not be null");
        ENTRIES.add(new Entry(type, mocker));
    }

    /**
     * Finds the mocker applicable to the given type, or null when no rule
     * matches. Primitive types are resolved through their wrapper type.
     *
     * @param type the type to look up; must not be null
     * @return the applicable mocker, or null if none is registered
     */
    public static Mocker<?> find(Class<?> type) {
        Objects.requireNonNull(type, "type must not be null");
        Class<?> key = box(type);
        List<Entry> entries = ENTRIES;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (entry.type.isAssignableFrom(key)) {
                return entry.mocker;
            }
        }
        return null;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        return type; // void and any other primitive have no wrapper to use
    }

    private static void registerDefaults() {
        register(String.class, new StringMocker());
        register(Integer.class, new IntegerMocker());
        register(Long.class, new LongMocker());
        register(Short.class, new ShortMocker());
        register(Byte.class, new ByteMocker());
        register(Boolean.class, new BooleanMocker());
        register(Character.class, new CharacterMocker());
        register(Float.class, new FloatMocker());
        register(Double.class, new DoubleMocker());
        register(BigDecimal.class, new BigDecimalMocker());
        register(BigInteger.class, new BigIntegerMocker());
        register(LocalDateTime.class, new LocalDateTimeMocker());
    }

    private static final class Entry {

        private final Class<?> type;
        private final Mocker<?> mocker;

        private Entry(Class<?> type, Mocker<?> mocker) {
            this.type = type;
            this.mocker = mocker;
        }
    }
}
