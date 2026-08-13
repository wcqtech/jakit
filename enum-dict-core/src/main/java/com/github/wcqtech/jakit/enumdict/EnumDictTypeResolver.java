package com.github.wcqtech.jakit.enumdict;

import java.util.Objects;

/**
 * Resolves the dictionary type string declared by an enum class.
 *
 * An enum is a valid dictionary type source when it implements
 * {@link EnumDictSource} or is annotated with {@link EnumDict}. For
 * interface-based enums, the interface is the single source of truth, even
 * when {@link EnumDict} is also present, and every constant must return the
 * same non-blank type. For annotation-based enums, the declared type is used,
 * falling back to the enum class simple name.
 */
public final class EnumDictTypeResolver {

    private EnumDictTypeResolver() {
    }

    /**
     * Resolves the dictionary type string for the given enum class.
     *
     * @param enumType the enum class to resolve
     * @return the dictionary type string
     * @throws EnumDictException when the class is not an enum, has no
     *         constants, returns an inconsistent or blank type, or neither
     *         implements {@link EnumDictSource} nor is annotated with
     *         {@link EnumDict}
     */
    public static String resolve(Class<?> enumType) {
        Objects.requireNonNull(enumType, "enumType must not be null");
        if (!enumType.isEnum()) {
            throw new EnumDictException("Only enum types can be used as a dictionary type source, but "
                    + enumType.getName() + " is not an enum");
        }
        if (enumType.getEnumConstants().length == 0) {
            throw new EnumDictException("Enum " + enumType.getName()
                    + " cannot be used as a dictionary type source: it has no constants");
        }
        if (EnumDictSource.class.isAssignableFrom(enumType)) {
            return resolveFromInterface(enumType);
        }
        EnumDict annotation = enumType.getAnnotation(EnumDict.class);
        if (annotation == null) {
            throw new EnumDictException("Enum " + enumType.getName()
                    + " cannot be used as a dictionary type source: it neither implements "
                    + EnumDictSource.class.getSimpleName() + " nor is annotated with @"
                    + EnumDict.class.getSimpleName());
        }
        String type = annotation.type();
        return type.isBlank() ? enumType.getSimpleName() : type;
    }

    private static String resolveFromInterface(Class<?> enumType) {
        Enum<?>[] constants = (Enum<?>[]) enumType.getEnumConstants();
        String type = null;
        for (Enum<?> constant : constants) {
            EnumDictSource source = (EnumDictSource) constant;
            String constantType = source.getDictType();
            if (constantType == null || constantType.isBlank()) {
                throw new EnumDictException("Blank dictionary type from "
                        + enumType.getName() + "." + constant.name());
            }
            if (type == null) {
                type = constantType;
            } else if (!type.equals(constantType)) {
                throw new EnumDictException("Inconsistent dictionary type in " + enumType.getName()
                        + ": " + type + " vs " + constantType);
            }
        }
        return type;
    }
}
