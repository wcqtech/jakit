package com.github.wcqtech.jakit.enumdict;

/**
 * Contract implemented by enum types that expose dictionary metadata directly.
 *
 * When an enum implements this interface, its methods take precedence over
 * {@link DictKey} and {@link DictValue} field annotations.
 */
public interface EnumDictSource {

    /**
     * Dictionary type of this enum dictionary.
     *
     * Defaults to the enum class simple name, matching the default of
     * {@link EnumDict#type()}. Override it when the type must stay stable
     * across renames or when a shorter business code is preferred.
     */
    default String getDictType() {
        return ((Enum<?>) this).getDeclaringClass().getSimpleName();
    }

    Object getDictKey();

    Object getDictValue();
}
