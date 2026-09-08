package com.github.wcqtech.jakit.utils.mock;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Package-private engine that mocks one root value per instance, walking the
 * field-filling pipeline documented in the design:
 *
 * <ol>
 * <li>{@code @MockIgnore} skips the field;</li>
 * <li>{@code @MockWith} uses the annotated mocker (assignability checked);</li>
 * <li>registered default rules from {@link Mockers} apply;</li>
 * <li>enum slots yield their first constant;</li>
 * <li>recursive constructions already on the current type path return null
 *     (cycle guard);</li>
 * <li>collections, maps and arrays are filled with
 *     {@link MockUtils#DEFAULT_ELEMENT_COUNT} elements;</li>
 * <li>unregistered JDK types fail with a descriptive error instead of being
 *     introspected;</li>
 * <li>other user-defined classes are constructed and filled recursively.</li>
 * </ol>
 *
 * <p>An engine is used for a single root mock; it holds no shared mutable
 * state, so {@code MockUtils} is safe for concurrent use.
 */
final class MockEngine {

    private static final String[] JDK_PACKAGE_PREFIXES = { "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "org.w3c.dom.", "org.w3c.dom", "org.xml.", "org.ietf.jgss." };

    private static final ConcurrentHashMap<Class<?>, Mocker<?>> MOCKER_CACHE = new ConcurrentHashMap<>();

    private final Class<?> rootType;
    private final List<Class<?>> path = new ArrayList<>();

    private MockEngine(Class<?> rootType) {
        this.rootType = rootType;
    }

    /** Mocks a root slot; position >= 0 denotes a stream element slot. */
    static Object mockRoot(Class<?> type, int position) {
        Objects.requireNonNull(type, "type must not be null");
        MockEngine engine = new MockEngine(type);
        ResolvedType resolved = ResolvedType.of(type);
        String path = "";
        MockContext context = new MockContext(type, path, null, position);
        Object value = engine.slotValue(resolved, context, type.getName());
        requireAssignable(type, value, type.getName(), "root");
        return value;
    }

    /** Mocks the fields of a freshly created instance of the given class. */
    private static Object newInstance(Class<?> type, String label) {
        if (type.isInterface()) {
            throw new IllegalArgumentException(
                    "cannot mock " + label + ": " + type.getName() + " is an interface; register a Mocker via "
                            + "Mockers.register or annotate the field with @MockWith/@MockIgnore");
        }
        if (Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException(
                    "cannot mock " + label + ": " + type.getName() + " is abstract; register a Mocker via "
                            + "Mockers.register or annotate the field with @MockWith/@MockIgnore");
        }
        if (type.isRecord()) {
            throw new IllegalArgumentException("cannot mock " + label + ": " + type.getName()
                    + " is a record; records are not supported, register a Mocker via Mockers.register or annotate "
                    + "the field with @MockWith/@MockIgnore");
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException("cannot mock " + label + ": " + type.getName()
                    + " has no accessible no-arg constructor; register a Mocker via Mockers.register or annotate the "
                    + "field with @MockWith/@MockIgnore", exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException(
                    "cannot mock " + label + ": failed to instantiate " + type.getName() + " (" + exception.getCause()
                            + "); register a Mocker via Mockers.register or annotate the field with "
                            + "@MockWith/@MockIgnore",
                    exception);
        }
    }

    private Object slotValue(ResolvedType type, MockContext context, String label) {
        Class<?> raw = type.raw;
        Mocker<?> mocker = Mockers.find(raw);
        if (mocker != null) {
            Object value = mocker.mock(context);
            requireAssignable(raw, value, label, "mocker");
            return value;
        }
        if (raw.isEnum()) {
            Object[] constants = raw.getEnumConstants();
            if (constants.length == 0) {
                throw new IllegalArgumentException("cannot mock " + label + ": enum " + raw.getName()
                        + " declares no constants");
            }
            return constants[0];
        }
        if (raw.isArray()) {
            if (onPath(raw.getComponentType())) {
                return null;
            }
            return arrayValue(raw, context, label);
        }
        if (Map.class.isAssignableFrom(raw)) {
            if (contentOnPath(type)) {
                return null;
            }
            return mapValue(type, context, label);
        }
        if (Collection.class.isAssignableFrom(raw)) {
            if (contentOnPath(type)) {
                return null;
            }
            return collectionValue(type, context, label);
        }
        if (isJdkType(raw)) {
            throw new IllegalArgumentException("cannot mock " + label + ": " + raw.getName()
                    + " is a JDK type without a registered Mocker; register one via Mockers.register or annotate the "
                    + "field with @MockWith/@MockIgnore");
        }
        if (path.contains(raw)) {
            return null;
        }
        Object instance = newInstance(raw, label);
        path.add(raw);
        try {
            fillFields(instance, raw, context);
        } finally {
            path.remove(path.size() - 1);
        }
        return instance;
    }

    private Object arrayValue(Class<?> raw, MockContext context, String label) {
        Class<?> component = raw.getComponentType();
        int length = MockUtils.DEFAULT_ELEMENT_COUNT;
        Object array = Array.newInstance(component, length);
        for (int i = 0; i < length; i++) {
            Object element = slotValue(ResolvedType.of(component), elementContext(context, i), elementLabel(context, i));
            try {
                Array.set(array, i, element);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("cannot mock " + label + ": array element " + i + " of type "
                        + component.getName() + " is not compatible with the produced value (" + describe(element)
                        + ")", exception);
            }
        }
        return array;
    }

    private Object collectionValue(ResolvedType type, MockContext context, String label) {
        Collection<Object> collection = instantiateCollection(type.raw, label);
        if (type.args.isEmpty()) {
            throw new IllegalArgumentException("cannot mock " + label + ": collection field of type " + type.raw
                    .getName() + " is declared without a generic element type");
        }
        int length = MockUtils.DEFAULT_ELEMENT_COUNT;
        for (int i = 0; i < length; i++) {
            collection.add(slotValue(type.args.get(0), elementContext(context, i), elementLabel(context, i)));
        }
        return collection;
    }

    private Object mapValue(ResolvedType type, MockContext context, String label) {
        if (type.args.size() != 2) {
            throw new IllegalArgumentException("cannot mock " + label + ": map field of type " + type.raw.getName()
                    + " is not declared with exactly one key and one value generic argument");
        }
        Map<Object, Object> map = instantiateMap(type.raw, label);
        int length = MockUtils.DEFAULT_ELEMENT_COUNT;
        for (int i = 0; i < length; i++) {
            ResolvedType keyType = type.args.get(0);
            ResolvedType valueType = type.args.get(1);
            Object key = slotValue(keyType, keyContext(context, i), keyLabel(context, i));
            Object value = slotValue(valueType, valueContext(context, i), valueLabel(context, i));
            map.put(key, value);
        }
        return map;
    }

    private static Collection<Object> instantiateCollection(Class<?> raw, String label) {
        if (raw.isInterface()) {
            if (Set.class.isAssignableFrom(raw)) {
                return new LinkedHashSet<>();
            }
            if (Deque.class.isAssignableFrom(raw) || Queue.class.isAssignableFrom(raw)) {
                return new java.util.ArrayDeque<>();
            }
            return new ArrayList<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Collection<Object> collection = (Collection<Object>) newInstance(raw, label);
            return collection;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "cannot mock " + label + ": collection type " + raw.getName() + " cannot be instantiated", exception);
        }
    }

    private static Map<Object, Object> instantiateMap(Class<?> raw, String label) {
        if (raw.isInterface()) {
            return new LinkedHashMap<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) newInstance(raw, label);
            return map;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "cannot mock " + label + ": map type " + raw.getName() + " cannot be instantiated", exception);
        }
    }

    private void fillFields(Object instance, Class<?> declaredClass, MockContext parentContext) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> type = declaredClass; type != null && type != Object.class; type = type.getSuperclass()) {
            hierarchy.add(0, type);
        }
        for (Class<?> type : hierarchy) {
            for (Field field : type.getDeclaredFields()) {
                fillField(instance, field, parentContext);
            }
        }
    }

    private void fillField(Object instance, Field field, MockContext parentContext) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || field.isSynthetic() || Modifier.isTransient(modifiers)) {
            return;
        }
        String path = childPath(parentBase(parentContext), field.getName());
        if (field.isAnnotationPresent(MockIgnore.class)) {
            return;
        }
        if (Modifier.isFinal(modifiers)) {
            throw new IllegalArgumentException("cannot mock field '" + path
                    + "': final instance fields are not supported; annotate the field with @MockIgnore");
        }
        try {
            field.setAccessible(true);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "cannot mock field '" + path + "': field is not accessible (" + exception + ")", exception);
        }
        MockContext context = new MockContext(rootType, path, field.getName(), -1);
        Object value;
        MockWith with = field.getAnnotation(MockWith.class);
        if (with != null) {
            Mocker<?> mocker = mockerFor(with.value());
            value = mocker.mock(context);
        } else {
            value = slotValue(ResolvedType.of(field.getGenericType()), context, fieldLabel(path, field.getType()));
        }
        requireAssignable(field.getType(), value, path, "field");
        try {
            field.set(instance, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("cannot mock field '" + path + "': " + exception, exception);
        }
    }

    private static Mocker<?> mockerFor(Class<? extends Mocker<?>> mockerType) {
        return MOCKER_CACHE.computeIfAbsent(mockerType, MockEngine::instantiateMocker);
    }

    private static Mocker<?> instantiateMocker(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (Mocker<?>) constructor.newInstance();
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException("cannot instantiate mocker " + type.getName()
                    + ": it has no no-arg constructor", exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException(
                    "cannot instantiate mocker " + type.getName() + ": " + exception.getCause(), exception);
        }
    }

    private static void requireAssignable(Class<?> raw, Object value, String label, String origin) {
        if (value == null) {
            return;
        }
        Class<?> target = raw.isPrimitive() ? boxed(raw) : raw;
        if (!target.isInstance(value)) {
            throw new IllegalArgumentException("cannot mock " + label + ": the value produced by the " + origin + " ("
                    + describe(value) + ") is not assignable to type " + raw.getTypeName());
        }
    }

    private static Class<?> boxed(Class<?> primitive) {
        if (primitive == boolean.class) {
            return Boolean.class;
        }
        if (primitive == byte.class) {
            return Byte.class;
        }
        if (primitive == char.class) {
            return Character.class;
        }
        if (primitive == short.class) {
            return Short.class;
        }
        if (primitive == int.class) {
            return Integer.class;
        }
        if (primitive == long.class) {
            return Long.class;
        }
        if (primitive == float.class) {
            return Float.class;
        }
        if (primitive == double.class) {
            return Double.class;
        }
        return primitive;
    }

    private boolean contentOnPath(ResolvedType container) {
        for (ResolvedType argument : container.args) {
            if (onPath(argument.raw)) {
                return true;
            }
        }
        return false;
    }

    private boolean onPath(Class<?> type) {
        if (type.isPrimitive() || type.isEnum()) {
            return false;
        }
        return path.contains(type);
    }

    private MockContext elementContext(MockContext parent, int index) {
        return new MockContext(rootType, parentBase(parent) + "[" + index + "]", null, index);
    }

    private MockContext keyContext(MockContext parent, int index) {
        return new MockContext(rootType, parentBase(parent) + "[" + index + "].key", null, index);
    }

    private MockContext valueContext(MockContext parent, int index) {
        return new MockContext(rootType, parentBase(parent) + "[" + index + "].value", null, index);
    }

    private static String childPath(String parent, String fieldName) {
        return parent.isEmpty() ? fieldName : parent + "." + fieldName;
    }

    /**
     * Path base of a parent slot: stream elements and container elements keep
     * their index so descendant fields of distinct elements get distinct seeds.
     */
    private static String parentBase(MockContext parentContext) {
        if (parentContext.getPosition() >= 0 && parentContext.getPath().isEmpty()) {
            return "[" + parentContext.getPosition() + "]";
        }
        return parentContext.getPath();
    }

    private static String elementLabel(MockContext context, int index) {
        return "element " + index + " of '" + context.getPath() + "'";
    }

    private static String keyLabel(MockContext context, int index) {
        return "key " + index + " of map field '" + context.getPath() + "'";
    }

    private static String valueLabel(MockContext context, int index) {
        return "value " + index + " of map field '" + context.getPath() + "'";
    }

    private static String fieldLabel(String path, Class<?> type) {
        return "field '" + path + "' of type " + type.getName();
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static boolean isJdkType(Class<?> type) {
        Package typePackage = type.getPackage();
        if (typePackage == null) {
            return false;
        }
        String name = typePackage.getName();
        if (name.equals("java") || name.equals("javax")) {
            return true;
        }
        for (String prefix : JDK_PACKAGE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Raw type plus its resolved generic arguments, built once per slot so
     * nested containers keep their element types.
     */
    private static final class ResolvedType {

        private final Class<?> raw;
        private final List<ResolvedType> args;

        private ResolvedType(Class<?> raw, List<ResolvedType> args) {
            this.raw = raw;
            this.args = args;
        }

        static ResolvedType of(Class<?> type) {
            return new ResolvedType(type, List.of());
        }

        static ResolvedType of(Type type) {
            return of(type, new java.util.HashSet<>());
        }

        private static ResolvedType of(Type type, Set<Type> resolving) {
            if (type instanceof Class<?> clazz) {
                return new ResolvedType(clazz, List.of());
            }
            if (type instanceof ParameterizedType parameterized) {
                if (!(parameterized.getRawType() instanceof Class<?> raw)) {
                    throw unresolvable(type);
                }
                List<ResolvedType> arguments = new ArrayList<>();
                for (Type argument : parameterized.getActualTypeArguments()) {
                    arguments.add(of(argument, resolving));
                }
                return new ResolvedType(raw, List.copyOf(arguments));
            }
            if (type instanceof GenericArrayType genericArray) {
                Class<?> component = arrayComponentClass(genericArray.getGenericComponentType(), resolving);
                return new ResolvedType(Array.newInstance(component, 0).getClass(), List.of());
            }
            if (type instanceof WildcardType wildcard) {
                Type[] upper = wildcard.getUpperBounds();
                if (upper.length == 1 && upper[0] != Object.class) {
                    return of(upper[0], resolving);
                }
                Type[] lower = wildcard.getLowerBounds();
                if (lower.length == 1) {
                    return of(lower[0], resolving);
                }
                throw unresolvable(type);
            }
            throw unresolvable(type);
        }

        private static Class<?> arrayComponentClass(Type component, Set<Type> resolving) {
            if (!resolving.add(component)) {
                throw unresolvable(component);
            }
            try {
                if (component instanceof Class<?> clazz) {
                    return clazz;
                }
                if (component instanceof ParameterizedType parameterized) {
                    if (!(parameterized.getRawType() instanceof Class<?> raw)) {
                        throw unresolvable(component);
                    }
                    return raw;
                }
                if (component instanceof GenericArrayType genericArray) {
                    Class<?> inner = arrayComponentClass(genericArray.getGenericComponentType(), resolving);
                    return Array.newInstance(inner, 0).getClass();
                }
                throw unresolvable(component);
            } finally {
                resolving.remove(component);
            }
        }

        private static IllegalArgumentException unresolvable(Type type) {
            return new IllegalArgumentException(
                    "cannot resolve generic type " + type.getTypeName() + "; declare concrete named type arguments "
                            + "(raw collections, unbounded wildcards and type variables are not supported)");
        }
    }
}
