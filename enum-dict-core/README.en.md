# enum-dict

[中文](README.md) | [English](README.en.md)

[![](https://img.shields.io/badge/GitHub-wcqtech/jakit-blue?logo=github)](https://github.com/wcqtech/jakit)
[![](https://jitpack.io/v/wcqtech/jakit.svg)](https://jitpack.io/#wcqtech/jakit)

`enum-dict` is a component that turns Java enums into static data dictionaries.

It is designed to remove the repetitive work and data inconsistency of maintaining data dictionaries. There are no database tables, no CRUD, and no runtime administration. Business code keeps keys and values inside enums.

At application startup, enums in business modules are scanned and registered as in-memory dictionaries. The component provides a Bean and static utility classes for business code; REST APIs are implemented by each business application according to its own conventions.

## Requirements

- Java 17+
- Spring Boot 3.0+

## Installation

Add the dependency through JitPack. Spring Boot projects can include the starter directly:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.wcqtech.jakit</groupId>
        <artifactId>enum-dict-spring-boot-starter</artifactId>
        <version>${version}</version>
    </dependency>
</dependencies>
```

The version should match an actually released Git tag.

If only the registry is needed, include `enum-dict-core`:

```xml
<dependency>
    <groupId>com.github.wcqtech.jakit</groupId>
    <artifactId>enum-dict-core</artifactId>
    <version>${version}</version>
</dependency>
```

## Quick Start

### Declare a Dictionary

Option 1: implement `EnumDictSource`. The interface methods are the single source of truth, and `getDictType()` defaults to the simple name of the enum class.

```java
import com.github.wcqtech.jakit.enumdict.EnumDictSource;

public enum OrderStatus implements EnumDictSource {

    PENDING(0, "Pending"),
    PAID(1, "Paid");

    private final int code;
    private final String label;

    OrderStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public Object getDictKey() {
        return code;
    }

    @Override
    public Object getDictValue() {
        return label;
    }
}
```

Option 2: use annotations. `@EnumDict` marks the enum class, while `@DictKey` and `@DictValue` mark the key field and the display text field respectively.

```java
import com.github.wcqtech.jakit.enumdict.DictKey;
import com.github.wcqtech.jakit.enumdict.DictValue;
import com.github.wcqtech.jakit.enumdict.EnumDict;

@EnumDict(type = "pay_channel")
public enum PayChannel {

    WECHAT(1, "WeChat"),
    ALIPAY(2, "Alipay");

    @DictKey
    private final int code;

    @DictValue
    private final String label;

    PayChannel(int code, String label) {
        this.code = code;
        this.label = label;
    }
}
```

### Automatic Scanning

By default, the starter resolves packages through `AutoConfigurationPackages`, i.e. the package containing `@SpringBootApplication` and its subpackages. No additional configuration is required.

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Query

Inject `EnumDictService`:

```java
import com.github.wcqtech.jakit.enumdict.service.EnumDictService;
import org.springframework.stereotype.Service;

@Service
public class OrderFacade {

    private final EnumDictService enumDictService;

    public OrderFacade(EnumDictService enumDictService) {
        this.enumDictService = enumDictService;
    }

    public String orderStatusName(int status) {
        return enumDictService.valueByKey("OrderStatus", String.valueOf(status))
                .orElse("Unknown status");
    }
}
```

Static methods on `EnumDictUtils` are also available. They are installed by the auto-configuration after scanning, so they can only be called after the Spring context has finished initialization; prefer injecting the Bean in tests or when mocking is required.

```java
import com.github.wcqtech.jakit.enumdict.service.EnumDictUtils;

String label = EnumDictUtils.getValueByKey("pay_channel", "1").orElse("Unknown channel");
```

`EnumDictUtils` also provides full-query static methods with the same names as `EnumDictService`:

```java
import com.github.wcqtech.jakit.enumdict.DictItem;
import com.github.wcqtech.jakit.enumdict.service.EnumDictUtils;

import java.util.List;
import java.util.Map;

Map<String, List<DictItem>> itemsByType = EnumDictUtils.itemsByType();
List<DictItem> allItems = EnumDictUtils.allItems();
```

### REST API

```java
import com.github.wcqtech.jakit.enumdict.service.EnumDictService;
import com.github.wcqtech.jakit.enumdict.service.EnumDictUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dict")
public class DictController {

    private final EnumDictService enumDictService;

    public DictController(EnumDictService enumDictService) {
        this.enumDictService = enumDictService;
    }

    @GetMapping("/bean")
    public Object bean() {
        return enumDictService.itemsByType();
    }

    @GetMapping("/utils")
    public Object utils() {
        return EnumDictUtils.allItems();
    }
}
```

## Configuration

```yaml
jakit:
  enum-dict:
    enabled: true
    base-packages:
      - com.example.biz.dict
      - com.example.order.**.dict
```

| Property | Default | Description |
| --- | --- | --- |
| `jakit.enum-dict.enabled` | `true` | Enables or disables the auto-configuration. |
| `jakit.enum-dict.base-packages` | empty | Packages to scan. Supports multiple values, comma-separated lists, and Ant wildcards. When configured, the default `AutoConfigurationPackages` is not used. |
| `jakit.enum-dict.convert.missing-policy` | `IGNORE` | Policy applied when a dictionary key is missing: `IGNORE` keeps the original value, `FAIL` throws. |

## Query API

`EnumDictService` provides read-only query methods:

| Method | Description |
| --- | --- |
| `items(type)` | Returns all dictionary items of the type in declaration order. |
| `itemByKey(type, key)` | Returns the dictionary item for the key. |
| `itemsByValue(type, value)` | Returns all dictionary items whose value matches. |
| `itemByValue(type, value)` | Returns the first dictionary item whose value matches. |
| `valueByKey(type, key)` | Returns the display text for the key. |
| `keysByValue(type, value)` | Returns all keys whose value matches. |
| `keyByValue(type, value)` | Returns the first key whose value matches. |
| `itemMap(type)` | Returns an immutable map keyed by dictionary key. |
| `types()` | Returns all registered types. |
| `itemsByType()` | Returns all dictionary items grouped by type; declaration order is preserved within each group, but map order is not guaranteed. |
| `allItems()` | Returns a flat list of all dictionary items; declaration order is preserved within each group, but cross-type order is not guaranteed. |
| `contains(type, key)` | Checks whether a dictionary item exists. |

Rules:

- Keys are normalized to `String`; for example, `Integer 1` and `String "1"` are the same key.
- Values are not guaranteed to be unique. `itemByValue` and `keyByValue` return the first match in declaration order; `itemsByValue` and `keysByValue` return all matches.
- Dictionary item order is the enum declaration order.

## Dictionary Value Conversion

Maps dictionary keys on objects to display text. Annotate a `String` field with `@DictField`, then call `convert` on `EnumDictService` or `EnumDictUtils`. Collection conversion and an additional consumer for each target object are also supported.

```java
import com.github.wcqtech.jakit.enumdict.convert.DictField;
import com.github.wcqtech.jakit.enumdict.service.EnumDictUtils;

public class OrderVO {

    @DictField(type = "order_status")
    private String status; // after convert: "1" -> "Paid"

    @DictField(type = "pay_channel", keyField = "channel")
    private String channelName; // filled from channel; channel stays unchanged

    private String channel;
}
```

```java
EnumDictUtils.convert(orderVO);
EnumDictUtils.convert(orders); // collection conversion
EnumDictUtils.convert(orders, order -> {
    count.incrementAndGet(); // extra consumer for each target object
});
```

Rules:

- The annotated field must be a `String`. When `keyField` is blank, the annotated field itself is the key source and is overwritten in place; explicitly naming the annotated field has the same behavior.
- When `keyField` names a sibling field, the value of that field is read and the display text is written to the annotated field.
- Nested convertible beans, `Collection`, `Map`, and object arrays are processed recursively by runtime type; raw and wildcard generic fields are also supported, while primitive arrays are skipped.
- When a key is missing, the original value is kept by default; switch globally with `jakit.enum-dict.convert.missing-policy: FAIL`, or locally with `EnumDictConverter(registry, MissingPolicy.FAIL)`.
- Records, final fields, and JDK value types are never written.
- If a Map key is a bean that gets converted, business code must keep the key's `hashCode`/`equals` independent of converted fields. The SDK does not rebuild the Map buckets after conversion; rebuild them yourself when needed.

## Standalone Core Usage

Without Spring, use `EnumDictRegistry` and `EnumDictConverter` directly:

```java
import com.github.wcqtech.jakit.enumdict.DictItem;
import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;

import java.util.List;
import java.util.Map;

EnumDictRegistry registry = new EnumDictRegistry();
registry.register("OrderStatus", List.of(
        new DictItem("OrderStatus", "0", "Pending"),
        new DictItem("OrderStatus", "1", "Paid")
));

Map<String, List<DictItem>> grouped = registry.itemsByType();
List<DictItem> flat = registry.allItems();
```
The registry is thread-safe and query results are returned as immutable copies.
```java
public class OrderVO {
    @DictField(type = "OrderStatus")
    String status;
}

EnumDictConverter converter = new EnumDictConverter(registry);
converter.convert(orderVO); // status: "1" -> "Paid"
```
Conversion is performed by the `EnumDictConverter`.

## Validation Rules

The following cases fail fast at startup with explicit error messages:

- `@EnumDict` is placed on a non-enum class.
- An annotation-based enum is missing `@DictKey` or `@DictValue`, or has the same annotation more than once.
- Constants implementing `EnumDictSource` return inconsistent `getDictType()` values.
- The type is `null` or blank (interface mode).
- A key is `null` or blank.
- A duplicate key appears within the same type.
- The same type is re-registered with different content.
- A key or value is `null`.

## Features

- Startup scanning: scans the package containing `@SpringBootApplication` by default; multiple packages and Ant wildcards can be configured.
- Two declaration modes: implement `EnumDictSource`, or use `@EnumDict`, `@DictKey`, and `@DictValue`.
- Read-only queries: provides the `EnumDictService` Bean and the `EnumDictUtils` static utility class.
- Strict validation: type, key, annotation completeness, and other problems fail fast at startup.
- Low dependency: `enum-dict-core` has zero Spring dependencies and can be used in plain Java projects.

## Modules

| Module | Responsibility | Dependencies |
| --- | --- | --- |
| `enum-dict-core` | Annotations, `EnumDictSource`, `DictItem`, `EnumDictRegistry`, `EnumDictConverter` | none |
| `enum-dict-spring-boot-starter` | Package scanning, auto-configuration, `EnumDictService` | `enum-dict-core` + Spring Boot |
