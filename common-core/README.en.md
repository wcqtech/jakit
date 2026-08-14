# common-core

[中文](README.md) | [English](README.en.md)

[![](https://img.shields.io/badge/GitHub-wcqtech/jakit-blue?logo=github)](https://github.com/wcqtech/jakit)
[![](https://jitpack.io/v/wcqtech/jakit.svg)](https://jitpack.io/#wcqtech/jakit)

jakit's common foundational utility module with zero third-party runtime dependencies, usable directly in plain Java projects.

## Utilities

| Utility | Purpose |
| --- | --- |
| `SeqUtils` | Assigns sequence numbers to collection elements in order |

Package layout: `com.github.wcqtech.jakit.common`, with future subpackages organized by feature.

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
        <artifactId>common-core</artifactId>
        <version>${version}</version>
    </dependency>
</dependencies>
```

The version should match an actually released Git tag.

## SeqUtils

`com.github.wcqtech.jakit.common.sequence.SeqUtils`

Assigns sequence numbers to collection elements, supporting custom start values, steps, and type conversion, with an optional visitor for extra element consumption during iteration.

### Basic Usage

```java
import com.github.wcqtech.jakit.common.sequence.SeqUtils;

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
