package com.github.wcqtech.jakit.utils.mock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a field to mock it with a specific {@link Mocker} instead of the
 * type-based default rule.
 *
 * <p>The referenced class must implement {@link Mocker} and expose a no-arg
 * constructor; the mocker is instantiated once and cached. When the produced
 * value is not assignable to the annotated field's type, mocking fails with an
 * {@link IllegalArgumentException} that names the field path and both types.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MockWith {

    /**
     * The mocker class to use for the annotated field.
     *
     * @return a no-arg-constructible {@link Mocker} implementation
     */
    Class<? extends Mocker<?>> value();
}
