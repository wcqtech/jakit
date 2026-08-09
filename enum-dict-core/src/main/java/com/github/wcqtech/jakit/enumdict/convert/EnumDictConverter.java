package com.github.wcqtech.jakit.enumdict.convert;

import com.github.wcqtech.jakit.enumdict.DictItem;
import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;
import com.github.wcqtech.jakit.enumdict.i18n.DictValueResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Converts dictionary keys on object fields into dictionary values.
 *
 * Fields annotated with {@link DictField} are resolved through the
 * registry. Nested convertible beans, collections, maps and object arrays are
 * traversed recursively; containers are recognized by their runtime type, so
 * raw or wildcard generic fields are supported as well.
 *
 * Conversion mutates the given objects in place. Records, final fields and
 * JDK value types are never written. Map keys must remain stable for the
 * business code after conversion; callers are responsible for keeping key
 * {@code hashCode}/{@code equals} independent of converted fields.
 */
public final class EnumDictConverter {

    private final EnumDictRegistry registry;
    private final MissingPolicy missingPolicy;
    private final DictValueResolver labelResolver;
    private final Map<Class<?>, ClassPlan> planCache = Collections.synchronizedMap(new WeakHashMap<>());

    public EnumDictConverter(EnumDictRegistry registry) {
        this(registry, MissingPolicy.IGNORE);
    }

    public EnumDictConverter(EnumDictRegistry registry, MissingPolicy missingPolicy) {
        this(registry, null, missingPolicy);
    }

    public EnumDictConverter(EnumDictRegistry registry, DictValueResolver labelResolver) {
        this(registry, labelResolver, MissingPolicy.IGNORE);
    }

    public EnumDictConverter(EnumDictRegistry registry, DictValueResolver labelResolver,
                             MissingPolicy missingPolicy) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.labelResolver = labelResolver;
        this.missingPolicy = Objects.requireNonNull(missingPolicy, "missingPolicy must not be null");
    }

    /**
     * Converts the given object in place and returns the same instance.
     *
     * If the target is a collection, a map or an object array, its contents
     * are converted as well. Null elements inside containers are skipped.
     *
     * @param target the object to convert
     * @param <T> the object type
     * @return the same object instance
     * @throws NullPointerException if {@code target} is null
     * @throws EnumDictConvertException when the missing policy is
     *         {@code FAIL} and a dictionary key is not found
     */
    public <T> T convert(T target) {
        Objects.requireNonNull(target, "target must not be null");
        handleValue(target, newVisitedSet(), null);
        return target;
    }

    /**
     * Converts the given object in place using the resolved display label for
     * the given locale.
     *
     * @param target the object to convert
     * @param locale the target locale; {@code null} resolves to
     *        {@link Locale#getDefault()}
     * @param <T> the object type
     * @return the same object instance
     * @throws NullPointerException if {@code target} is null
     * @throws EnumDictConvertException when the missing policy is
     *         {@code FAIL} and a dictionary key is not found
     */
    public <T> T convert(T target, Locale locale) {
        Objects.requireNonNull(target, "target must not be null");
        handleValue(target, newVisitedSet(), resolveLocale(locale));
        return target;
    }

    /**
     * Converts every element of the given collection. Null elements are
     * skipped.
     *
     * @param targets the elements to convert
     * @param <T> the element type
     * @throws NullPointerException if {@code targets} is null
     * @throws EnumDictConvertException when the missing policy is
     *         {@code FAIL} and a dictionary key is not found
     */
    public <T> void convert(Collection<T> targets) {
        Objects.requireNonNull(targets, "targets must not be null");
        Set<Object> visited = newVisitedSet();
        for (T element : targets) {
            if (element != null) {
                handleValue(element, visited, null);
            }
        }
    }

    /**
     * Converts every element of the given collection using the resolved
     * display label for the given locale.
     *
     * Note: this overload and {@code convert(Collection, Consumer)} share the
     * same first parameter type, so passing a bare {@code null} as the second
     * argument is ambiguous and will not compile. Pass an explicitly typed
     * {@link Locale} or {@link Consumer}, or use the single-argument
     * {@code convert(Collection)} when neither is needed.
     *
     * @param targets the elements to convert
     * @param locale the target locale; {@code null} resolves to
     *        {@link Locale#getDefault()}
     * @param <T> the element type
     * @throws NullPointerException if {@code targets} is null
     * @throws EnumDictConvertException when the missing policy is
     *         {@code FAIL} and a dictionary key is not found
     */
    public <T> void convert(Collection<T> targets, Locale locale) {
        Objects.requireNonNull(targets, "targets must not be null");
        Set<Object> visited = newVisitedSet();
        Locale targetLocale = resolveLocale(locale);
        for (T element : targets) {
            if (element != null) {
                handleValue(element, visited, targetLocale);
            }
        }
    }

    /**
     * Converts every element of the given collection, then invokes the visitor
     * on each converted element. Null elements are skipped and are not passed
     * to the visitor.
     *
     * @param targets the elements to convert
     * @param visitor invoked after each element is converted
     * @param <T> the element type
     * @throws NullPointerException if {@code targets} or {@code visitor} is null
     * @throws EnumDictConvertException when the missing policy is
     *         {@code FAIL} and a dictionary key is not found
     */
    public <T> void convert(Collection<T> targets, Consumer<? super T> visitor) {
        Objects.requireNonNull(targets, "targets must not be null");
        Objects.requireNonNull(visitor, "visitor must not be null");
        Set<Object> visited = newVisitedSet();
        for (T element : targets) {
            if (element != null) {
                handleValue(element, visited, null);
                visitor.accept(element);
            }
        }
    }

    /**
     * Converts every element of the given collection using the resolved
     * display label for the given locale, then invokes the visitor on each
     * converted element.
     *
     * @param targets the elements to convert
     * @param visitor invoked after each element is converted
     * @param locale the target locale; {@code null} resolves to
     *        {@link Locale#getDefault()}
     * @param <T> the element type
     * @throws NullPointerException if {@code targets} or {@code visitor} is null
     * @throws EnumDictConvertException when the missing policy is
     *         {@code FAIL} and a dictionary key is not found
     */
    public <T> void convert(Collection<T> targets, Consumer<? super T> visitor, Locale locale) {
        Objects.requireNonNull(targets, "targets must not be null");
        Objects.requireNonNull(visitor, "visitor must not be null");
        Set<Object> visited = newVisitedSet();
        Locale targetLocale = resolveLocale(locale);
        for (T element : targets) {
            if (element != null) {
                handleValue(element, visited, targetLocale);
                visitor.accept(element);
            }
        }
    }

    private void handleValue(Object value, Set<Object> visited, Locale locale) {
        if (value == null || !visited.add(value)) {
            return;
        }
        if (value instanceof Object[] array) {
            for (Object element : array) {
                handleValue(element, visited, locale);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                handleValue(element, visited, locale);
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                handleValue(entry.getKey(), visited, locale);
                handleValue(entry.getValue(), visited, locale);
            }
            return;
        }
        if (isConvertibleBean(value.getClass())) {
            convertBean(value, visited, locale);
        }
    }

    private void convertBean(Object bean, Set<Object> visited, Locale locale) {
        ClassPlan plan = planOf(bean.getClass());
        for (DictFieldPlan dictField : plan.dictFields) {
            applyDictField(bean, dictField, locale);
        }
        for (Field field : plan.fields) {
            handleValue(readField(field, bean), visited, locale);
        }
    }

    private void applyDictField(Object bean, DictFieldPlan dictField, Locale locale) {
        if (Modifier.isFinal(dictField.field.getModifiers())) {
            return;
        }
        Object keyValue = readField(dictField.keyField, bean);
        if (keyValue == null) {
            return;
        }
        String key = String.valueOf(keyValue);
        Optional<DictItem> item = registry.get(dictField.type, key);
        if (item.isEmpty()) {
            if (missingPolicy == MissingPolicy.FAIL) {
                throw new EnumDictConvertException("No dictionary item found for type '" + dictField.type
                        + "', key '" + key + "' while converting "
                        + bean.getClass().getName() + "." + dictField.field.getName());
            }
            return;
        }
        DictItem dictItem = item.orElseThrow();
        writeField(dictField.field, bean, resolveLabel(dictItem, locale));
    }

    private String resolveLabel(DictItem item, Locale locale) {
        if (locale == null || labelResolver == null) {
            return item.value();
        }
        return labelResolver.resolve(item.type(), item.key(), item.i18nKey(), item.value(), locale);
    }

    private ClassPlan planOf(Class<?> type) {
        ClassPlan cached = planCache.get(type);
        if (cached != null) {
            return cached;
        }
        ClassPlan plan = buildPlan(type);
        planCache.put(type, plan);
        return plan;
    }

    private static ClassPlan buildPlan(Class<?> type) {
        Map<String, Field> fieldsByName = new LinkedHashMap<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    fieldsByName.putIfAbsent(field.getName(), field);
                }
            }
            current = current.getSuperclass();
        }
        List<Field> fields = List.copyOf(fieldsByName.values());
        List<DictFieldPlan> dictFields = new ArrayList<>();
        for (Field field : fields) {
            DictField annotation = field.getAnnotation(DictField.class);
            if (annotation == null) {
                continue;
            }
            if (field.getType() != String.class) {
                throw new IllegalArgumentException("@DictField is only supported on String fields, but "
                        + type.getName() + "." + field.getName() + " has type " + field.getType().getName());
            }
            String keyName = annotation.keyField().isBlank() ? field.getName() : annotation.keyField();
            Field keyField = fieldsByName.get(keyName);
            if (keyField == null) {
                throw new IllegalArgumentException("@DictField on " + type.getName() + "." + field.getName()
                        + " references a missing key field '" + keyName + "'");
            }
            makeAccessible(field);
            makeAccessible(keyField);
            dictFields.add(new DictFieldPlan(field, keyField, annotation.type()));
        }
        for (Field field : fields) {
            makeAccessible(field);
        }
        return new ClassPlan(fields, List.copyOf(dictFields));
    }

    private static void makeAccessible(Field field) {
        if (!field.trySetAccessible()) {
            throw new IllegalStateException("Cannot access field " + field);
        }
    }

    private static Object readField(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read field " + field, e);
        }
    }

    private static void writeField(Field field, Object target, String value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot write field " + field, e);
        }
    }

    private static boolean isConvertibleBean(Class<?> type) {
        if (type.isArray() || type.isEnum() || type.isRecord() || type.isPrimitive()) {
            return false;
        }
        String name = type.getName();
        return !(name.startsWith("java.") || name.startsWith("javax.")
                || name.startsWith("jdk.") || name.startsWith("sun.") || name.startsWith("com.sun."));
    }

    private static Set<Object> newVisitedSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static Locale resolveLocale(Locale locale) {
        return locale != null ? locale : Locale.getDefault();
    }

    private record ClassPlan(List<Field> fields, List<DictFieldPlan> dictFields) {
    }

    private record DictFieldPlan(Field field, Field keyField, String type) {
    }
}
