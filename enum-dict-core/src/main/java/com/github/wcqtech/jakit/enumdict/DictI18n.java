package com.github.wcqtech.jakit.enumdict;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an enum field as the source of the dictionary item's i18n message
 * key. A null or blank field value means the item has no explicit message key.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DictI18n {
}
