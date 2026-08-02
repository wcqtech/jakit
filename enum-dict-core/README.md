# enum-dict

[中文](README.md) | [English](README.en.md)

enum-dict 是一个让 Java 枚举成为静态数据字典的组件。

旨在解决维护数据字典的重复工作和数据不一致性问题。

不建表、不写CRUD、不动态维护。业务代码在枚举里维护 key 和 value。

应用程序启动时扫描业务模块中的枚举，将字典注册到内存。

提供 Bean 和静态工具类供业务代码使用，REST API 由业务方按照自主规范实现。

## 环境要求

- Java 17+
- Spring Boot 3.0+


## 引入

通过 JitPack 引入。Spring Boot 项目直接引入 starter：

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

版本号以实际发布的 Git tag 为准。

仅需要注册表时，可以只引入 core：

```xml
<dependency>
    <groupId>com.github.wcqtech.jakit</groupId>
    <artifactId>enum-dict-core</artifactId>
    <version>${version}</version>
</dependency>
```

## 快速开始

### 声明字典

方式一：枚举实现 `EnumDictSource`。接口方法返回值为唯一事实来源，`getDictType()` 缺省使用枚举类简单名。

```java
import com.github.wcqtech.jakit.enumdict.EnumDictSource;

public enum OrderStatus implements EnumDictSource {

    PENDING(0, "待支付"),
    PAID(1, "已支付");

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

方式二：枚举使用注解。`@EnumDict` 标注枚举类，`@DictKey` 和 `@DictValue` 分别标记 key 与展示文本字段。

```java
import com.github.wcqtech.jakit.enumdict.DictKey;
import com.github.wcqtech.jakit.enumdict.DictValue;
import com.github.wcqtech.jakit.enumdict.EnumDict;

@EnumDict(type = "pay_channel")
public enum PayChannel {

    WECHAT(1, "微信"),
    ALIPAY(2, "支付宝");

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

### 自动扫描

starter 默认通过 `AutoConfigurationPackages` 获取包路径，也就是 `@SpringBootApplication` 所在包及其子包，无需额外配置。

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

### 查询

注入 `EnumDictService`：

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
                .orElse("未知状态");
    }
}
```

也可以使用 `EnumDictUtils` 静态方法。它由自动装配在扫描完成后安装，只能在 Spring 上下文初始化完成后调用；测试或需要 mock 的场景优先注入 Bean。

```java
import com.github.wcqtech.jakit.enumdict.service.EnumDictUtils;

String label = EnumDictUtils.getValueByKey("pay_channel", "1").orElse("未知渠道");
```

`EnumDictUtils` 还提供与 `EnumDictService` 同名的全量查询静态方法：

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
## 配置

```yaml
jakit:
  enum-dict:
    enabled: true
    base-packages:
      - com.example.biz.dict
      - com.example.order.**.dict
```

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `jakit.enum-dict.enabled` | `true` | 是否启用自动装配 |
| `jakit.enum-dict.base-packages` | 空 | 要扫描的包路径，支持多个值、逗号分隔和 Ant 通配符；配置后不再使用默认的 `AutoConfigurationPackages` |

## 查询 API

`EnumDictService` 提供只读查询方法：

| 方法 | 说明 |
| --- | --- |
| `items(type)` | 按声明顺序返回该 type 的全部字典项 |
| `itemByKey(type, key)` | 按 key 查询字典项 |
| `itemsByValue(type, value)` | 按展示文本查询全部字典项 |
| `itemByValue(type, value)` | 按展示文本查询第一个字典项 |
| `valueByKey(type, key)` | 按 key 查询展示文本 |
| `keysByValue(type, value)` | 按展示文本查询全部 key |
| `keyByValue(type, value)` | 按展示文本查询第一个 key |
| `itemMap(type)` | 返回以 key 为键的不可变 Map |
| `types()` | 返回全部已注册 type |
| `itemsByType()` | 按 type 分组返回全部字典项，组内保持声明顺序，Map 顺序不保证 |
| `allItems()` | 返回全部字典项的扁平列表，组内保持声明顺序，跨 type 顺序不保证 |
| `contains(type, key)` | 判断字典项是否存在 |

规则说明：

- key 统一归一化为 String，例如 Integer `1` 与 String `"1"` 是同一个 key。
- value 不保证唯一；`itemByValue`、`keyByValue` 返回声明顺序中的首个匹配，`itemsByValue`、`keysByValue` 返回全部匹配。
- 字典项顺序即枚举声明顺序。

## 独立使用 core

不启动 Spring 时，可以直接使用 `EnumDictRegistry`：

```java
import com.github.wcqtech.jakit.enumdict.DictItem;
import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;

import java.util.List;
import java.util.Map;

EnumDictRegistry registry = new EnumDictRegistry();
registry.register("OrderStatus", List.of(
        new DictItem("OrderStatus", "0", "待支付"),
        new DictItem("OrderStatus", "1", "已支付")
));

Map<String, List<DictItem>> grouped = registry.itemsByType();
List<DictItem> flat = registry.allItems();
```

注册表是线程安全的，查询结果以不可变副本返回。

## 校验规则

以下情况会在启动阶段直接失败并给出明确错误信息：

- `@EnumDict` 标注在非枚举类上。
- 注解方式枚举缺少 `@DictKey` 或 `@DictValue`，或重复标记同一注解。
- 实现 `EnumDictSource` 时各常量的 `getDictType()` 不一致。
- type 为 null 或空白（接口方式）。
- key 为 null 或空白。
- 同一 type 内出现重复 key。
- 同一 type 被不同内容重复注册。
- key 或 value 为 null。


## 特性

- 启动扫描：默认扫描 `@SpringBootApplication` 所在包，可通过配置指定多个包路径，支持 Ant 通配符。
- 两种声明方式：实现 `EnumDictSource`，或使用 `@EnumDict`、`@DictKey`、`@DictValue` 注解。
- 只读查询：提供 `EnumDictService` Bean 和 `EnumDictUtils` 静态工具类。
- 严格校验：类型、key、注解完整性等问题在启动阶段 fail-fast。
- 低依赖：`enum-dict-core` 零 Spring 依赖，可独立用于普通 Java 项目。

## 模块

| 模块 | 职责 | 依赖 |
| --- | --- | --- |
| `enum-dict-core` | 注解、`EnumDictSource`、`DictItem`、`EnumDictRegistry` | 无 |
| `enum-dict-spring-boot-starter` | 包扫描、自动装配、`EnumDictService` | `enum-dict-core` + Spring Boot |
