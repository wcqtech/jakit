package com.github.wcqtech.jakit.enumdict;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an enum type as a data dictionary source.
 *
 * When {@link #type()} is empty, the enum class simple name is used as the
 * dictionary type.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EnumDict {

    /**
     * Dictionary type; defaults to the annotated enum class simple name.
     */
    String type() default "";
}
