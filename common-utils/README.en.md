# common-utils

[中文](README.md) | [English](README.en.md)

[![](https://img.shields.io/badge/GitHub-wcqtech/jakit-blue?logo=github)](https://github.com/wcqtech/jakit)
[![](https://jitpack.io/v/wcqtech/jakit.svg)](https://jitpack.io/#wcqtech/jakit)

jakit's useful utilities module.

## Utilities

| Utility | Purpose |
| --- | --- |
| `SeqUtils` | Assigns sequence numbers to collection elements in order |
| `BigDecimalUtils` | `BigDecimal` comparison, range checks, extremes, and aggregation |
| `BigDecimalFormatUtils` | `BigDecimal` percentage and digit grouping formatting |
| `ChineseAmountUtils` | Chinese digit grouping, RMB-symbol formatting, and uppercase conversion |
| `TreeUtils` | Builds trees from flat data; sorting, traversal, lookup, and path extraction |

Package layout: `com.github.wcqtech.jakit.utils`, with subpackages organized by feature, currently including `sequence`, `number`, `amount`, and `tree`.

## Requirements

- JDK 17+

## Installation

Add the dependency through JitPack:

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

The version should match an actually released Git tag.

## SeqUtils

`com.github.wcqtech.jakit.utils.sequence.SeqUtils`

Assigns sequence numbers to collection elements, supporting custom start values, steps, and type conversion, with an optional visitor for extra element consumption during iteration.

### Basic Usage

```java
import com.github.wcqtech.jakit.utils.sequence.SeqUtils;

List<Item> items = ...;
SeqUtils.sequence(items, Item::setSeq); // 1, 2, 3...
```

### Custom Start and Step

```java
// 10, 12, 14...
SeqUtils.sequence(items, Item::setSeq, 10, 2);

// 3, 2, 1
SeqUtils.sequence(items, Item::setSeq, 3, -1);
```

### Type Conversion

```java
SeqUtils.sequence(items, Item::setLabel, seq -> "SEQ-" + seq);
```

### Additional Consumption

The visitor is invoked after each element receives its sequence number, allowing extra processing:

```java
SeqUtils.sequence(items, Item::setSeq, seq -> seq, 1, 1, item -> log(item));
```

### Notes

- Assignment order follows the collection's iteration order; use an ordered collection such as `List` when order matters.
- Concurrent invocations on the same collection are not thread-safe; synchronize externally when needed.
- All arguments must be non-null; an empty collection is a no-op.
- If a callback throws, earlier elements may already have been modified.

## BigDecimalUtils

`com.github.wcqtech.jakit.utils.number.BigDecimalUtils`

Provides numeric comparison, range checks, extremes, and aggregation for `BigDecimal`. All comparisons are based on numeric value rather than scale, so `1.0` and `1.00` are considered equal.

### Comparison

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

### Sign and Range

```java
BigDecimalUtils.isZero(BigDecimal.ZERO);            // true
BigDecimalUtils.isPositive(new BigDecimal("0.01")); // true
BigDecimalUtils.isNegative(new BigDecimal("-0.01")); // true

// Closed range [1, 100] and open range (1, 100)
BigDecimalUtils.between(new BigDecimal("50"), BigDecimal.ONE, new BigDecimal("100")); // true
BigDecimalUtils.betweenExclusive(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("100")); // false

// Clamp to [1, 100]
BigDecimalUtils.clamp(new BigDecimal("200"), BigDecimal.ONE, new BigDecimal("100")); // 100
```

### Extremes and Aggregation

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

`min`, `max`, `sum`, and `mul` also provide varargs overloads, for example `BigDecimalUtils.sum(a, b, c)`.

### Notes

- All arguments must be non-null.
- `min` and `max` require a non-empty collection; `sum` and `mul` allow empty collections and return `BigDecimal.ZERO` and `BigDecimal.ONE` respectively.
- `between`, `betweenExclusive`, and `clamp` require `min <= max`; otherwise an `IllegalArgumentException` is thrown.

## BigDecimalFormatUtils

`com.github.wcqtech.jakit.utils.number.BigDecimalFormatUtils`

Provides percentage and digit grouping formatting. Percentage formatting distinguishes two input semantics: a ratio is first scaled by one hundred, while an already percentage value is not scaled further.

### Percentage

```java
import com.github.wcqtech.jakit.utils.number.BigDecimalFormatUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Ratio: 0.15 -> 15%, defaults to two fraction digits and HALF_UP
BigDecimalFormatUtils.ratioPercent(new BigDecimal("0.155")); // 15.50%

// Already a percentage: 15 -> 15%
BigDecimalFormatUtils.percent(new BigDecimal("15.556")); // 15.56%

// Custom fraction digits and rounding mode
BigDecimalFormatUtils.ratioPercent(new BigDecimal("0.1556"), 2, RoundingMode.HALF_UP); // 15.56%
BigDecimalFormatUtils.percent(new BigDecimal("15.5"), 0, RoundingMode.DOWN);            // 15%
```

Formatted percentages never use thousands grouping.

### Digit Grouping

```java
import java.util.Locale;

// Preserves all fraction digits without adding trailing zeros
BigDecimalFormatUtils.digitGrouping(new BigDecimal("1234567.891"), Locale.US); // 1,234,567.891
BigDecimalFormatUtils.digitGrouping(new BigDecimal("1234567.891"), Locale.GERMANY); // 1.234.567,891

// Use a DecimalFormat pattern to control grouping, fraction digits, and rounding
BigDecimalFormatUtils.digitGrouping(
        new BigDecimal("1234567.891"),
        "#,##0.00",
        Locale.US); // 1,234,567.89
```

`digitGrouping` honors locale-specific grouping rules; for example an Indian locale formats `123456789` as `12,34,56,789`.

### Notes

- `ratioPercent` takes a ratio (typically between 0 and 1), while `percent` takes a value that is already a percentage.
- `scale` must not be negative; `ratio`, `percent`, `roundingMode`, and `locale` must not be null.
- `digitGrouping(value, pattern, locale)` throws `IllegalArgumentException` for an invalid pattern.

## ChineseAmountUtils

`com.github.wcqtech.jakit.utils.amount.ChineseAmountUtils`

Provides Chinese digit grouping, RMB-symbol formatting, and RMB uppercase amount conversion.

### Chinese Digit Grouping

```java
import com.github.wcqtech.jakit.utils.amount.ChineseAmountUtils;

import java.math.BigDecimal;

ChineseAmountUtils.digitGrouping(new BigDecimal("1234567.891")); // 1,234,567.891
```

This is a convenience wrapper around `BigDecimalFormatUtils.digitGrouping(value, Locale.CHINA)`.

### RMB Uppercase

```java
import java.math.RoundingMode;

ChineseAmountUtils.toRMBUppercase(new BigDecimal("123.45"));            // 壹佰贰拾叁元肆角伍分
ChineseAmountUtils.toRMBUppercase(new BigDecimal("10.50"));             // 壹拾元伍角整
ChineseAmountUtils.toRMBUppercase(new BigDecimal("100000000.05"));      // 壹亿元零伍分
ChineseAmountUtils.toRMBUppercase(new BigDecimal("-123.45"));           // 负壹佰贰拾叁元肆角伍分
ChineseAmountUtils.toRMBUppercase(new BigDecimal("1.009"));             // 壹元零壹分
ChineseAmountUtils.toRMBUppercase(new BigDecimal("1.009"), RoundingMode.DOWN); // 壹元整
```

By default the amount is rounded to two fraction digits with `HALF_UP`; an overload allows specifying a custom `RoundingMode`.

### RMB Symbol

```java
ChineseAmountUtils.formatRmb(new BigDecimal("1234.5"));                  // ￥1,234.50
ChineseAmountUtils.formatRmb(new BigDecimal("1234567.891"));             // ￥1,234,567.89
ChineseAmountUtils.formatRmb(new BigDecimal("-1234.5"));                 // -￥1,234.50
ChineseAmountUtils.formatRmb(new BigDecimal("1.009"), RoundingMode.DOWN); // ￥1.00
```

`formatRmb` keeps two fraction digits and defaults to `HALF_UP` rounding; the minus sign is placed before the `￥` symbol.

### Notes

- The amount and rounding mode must not be null.
- `formatRmb` and `toRMBUppercase` keep two fraction digits and default to `HALF_UP` rounding; pass a `RoundingMode` to override.
- RMB-symbol output puts the minus sign before the symbol, e.g. `-￥1,234.50`.
- The integer part is grouped in four-digit sections using units such as `万`, `亿`, and `兆`, with `零` filling gaps between sections.
- Results end with `整` when the fen digit is zero, and a `零` is inserted after the yuan when jiao is zero but fen is not.
- Negative amounts are prefixed with `负`.

## TreeUtils

`com.github.wcqtech.jakit.utils.tree.TreeUtils`

Easy to builds trees from flat business data and provides sorting, traversal, depth lookup, id lookup, indexing, and path extraction.

### Building

```java
import com.github.wcqtech.jakit.utils.tree.TreeUtils;
import com.github.wcqtech.jakit.utils.tree.TreeNode;

import java.util.Comparator;
import java.util.List;

List<Menu> menus = ...;

// A node becomes a root when its parentId is null or has no matching node;
// the result is a forest
List<TreeNode<Menu>> roots = TreeUtils.buildTree(menus, Menu::getId, Menu::getParentId);

// With a comparator, roots and every level of children are sorted
List<TreeNode<Menu>> sorted = TreeUtils.buildTree(menus, Menu::getId, Menu::getParentId,
        Comparator.comparing(Menu::getName));

// Sort after building
TreeUtils.sort(roots, Comparator.comparing(Menu::getName));
```

- A duplicate id, a null id, or a cycle throws `IllegalArgumentException`.
- Sorting with a comparator is stable: elements with equal keys keep their input order.

### Traversal

```java
TreeUtils.preorder(roots);    // preorder: node before its children
TreeUtils.postorder(roots);   // postorder: children before their node
TreeUtils.bfs(roots);         // breadth-first, level by level

// Data variants return the business data directly
TreeUtils.preorderData(roots);
TreeUtils.bfsData(roots);

// Depth-limited: maxDepth is relative to the passed roots; 0 visits roots only
TreeUtils.preorder(roots, 0); // roots only
TreeUtils.preorder(roots, 1); // roots and direct children
TreeUtils.bfs(roots, 2);
```

### Lookup and Indexing

```java
// Find by id; returns Optional.empty() when nothing matches
Optional<TreeNode<Menu>> found = TreeUtils.findById(roots, Menu::getId, 42L);

// Nodes located at the given depth
List<TreeNode<Menu>> level = TreeUtils.findAtDepth(roots, 2);

// Unique-key index: duplicate or null keys throw IllegalArgumentException
Map<String, TreeNode<Menu>> byCode = TreeUtils.uniqueIndex(roots, Menu::getCode);

// Plain-key index: values keep traversal order; each group can be sorted
Map<Long, List<TreeNode<Menu>>> byParent = TreeUtils.index(roots, Menu::getParentId,
        Comparator.comparing(Menu::getSort));
```

### Descendants and Paths

```java
// All descendants in preorder, excluding the node itself
List<TreeNode<Menu>> descendants = TreeUtils.descendants(node);

// Path from a root to the target node, root first, target last, both included
Optional<List<TreeNode<Menu>>> path = TreeUtils.findPath(roots, Menu::getId, 42L);
```

### Notes

- `TreeNode<T>` is immutable and holds no parent reference, which makes trees safe to share, cache, and serialize; `getChildren()` returns a read-only view.
- Depth always starts at 0 for the root; the depth of depth-limited traversal and `findAtDepth` is relative to the passed roots.
- A null collection argument throws `NullPointerException`; an empty collection yields an empty result; a negative `maxDepth`/`depth` throws `IllegalArgumentException`.
- Extractors, `keyExtractor`, and comparators must not be null; a null extracted id or key throws `IllegalArgumentException`.
- Traversal, sorting, and lookup never modify the tree structure.
