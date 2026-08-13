package com.github.wcqtech.jakit.enumdict.convert;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a String field as a dictionary field whose value is replaced by the
 * dictionary value (label) during conversion.
 *
 * When {@link #keyField()} is blank, the annotated field itself is the source
 * of the dictionary key and the label overwrites it in place. When
 * {@link #keyField()} names another field in the same object, that field is
 * read as the key and the label is written to the annotated field, leaving the
 * source untouched.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DictField {

    /**
     * Dictionary type to look up, matching a type registered in the registry.
     * Blank when the type is derived from {@link #enumType()}.
     */
    String type() default "";

    /**
     * Dictionary‑derived enum class. Enum must implement {@link com.github.wcqtech.jakit.enumdict.EnumDictSource}
     * or be annotated with {@link com.github.wcqtech.jakit.enumdict.EnumDict}; interface takes precedence if both exist.
     * Defaults to {@link Void} (unset). When {@link #type()} and this attribute are both specified,
     * resolved enum type must match {@link #type()}.
     */
    Class<?> enumType() default Void.class;

    /**
     * Name of the field holding the dictionary key within the same object.
     * Defaults to the annotated field itself.
     */
    String keyField() default "";
}
