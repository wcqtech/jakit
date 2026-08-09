# enum-dict

[中文](README.md) | [English](README.en.md)

[![](https://img.shields.io/badge/GitHub-wcqtech/jakit-blue?logo=github)](https://github.com/wcqtech/jakit)
[![](https://jitpack.io/v/wcqtech/jakit.svg)](https://jitpack.io/#wcqtech/jakit)

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
| `jakit.enum-dict.convert.missing-policy` | `IGNORE` | 字典 key 未命中时的默认处理策略：`IGNORE` 保留原值，`FAIL` 抛 `EnumDictConvertException` |
| `jakit.enum-dict.i18n.missing-policy` | `IGNORE` | 翻译缺失时的默认处理策略：`IGNORE` 回退字面 label，`FAIL` 抛 `EnumDictI18nException` |

两个 `FAIL` 策略分别抛 `EnumDictConvertException` 与 `EnumDictI18nException`，二者均继承 `EnumDictException`。

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

## 字典值转换

把对象中的字典 key 映射为展示文本。在 String 字段上使用 `@DictField`，然后调用 `EnumDictService` 或 `EnumDictUtils` 的 `convert` 方法。支持集合转换，以及对目标对象的附加消费。

```java
import com.github.wcqtech.jakit.enumdict.convert.DictField;
import com.github.wcqtech.jakit.enumdict.service.EnumDictUtils;

public class OrderVO {

    @DictField(type = "order_status")
    private String status; // convert 后 "1" -> "已支付"

    @DictField(type = "pay_channel", keyField = "channel")
    private String channelName; // 由 channel 的值填充，channel 保持不变

    private String channel;
}
```

```java
EnumDictUtils.convert(orderVO);
EnumDictUtils.convert(orders); // 集合转换
EnumDictUtils.convert(orders, order -> {
    count.incrementAndGet(); // 对目标对象额外消费
});
```

规则：

- 标注字段必须是 String；`keyField` 缺省时使用标注字段自身作为字典 key 并原地覆盖，显式声明为自身字段名时行为相同。
- `keyField` 指向兄弟字段时，读取该字段的值，把展示文本写入标注字段。
- 嵌套的可转换 bean、`Collection`、`Map` 与对象数组会递归处理，按运行时实际类型判断，raw 或通配符泛型同样适用，基本类型数组跳过。
- 未命中字典时默认保留原值，可通过 `jakit.enum-dict.convert.missing-policy: FAIL` 全局改为抛 `EnumDictConvertException`，或使用 `EnumDictConverter(registry, MissingPolicy.FAIL)` 局部切换。
- Record、final 字段和 JDK 值类型不会被写入。
- 若 Map 的 key 是会被转换的 bean，业务方必须保证 key 的 `hashCode`/`equals` 不依赖被转换字段；转换后 SDK 不会重建 Map 桶结构，需要重建时由业务方自行处理。

## 国际化（i18n）

字典展示文本按 `Locale` 解析，基于 Spring 标准 `MessageSource`。

### 声明 i18n key

接口方式实现 `getDictI18nKey()`，注解方式使用 `@DictI18n` 标记字段；未声明时按 `{type}.{key}` 约定生成 message key。

```java
@EnumDict(type = "order_status")
public enum OrderStatus {

    PENDING(0, "待支付", "order.status.pending"),
    PAID(1, "已支付", "order.status.paid");

    @DictKey
    private final int code;

    @DictValue
    private final String label;

    @DictI18n
    private final String i18nKey;

    OrderStatus(int code, String label, String i18nKey) {
        this.code = code;
        this.label = label;
        this.i18nKey = i18nKey;
    }
}
```

```properties
# src/main/resources/messages.properties
order.status.pending=Pending
order.status.paid=Paid
```

### locale 查询

`EnumDictService` 与 `EnumDictUtils` 提供全部查询方法及 convert 的 `Locale` 重载；未显式传 locale 时使用 `LocaleContextHolder`（非 web 场景为 `Locale.getDefault()`）。locale 查询返回翻译后的 `DictItem` 副本，反向查询按翻译文本匹配。

```java
enumDictService.valueByKey("order_status", "1", Locale.ENGLISH); // Pending
EnumDictUtils.convert(orderVO, Locale.ENGLISH);
```

### 缺失策略

`jakit.enum-dict.i18n.missing-policy` 默认 `IGNORE`，翻译缺失回退字面 label；配置 `FAIL` 时抛 `EnumDictI18nException`。局部切换可直接构造 `MessageSourceDictValueResolver(messageSource, MissingPolicy.FAIL)` 或 `ResourceBundleDictValueResolver(baseName, MissingPolicy.FAIL)`。

`MessageSource` 与 `LocaleResolver` 由业务方或 Spring Boot 自动配置提供，SDK 只消费不注册；不存在 `MessageSource` Bean 时 locale 查询回退字面 label。core 可独立使用 `ResourceBundleDictValueResolver` 或自定义 `DictValueResolver` 实现。

## 独立使用 core

不启动 Spring 时，可以直接使用 `EnumDictRegistry` 和 `EnumDictConverter`：

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
```java
public class OrderVO {
    @DictField(type = "OrderStatus")
    String status;
}
DictValueResolver resolver = new ResourceBundleDictValueResolver("messages");
EnumDictConverter converter = new EnumDictConverter(registry, resolver);
converter.convert(orderVO, Locale.CHINESE); // status: "1" -> "已支付"
```
转换由 `EnumDictConverter` 完成，国际化由 `DictValueResolver` 完成。

## 校验规则

以下情况会在启动阶段直接失败并给出明确错误信息：

- `@EnumDict` 标注在非枚举类上。
- 注解方式枚举缺少 `@DictKey` 或 `@DictValue`，或重复标记同一注解。
- 重复标记 `@DictI18n`。
- 实现 `EnumDictSource` 时各常量的 `getDictType()` 不一致。
- type 为 null 或空白（接口方式）。
- key 为 null 或空白。
- 同一 type 内出现重复 key。
- 同一 type 被不同内容重复注册。
- key 或 value 为 null。


## 特性

- 启动扫描：默认扫描 `@SpringBootApplication` 所在包，可通过配置指定多个包路径，支持 Ant 通配符。
- 两种声明方式：实现 `EnumDictSource`，或使用 `@EnumDict`、`@DictKey`、`@DictValue` 注解。
- 国际化：基于 Spring `MessageSource` 的 `Locale` 查询与转换，支持 `IGNORE`/`FAIL` 缺失策略。
- 只读查询：提供 `EnumDictService` Bean 和 `EnumDictUtils` 静态工具类。
- 严格校验：类型、key、注解完整性等问题在启动阶段 fail-fast。
- 低依赖：`enum-dict-core` 零 Spring 依赖，可独立用于普通 Java 项目。

## 模块

| 模块 | 职责 | 依赖 |
| --- | --- | --- |
| `enum-dict-core` | 注解、`EnumDictSource`、`DictItem`、`EnumDictRegistry`、`EnumDictConverter` | 无 |
| `enum-dict-spring-boot-starter` | 包扫描、自动装配、`EnumDictService` | `enum-dict-core` + Spring Boot |
