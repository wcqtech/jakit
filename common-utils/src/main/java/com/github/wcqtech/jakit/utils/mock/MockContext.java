package com.github.wcqtech.jakit.utils.mock;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Immutable description of a single slot to mock.
 *
 * <p>A slot is either the mocked object itself (path {@code ""}), a field of
 * the object being mocked, an element inside a collection/array, a key or
 * value of a map entry, or one element of a {@link MockUtils#multiMock} stream.
 *
 * <p>The {@link #getSeed() seed} is derived from the canonical path and the
 * position through a stable hash (FNV-1a). Equal paths and positions always
 * yield equal seeds, and distinct slots usually yield distinct seeds; built-in
 * mockers derive their values from the seed, which is what makes mocking
 * deterministic while still producing distinct values for distinct fields and
 * for distinct positions inside one container.
 *
 * <p>Instances are created by the mocking engine; applications normally only
 * read them inside a {@link Mocker} implementation or a test.
 */
public final class MockContext {

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final String POSITION_SEPARATOR = "@";

    private final Class<?> rootType;
    private final String path;
    private final String fieldName;
    private final int position;
    private final long seed;

    /**
     * Creates a context for one mock slot.
     *
     * @param rootType the class passed to {@link MockUtils#mock}; must not be
     *                 null
     * @param path canonical path of the slot, e.g. {@code ""} for the root,
     *             {@code "items[1].price"} for a nested field; must not be null
     * @param fieldName name of the nearest enclosing field, or null when the
     *                  slot is the root, a stream element, or a collection
     *                  element
     * @param position zero-based index inside the enclosing collection, array,
     *                 map or stream, or {@code -1} when the slot is not inside
     *                 any container
     */
    public MockContext(Class<?> rootType, String path, String fieldName, int position) {
        this.rootType = Objects.requireNonNull(rootType, "rootType must not be null");
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.fieldName = fieldName;
        if (position < -1) {
            throw new IllegalArgumentException("position must be >= -1, but was " + position);
        }
        this.position = position;
        this.seed = fnv1a(path + POSITION_SEPARATOR + position);
    }

    /**
     * Returns the class that was passed to {@link MockUtils#mock} at the root
     * of the current mock call.
     *
     * @return the root type
     */
    public Class<?> getRootType() {
        return rootType;
    }

    /**
     * Returns the canonical path of this slot, e.g. {@code ""} for the root,
     * {@code "orderNo"} for a direct field, {@code "items[1].price"} for a
     * field nested in a collection element.
     *
     * @return the canonical path
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the name of the nearest enclosing field, or null when this slot
     * is the root object, a stream element, or a container element.
     *
     * @return the nearest field name, or null
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Returns the zero-based index of this slot inside its enclosing
     * collection, array, map or stream, or {@code -1} when the slot is not
     * inside any container.
     *
     * @return the position, or {@code -1}
     */
    public int getPosition() {
        return position;
    }

    /**
     * Returns the deterministic seed of this slot, computed as a stable
     * FNV-1a hash of the canonical path and the position.
     *
     * @return the seed
     */
    public long getSeed() {
        return seed;
    }

    @Override
    public String toString() {
        return "MockContext{rootType=" + rootType.getName() + ", path='" + path + '\'' + ", position=" + position
                + '}';
    }

    private static long fnv1a(String key) {
        long hash = FNV_OFFSET_BASIS;
        for (byte value : key.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (value & 0xff);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
