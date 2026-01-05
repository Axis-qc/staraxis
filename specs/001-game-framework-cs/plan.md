# Implementation Plan: Game Framework Architecture (C/S Separation)

**Branch**: `001-game-framework-cs` | **Date**: 2026-01-05 | **Spec**: [specs/001-game-framework-cs/spec.md](spec.md)
**Input**: Feature specification from `/specs/001-game-framework-cs/spec.md`

## Summary

实现基于客户端与服务端严格分离的游戏基础架构。核心采用 LibGDX 框架，将业务逻辑（`core` 模块）与渲染交互（`lwjgl3` 模块）完全解耦。服务端采用 20Hz 固定步进更新，使用 Kryo 二进制序列化进行状态同步。

## Technical Context

**Language/Version**: Java 17 (LibGDX 默认版本)  
**Primary Dependencies**: LibGDX 1.14.0, Kryo (序列化), KryoNet (可选，用于网络通信调研)  
**Storage**: N/A (当前阶段仅限内存状态同步)  
**Testing**: JUnit 5 (用于 core 模块逻辑测试)  
**Target Platform**: Desktop (LWJGL3)
**Project Type**: Multi-module Gradle Project (LibGDX structure)  
**Performance Goals**: 20Hz Tick Rate, <10ms logic update time per tick  
**Constraints**: `core` 模块禁止引入任何图形库依赖  
**Scale/Scope**: 核心框架搭建，支持实体状态同步原型

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **模块化 (Modularization)**: ✅ 采用 Gradle 多模块结构。
- **端侧分离 (C/S Separation)**: ✅ 严格区分 core (Logic) 与 lwjgl3 (Render)。
- **命名规范 (Naming)**: ✅ 强制执行“名称(用途)”规范。
- **Mod 支持 (Extensibility)**: ✅ 预留服务端逻辑插件接口。
- **模拟驱动 (Simulation)**: ✅ 固定 20Hz Tick Rate。

## Project Structure

### Documentation (this feature)

```text
specs/001-game-framework-cs/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
core/src/main/java/com/staraxis/game/
├── core/                # 服务端核心逻辑
│   ├── engine/          # 模拟引擎 (Tick 驱动)
│   ├── state/           # 游戏状态管理
│   └── network/         # 通信契约 (Kryo 注册)
└── shared/              # C/S 共享实体/常量

lwjgl3/src/main/java/com/staraxis/game/
└── client/              # 客户端渲染与输入转发
    ├── renderer/        # 渲染代理
    └── input/           # 输入处理
```

**Structure Decision**: 遵循 LibGDX 标准多模块结构，但在 `core` 内部划分明确的 `core` (Server-only) 和 `shared` 目录，确保逻辑隔离。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
