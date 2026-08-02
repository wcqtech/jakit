# jakit

[中文](README.md) | [English](README.en.md)

jakit is a Java development toolkit.

[enum-dict](enum-dict-core/README.en.md) is a data dictionary component: simple and fast, turning enums into data dictionaries.

## Modules

| Module | Description |
| --- | --- |
| `enum-dict-core` | Annotations, the `EnumDictSource` interface, `DictItem`, and `EnumDictRegistry`. Zero Spring dependency, usable standalone in plain Java projects. |
| `enum-dict-spring-boot-starter` | Spring Boot 3 auto-configuration that scans enum dictionaries in business packages, registers them in memory, and provides `EnumDictService` and `EnumDictUtils`. |

## Requirements

- JDK 17+
- Maven 3.6+
- Spring Boot 3.0+ (build baseline: 3.2.7)

## License

This project is released under Apache License 2.0.
