# staraxis Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-01-05

## Active Technologies
- Java (LibGDX Framework) + `libgdx`, `gdx-freetype` (003-localization-ui-settings)
- LibGDX `Preferences` ("staraxis-settings") (003-localization-ui-settings)
- Java (LibGDX Framework) + `libgdx`, `gdx-freetype`, `Gradle` (004-ui-layer-decoupling)
- N/A (UI State only) (004-ui-layer-decoupling)
- Java 21 + libGDX 1.14.0（含 Scene2D UI / gdx-freetype） (005-hex-world-gen)
- N/A（本期配置仅用于启动本局；是否持久化到偏好设置后续再定） (005-hex-world-gen)
- Java 21 + libGDX 1.14.0 (Scene2D UI, gdx-freetype) (006-ui-input-polishing)
- N/A (本地化文本存储于 .properties 资源文件) (006-ui-input-polishing)
- N/A（本期配置用于启动本局；是否持久化到偏好/存档系统后续再定） (007-stellar-planet-gen)
- Java 21（Gradle Toolchain） + libGDX 1.14.0（shared 模块）、Kryo 5.5.0、Jackson 2.16.1（server/lwjgl3 使用）、LWJGL3 backend（客户端） (009-galaxy-system-gen)
- N/A（本特性阶段只涉及内存数据结构；序列化由现有 Kryo 体系承接） (009-galaxy-system-gen)

- Java 17 (LibGDX 默认版本) + LibGDX 1.14.0, Kryo (序列化), KryoNet (可选，用于网络通信调研) (001-game-framework-cs)

## Project Structure

```text
src/
tests/
```

## Commands

# Add commands for Java 17 (LibGDX 默认版本)

## Code Style

Java 17 (LibGDX 默认版本): Follow standard conventions

## Recent Changes
- 009-galaxy-system-gen: Added Java 21（Gradle Toolchain） + libGDX 1.14.0（shared 模块）、Kryo 5.5.0、Jackson 2.16.1（server/lwjgl3 使用）、LWJGL3 backend（客户端）
- 007-stellar-planet-gen: Added Java 21 + libGDX 1.14.0（含 Scene2D UI / gdx-freetype）
- 006-ui-input-polishing: Added Java 21 + libGDX 1.14.0 (Scene2D UI, gdx-freetype)


<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
