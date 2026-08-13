package com.github.wcqtech.jakit.enumdict.scanner;

import com.github.wcqtech.jakit.enumdict.DictItem;
import com.github.wcqtech.jakit.enumdict.DictI18n;
import com.github.wcqtech.jakit.enumdict.DictKey;
import com.github.wcqtech.jakit.enumdict.DictValue;
import com.github.wcqtech.jakit.enumdict.EnumDict;
import com.github.wcqtech.jakit.enumdict.EnumDictException;
import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;
import com.github.wcqtech.jakit.enumdict.EnumDictSource;
import com.github.wcqtech.jakit.enumdict.EnumDictTypeResolver;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Scans classpath packages for enum dictionaries and registers them.
 *
 * An enum is a dictionary candidate when it is annotated with {@link EnumDict}
 * or implements {@link EnumDictSource}. For enums implementing {@link EnumDictSource}, the
 * interface methods are the single source of truth, even when {@link EnumDict} is
 * also present. For annotation-based enums, {@link DictKey} and {@link DictValue}
 * fields are required.
 */
public final class EnumDictScanner {

    private final EnumDictRegistry registry;
    private final ResourceLoader resourceLoader;

    public EnumDictScanner(EnumDictRegistry registry, ResourceLoader resourceLoader) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
    }

    public void scan(String... basePackages) {
        if (basePackages == null) {
            return;
        }
        for (String basePackage : basePackages) {
            if (basePackage == null || basePackage.isBlank()) {
                continue;
            }
            scanPackage(basePackage.trim());
        }
    }

    private void scanPackage(String basePackage) {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.setResourceLoader(resourceLoader);
        provider.addIncludeFilter(new AnnotationTypeFilter(EnumDict.class));
        provider.addIncludeFilter(new AssignableTypeFilter(EnumDictSource.class));
        for (BeanDefinition candidate : provider.findCandidateComponents(basePackage)) {
            String className = candidate.getBeanClassName();
            if (className == null) {
                throw new IllegalStateException("Dictionary candidate without class name in package " + basePackage);
            }
            Class<?> type = ClassUtils.resolveClassName(className, resourceLoader.getClassLoader());
            if (!type.isEnum()) {
                throw new IllegalStateException("Only enum types can be dictionaries, but " + type.getName()
                        + " is not an enum");
            }
            registerEnum(type);
        }
    }

    private void registerEnum(Class<?> enumType) {
        Enum<?>[] constants = (Enum<?>[]) enumType.getEnumConstants();
        List<DictItem> items = new ArrayList<>(constants.length);
        String type;
        if (EnumDictSource.class.isAssignableFrom(enumType)) {
            type = collectFromInterface(enumType, constants, items);
        } else {
            type = collectFromAnnotations(enumType, constants, items);
        }
        try {
            registry.register(type, items);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to register dictionary from enum " + enumType.getName(), e);
        }
    }

    private static String collectFromInterface(Class<?> enumType, Enum<?>[] constants, List<DictItem> items) {
        String type = resolveType(enumType);
        for (Enum<?> constant : constants) {
            EnumDictSource source = (EnumDictSource) constant;
            Object key = source.getDictKey();
            if (key == null) {
                throw new IllegalStateException("Null dictionary key in " + enumType.getName() + "." + constant.name());
            }
            Object value = source.getDictValue();
            if (value == null) {
                throw new IllegalStateException("Null dictionary value in " + enumType.getName() + "." + constant.name());
            }
            items.add(new DictItem(type, String.valueOf(key), String.valueOf(value),
                    source.getDictI18nKey()));
        }
        return type;
    }

    private static String collectFromAnnotations(Class<?> enumType, Enum<?>[] constants, List<DictItem> items) {
        String type = resolveType(enumType);
        Field keyField = findAnnotatedField(enumType, DictKey.class);
        Field valueField = findAnnotatedField(enumType, DictValue.class);
        Field i18nField = findAnnotatedField(enumType, DictI18n.class);
        if (keyField == null) {
            throw new IllegalStateException("Missing @" + DictKey.class.getSimpleName() + " on " + enumType.getName());
        }
        if (valueField == null) {
            throw new IllegalStateException("Missing @" + DictValue.class.getSimpleName() + " on " + enumType.getName());
        }
        keyField.setAccessible(true);
        valueField.setAccessible(true);
        if (i18nField != null) {
            i18nField.setAccessible(true);
        }
        for (Enum<?> constant : constants) {
            Object key = readField(enumType, constant, keyField);
            if (key == null) {
                throw new IllegalStateException("Null dictionary key in " + enumType.getName() + "." + constant.name());
            }
            Object value = readField(enumType, constant, valueField);
            if (value == null) {
                throw new IllegalStateException("Null dictionary value in " + enumType.getName() + "." + constant.name());
            }
            Object i18nKey = i18nField == null ? null : readField(enumType, constant, i18nField);
            items.add(new DictItem(type, String.valueOf(key), String.valueOf(value),
                    i18nKey == null ? null : String.valueOf(i18nKey)));
        }
        return type;
    }

    private static String resolveType(Class<?> enumType) {
        try {
            return EnumDictTypeResolver.resolve(enumType);
        } catch (EnumDictException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static Object readField(Class<?> enumType, Enum<?> constant, Field field) {
        try {
            return field.get(constant);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read field " + field.getName() + " on " + enumType.getName(), e);
        }
    }

    private static Field findAnnotatedField(Class<?> enumType, Class<? extends Annotation> annotationType) {
        Field found = null;
        for (Class<?> current = enumType; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (field.isAnnotationPresent(annotationType)) {
                    if (found != null) {
                        throw new IllegalStateException("Multiple @" + annotationType.getSimpleName()
                                + " fields on " + enumType.getName());
                    }
                    found = field;
                }
            }
        }
        return found;
    }
}
