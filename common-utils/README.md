# common-utils

[中文](README.md) | [English](README.en.md)

[![](https://img.shields.io/badge/GitHub-wcqtech/jakit-blue?logo=github)](https://github.com/wcqtech/jakit)
[![](https://jitpack.io/v/wcqtech/jakit.svg)](https://jitpack.io/#wcqtech/jakit)

jakit 的实用工具模块。

## 工具类

| 工具类 | 用途 |
| --- | --- |
| `SeqUtils` | 为集合元素按顺序分配序列号 |
| `BigDecimalUtils` | `BigDecimal` 比较、范围判断、极值与聚合计算 |
| `BigDecimalFormatUtils` | `BigDecimal` 百分比与数字分组格式化 |
| `ChineseAmountUtils` | 中文金额分组、RMB 符号格式与大写转换 |
| `TreeUtils` | 扁平数据构建树、排序、遍历、查找与路径提取 |

包结构：`com.github.wcqtech.jakit.utils`，按功能子包扩展，目前包含 `sequence`、`number`、`amount` 与 `tree`。

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
        <artifactId>common-utils</artifactId>
        <version>${version}</version>
    </dependency>
</dependencies>
```

版本号以实际发布的 Git tag 为准。

## SeqUtils

`com.github.wcqtech.jakit.utils.sequence.SeqUtils`

为集合元素分配序列号，支持自定义起始值、步长与类型转换，并可在遍历过程中对元素进行附加消费。

### 基础用法

```java
import com.github.wcqtech.jakit.utils.sequence.SeqUtils;

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

## BigDecimalUtils

`com.github.wcqtech.jakit.utils.number.BigDecimalUtils`

提供 `BigDecimal` 的数值比较、范围判断、极值与聚合计算。所有比较都基于数值大小而非精度，因此 `1.0` 与 `1.00` 被视为相等。

### 比较

```java
import com.github.wcqtech.jakit.utils.number.BigDecimalUtils;

import java.math.BigDecimal;

BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

BigDecimalUtils.compare(a, b);      // 0
BigDecimalUtils.eq(a, b);           // true
BigDecimalUtils.ne(a, b);           // false
BigDecimalUtils.gt(a, b);           // false
BigDecimalUtils.gte(a, b);          // true
BigDecimalUtils.lt(a, b);           // false
BigDecimalUtils.lte(a, b);          // true
```

### 正负与范围

```java
BigDecimalUtils.isZero(BigDecimal.ZERO);           // true
BigDecimalUtils.isPositive(new BigDecimal("0.01")); // true
BigDecimalUtils.isNegative(new BigDecimal("-0.01")); // true

// 闭区间 [1, 100] 与开区间 (1, 100)
BigDecimalUtils.between(new BigDecimal("50"), BigDecimal.ONE, new BigDecimal("100")); // true
BigDecimalUtils.betweenExclusive(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("100")); // false

// 钳制到 [1, 100]
BigDecimalUtils.clamp(new BigDecimal("200"), BigDecimal.ONE, new BigDecimal("100")); // 100
```

### 极值与聚合

```java
List<BigDecimal> values = List.of(
        new BigDecimal("10.5"),
        new BigDecimal("3.14"),
        new BigDecimal("99")
);

BigDecimalUtils.min(values);  // 3.14
BigDecimalUtils.max(values);  // 99
BigDecimalUtils.sum(values);  // 112.64
BigDecimalUtils.mul(values);  // 10.5 * 3.14 * 99
```

`min`、`max`、`sum`、`mul` 同时提供可变参数重载，例如 `BigDecimalUtils.sum(a, b, c)`。

### 说明

- 所有参数均不可为 null。
- `min`、`max` 要求集合非空；`sum`、`mul` 允许空集合，分别返回 `BigDecimal.ZERO` 与 `BigDecimal.ONE`。
- `between`、`betweenExclusive`、`clamp` 要求 `min <= max`，否则抛 `IllegalArgumentException`。

## BigDecimalFormatUtils

`com.github.wcqtech.jakit.utils.number.BigDecimalFormatUtils`

提供百分比与数字分组格式化。百分比格式化区分两种输入语义：比率会先放大 100 倍，已经是百分数的数值则不再缩放。

### 百分比

```java
import com.github.wcqtech.jakit.utils.number.BigDecimalFormatUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 比率：0.15 -> 15%，默认保留两位小数并使用 HALF_UP
BigDecimalFormatUtils.ratioPercent(new BigDecimal("0.155")); // 15.50%

// 已经是百分数的值：15 -> 15%
BigDecimalFormatUtils.percent(new BigDecimal("15.556")); // 15.56%

// 自定义小数位数与舍入模式
BigDecimalFormatUtils.ratioPercent(new BigDecimal("0.1556"), 2, RoundingMode.HALF_UP); // 15.56%
BigDecimalFormatUtils.percent(new BigDecimal("15.5"), 0, RoundingMode.DOWN);            // 15%
```

格式化的百分比不使用千分位分组。

### 数字分组

```java
import java.util.Locale;

// 保留全部小数位，不补零
BigDecimalFormatUtils.digitGrouping(new BigDecimal("1234567.891"), Locale.US); // 1,234,567.891
BigDecimalFormatUtils.digitGrouping(new BigDecimal("1234567.891"), Locale.GERMANY); // 1.234.567,891

// 使用 DecimalFormat 模式控制分组、小数位与舍入
BigDecimalFormatUtils.digitGrouping(
        new BigDecimal("1234567.891"),
        "#,##0.00",
        Locale.US); // 1,234,567.89
```

`digitGrouping` 遵循区域设置的分组规则，例如印度区域设置会把 `123456789` 格式化为 `12,34,56,789`。

### 说明

- `ratioPercent` 的入参为比率（典型取值 0~1），`percent` 的入参为已是百分数的数值。
- `scale` 不可为负数；`ratio`、`percent`、`roundingMode` 与 `locale` 均不可为 null。
- `digitGrouping(value, pattern, locale)` 传入非法模式时抛 `IllegalArgumentException`。

## ChineseAmountUtils

`com.github.wcqtech.jakit.utils.amount.ChineseAmountUtils`

提供中文数字分组、RMB 符号格式与人民币大写金额转换。

### 中文数字分组

```java
import com.github.wcqtech.jakit.utils.amount.ChineseAmountUtils;

import java.math.BigDecimal;

ChineseAmountUtils.digitGrouping(new BigDecimal("1234567.891")); // 1,234,567.891
```

该方法是 `BigDecimalFormatUtils.digitGrouping(value, Locale.CHINA)` 的便捷封装。

### 人民币大写

```java
import java.math.RoundingMode;

ChineseAmountUtils.toRMBUppercase(new BigDecimal("123.45"));            // 壹佰贰拾叁元肆角伍分
ChineseAmountUtils.toRMBUppercase(new BigDecimal("10.50"));             // 壹拾元伍角整
ChineseAmountUtils.toRMBUppercase(new BigDecimal("100000000.05"));      // 壹亿元零伍分
ChineseAmountUtils.toRMBUppercase(new BigDecimal("-123.45"));           // 负壹佰贰拾叁元肆角伍分
ChineseAmountUtils.toRMBUppercase(new BigDecimal("1.009"));             // 壹元零壹分
ChineseAmountUtils.toRMBUppercase(new BigDecimal("1.009"), RoundingMode.DOWN); // 壹元整
```

默认保留两位小数并使用 `HALF_UP` 舍入，也可以通过重载指定 `RoundingMode`。

### RMB 符号

```java
ChineseAmountUtils.formatRmb(new BigDecimal("1234.5"));                  // ￥1,234.50
ChineseAmountUtils.formatRmb(new BigDecimal("1234567.891"));             // ￥1,234,567.89
ChineseAmountUtils.formatRmb(new BigDecimal("-1234.5"));                 // -￥1,234.50
ChineseAmountUtils.formatRmb(new BigDecimal("1.009"), RoundingMode.DOWN); // ￥1.00
```

`formatRmb` 默认保留两位小数并使用 `HALF_UP` 舍入，负号位于 `￥` 符号之前。

### 说明

- 金额与舍入模式均不可为 null。
- `formatRmb` 与 `toRMBUppercase` 保留两位小数，默认使用 `HALF_UP` 舍入，可通过重载指定 `RoundingMode`。
- RMB 符号格式中负号位于符号前，如 `-￥1,234.50`。
- 整数部分按四位一节使用 `万、亿、兆` 等单位，中间空位补 `零`。
- 分位为 0 时以 `整` 结尾，角位为 0 而分位非 0 时在元后补 `零`。
- 负数以 `负` 开头。

## TreeUtils

`com.github.wcqtech.jakit.utils.tree.TreeUtils`

便捷的把扁平业务数据构建为树形结构，并提供排序、遍历、指定深度查找、id 查找、索引与路径提取等高频能力。

### 构建

```java
import com.github.wcqtech.jakit.utils.tree.TreeUtils;
import com.github.wcqtech.jakit.utils.tree.TreeNode;

import java.util.Comparator;
import java.util.List;

List<Menu> menus = ...;

// parentId 为 null 或找不到对应节点时成为根节点，返回森林
List<TreeNode<Menu>> roots = TreeUtils.buildTree(menus, Menu::getId, Menu::getParentId);

// 带 comparator 时对 roots 与每层 children 排序
List<TreeNode<Menu>> sorted = TreeUtils.buildTree(menus, Menu::getId, Menu::getParentId,
        Comparator.comparing(Menu::getName));

// 构建后排序
TreeUtils.sort(roots, Comparator.comparing(Menu::getName));
```

- 重复 id、id 为 null 或存在环时抛 `IllegalArgumentException`。
- 带 comparator 的排序是稳定排序，相同排序值保持输入顺序。

### 遍历

```java
TreeUtils.preorder(roots);    // 前序：节点先于 children
TreeUtils.postorder(roots);   // 后序：children 先于节点
TreeUtils.bfs(roots);         // 广度优先，逐层

// Data 版本直接返回业务数据
TreeUtils.preorderData(roots);
TreeUtils.bfsData(roots);

// 深度受限：maxDepth 相对本次传入的根节点，0 只返回根
TreeUtils.preorder(roots, 0); // 仅根节点
TreeUtils.preorder(roots, 1); // 根与直接 children
TreeUtils.bfs(roots, 2);
```

### 查找与索引

```java
// 按 id 查找，未命中返回 Optional.empty()
Optional<TreeNode<Menu>> found = TreeUtils.findById(roots, Menu::getId, 42L);

// 位于指定深度的节点
List<TreeNode<Menu>> level = TreeUtils.findAtDepth(roots, 2);

// 唯一键索引：key 重复或为 null 抛 IllegalArgumentException
Map<String, TreeNode<Menu>> byCode = TreeUtils.uniqueIndex(roots, Menu::getCode);

// 普通键索引：value 保持遍历顺序，可对每组排序
Map<Long, List<TreeNode<Menu>>> byParent = TreeUtils.index(roots, Menu::getParentId,
        Comparator.comparing(Menu::getSort));
```

### 后代与路径

```java
// 全部后代，不包含节点自身，按前序排列
List<TreeNode<Menu>> descendants = TreeUtils.descendants(node);

// 从根到目标节点的路径，root 在前、目标在后、包含两端
Optional<List<TreeNode<Menu>>> path = TreeUtils.findPath(roots, Menu::getId, 42L);

// 从节点向上到根的路径（node 在前、根在后）；node 不在该森林时返回 Optional.empty()
Optional<List<TreeNode<Menu>>> toRoot = TreeUtils.pathToRoot(roots, someNode);

// reverse = true 时从根到节点（根在前、node 在后）
Optional<List<TreeNode<Menu>>> fromRoot = TreeUtils.pathToRoot(roots, someNode, true);

```

### 说明

- `TreeNode<T>` 不可变，不持有 parent 引用，便于共享、缓存与序列化；`getChildren()` 返回只读视图。
- 深度一律以 0 为根；深度受限遍历与 `findAtDepth` 的深度相对本次传入的根节点计算。
- 集合入参为 null 抛 `NullPointerException`；空集合返回空结果；`maxDepth`/`depth` 为负抛 `IllegalArgumentException`。
- 提取器、keyExtractor 与 comparator 不可为 null；id 或 key 提取结果为 null 抛 `IllegalArgumentException`。
- 遍历、排序、查找均不修改树结构。
