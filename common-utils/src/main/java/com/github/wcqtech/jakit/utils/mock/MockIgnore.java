package com.github.wcqtech.jakit.utils.mock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a field to exclude it from mocking: the field is left untouched,
 * so reference fields stay null and primitive fields keep their JVM default
 * value ({@code 0}/{@code false}) on the freshly created instance.
 *
 * <p>Also the only supported way to keep a {@code final} instance field: final
 * fields cannot be assigned reflectively in a reliable way, so without this
 * annotation mocking a class that declares one fails.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MockIgnore {
}
