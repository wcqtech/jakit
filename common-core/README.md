# common-core

[中文](README.md) | [English](README.en.md)

[![](https://img.shields.io/badge/GitHub-wcqtech/jakit-blue?logo=github)](https://github.com/wcqtech/jakit)
[![](https://jitpack.io/v/wcqtech/jakit.svg)](https://jitpack.io/#wcqtech/jakit)

jakit 的公共基础工具模块，零第三方运行期依赖，可被普通 Java 项目直接引用。

## 工具类

| 工具类 | 用途 |
| --- | --- |
| `SeqUtils` | 为集合元素按顺序分配序列号 |

包结构：`com.github.wcqtech.jakit.common`，后续按功能子包扩展。

## 环境要求

- JDK 17+

## 引入

通过 JitPack 引入：

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
        <artifactId>common-core</artifactId>
        <version>${version}</version>
    </dependency>
</dependencies>
```

版本号以实际发布的 Git tag 为准。

## SeqUtils

`com.github.wcqtech.jakit.common.sequence.SeqUtils`

为集合元素分配序列号，支持自定义起始值、步长与类型转换，并可在遍历过程中对元素进行附加消费。

### 基础用法

```java
import com.github.wcqtech.jakit.common.sequence.SeqUtils;

List<Item> items = ...;
SeqUtils.sequence(items, Item::setSeq); // 1, 2, 3...
```

### 自定义起始值与步长

```java
// 10, 12, 14...
SeqUtils.sequence(items, Item::setSeq, 10, 2);

// 3, 2, 1
SeqUtils.sequence(items, Item::setSeq, 3, -1);
```

### 类型转换

```java
SeqUtils.sequence(items, Item::setLabel, seq -> "SEQ-" + seq);
```

### 附加消费

visitor 会在每个元素完成序列号分配后被调用，可对元素做额外处理：

```java
SeqUtils.sequence(items, Item::setSeq, seq -> seq, 1, 1, item -> log(item));
```

### 说明

- 分配顺序跟随集合的迭代顺序，需要确定顺序时请使用 `List` 等有序集合。
- 不保证多个线程对同一集合并发调用时的安全，需要时请自行同步。
- 所有参数均不可为 null，集合为空时不执行任何操作。
- 若回调抛出异常，之前的元素可能已经被修改。
