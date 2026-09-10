# jakit

[中文](README.md) | [English](README.en.md)

[![](https://img.shields.io/badge/GitHub-wcqtech/jakit-blue?logo=github)](https://github.com/wcqtech/jakit)
[![](https://jitpack.io/v/wcqtech/jakit.svg)](https://jitpack.io/#wcqtech/jakit)

jakit 是一个 Java 开发工具集。

[enum-dict](enum-dict-core/README.md) 数据字典组件：简单、快捷，让枚举成为数据字典。

[common-utils](common-utils/README.md) 实用的小工具们。

[dbucket](dbucket-core/README.md) 基于数据库存储的轻量分布式令牌桶。

## 模块

| 模块 | 说明 |
| --- | --- |
| `enum-dict-core` | 注解、`EnumDictSource` 接口、`DictItem`、`EnumDictRegistry` 与 `EnumDictConverter`。零 Spring 依赖，可独立用于普通 Java 项目。 |
| `enum-dict-spring-boot-starter` | Spring Boot 3 自动装配，扫描业务包中的枚举字典、注册到内存，并提供 `EnumDictService` 与 `EnumDictUtils`。 |
| `common-utils` | 无依赖的通用工具类，包含序列、BigDecimal 与中文金额工具。 |
| `dbucket-core` | 门面 API、`BucketStore` SPI、MySQL/PostgreSQL/KingbaseES 方言 SQL 片段、JDBC 存储与等待策略。只需 spring-jdbc。 |
| `dbucket-spring-boot-starter` | Spring Boot 3 自动装配、`@DBucket` 方法级限流注解与可选 Micrometer 指标。 |


## 环境要求

- JDK 17+
- Maven 3.6+
- Spring Boot 3.0+ (构建基准版本为 3.2.7)

## 许可证

本项目基于 Apache License 2.0 发布。
