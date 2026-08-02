# jakit

[中文](README.md) | [English](README.en.md)

[![](https://img.shields.io/badge/GitHub-wcqtech/jakit-blue?logo=github)](https://github.com/wcqtech/jakit)
[![](https://jitpack.io/v/wcqtech/jakit.svg)](https://jitpack.io/#wcqtech/jakit)

jakit 是一个 Java 开发工具集。

[enum-dict](enum-dict-core/README.md) 数据字典组件：简单、快捷，让枚举成为数据字典。


## 模块

| 模块 | 说明 |
| --- | --- |
| `enum-dict-core` | 注解、`EnumDictSource` 接口、`DictItem` 与 `EnumDictRegistry`。零 Spring 依赖，可独立用于普通 Java 项目。 |
| `enum-dict-spring-boot-starter` | Spring Boot 3 自动装配，扫描业务包中的枚举字典、注册到内存，并提供 `EnumDictService` 与 `EnumDictUtils`。 |


## 环境要求

- JDK 17+
- Maven 3.6+
- Spring Boot 3.0+ (构建基准版本为 3.2.7)

## 许可证

本项目基于 Apache License 2.0 发布。
