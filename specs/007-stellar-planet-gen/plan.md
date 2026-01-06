# Implementation Plan: Stellar & Planet Generation (恒星与行星生成)

**Branch**: `007-stellar-planet-gen` | **Date**: 2026-01-06 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/007-stellar-planet-gen/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

在六边形星域大地图（封闭边界、半径 R）基础上扩展“星系区块”的恒星/行星生成：每个星系区块生成 1 个星系系统，允许 1..3 颗恒星；行星必须明确归属某一颗恒星（每颗恒星各自生成行星组）。渲染目标为 3D 表现但保持 2D 俯视可读（网格边界/交互高亮清晰）。在“新游戏”流程中接入世界生成设置：地图大小预设（半径 R）、Seed、StarDensity、PlanetComplexity、NebulaRatio。逻辑层负责生成与数据统计；客户端负责 UI 与渲染，遵循 C/S 分离与 UI 独立性。

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: libGDX 1.14.0（含 Scene2D UI / gdx-freetype）  
**Storage**: 本期配置仅用于启动本局；本期不做持久化到偏好/存档系统  
**Testing**: JUnit 5（生成确定性、统计、数据规则的单元测试），手动 UI/渲染验证  
**Target Platform**: Desktop（`lwjgl3`），逻辑层在 `core/shared` 可 headless 运行
**Project Type**: Multi-project Gradle（`core`/`shared`/`lwjgl3`）  
**Performance Goals**: 1080p 渲染 60FPS；默认地图预设下生成耗时 < 300ms（见 SC-001）  
**Constraints**: 严格遵循 C/S 分离与 UI 独立性；逻辑层不得依赖 Scene2D/UI；生成必须可复现（同配置同种子同结果）；避免硬编码与硬枚举（数据驱动预设）  
**Scale/Scope**: 首版仅覆盖：配置接入 + 星系区块恒星/行星数据生成 + 统计验证 + 俯视可读渲染呈现；不包含玩法系统（殖民/战斗/经济等）

## Constitution Check

- **模块化与可维护性 (Modularization & Maintainability)**: 通过。配置/生成/数据/渲染/界面分层，预设与类型表数据驱动，避免硬编码与硬枚举。
- **架构分层与端侧分离 (Layered Architecture & C/S Separation)**: 通过。世界生成与统计归属逻辑层（`core/shared`）；客户端（`lwjgl3`）仅负责渲染与 UI 输入。
- **规范化命名与注释 (Standardized Naming & Documentation)**: 通过。按项目规范使用中文命名说明与文档。
- **扩展性与 Mod 支持 (Extensibility & Mod Support)**: 通过。恒星/行星类型与生成参数为可扩展数据结构，可后续开放 Mod。
- **游戏模拟驱动 (Simulation-Driven Logic)**: 通过。世界生成是一次性初始化过程；后续玩法系统不在本期实现。
- **UI 层独立性 (Independent UI Layer)**: 通过。新游戏配置 UI 仅编辑配置与触发生成，不包含世界规则计算。

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

## Project Structure

### Documentation (this feature)

```text
specs/007-stellar-planet-gen/
├── plan.md              # This file (/speckit.plan)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
core/src/main/java/com/staraxis/game/core/
└── world/                       # 世界生成与世界数据（逻辑层）

shared/src/main/java/com/staraxis/game/shared/
├── model/                       # 可序列化的世界数据结构（如需下发给客户端）
└── network/                     # 本局开始/世界生成请求响应（如采用消息驱动）

lwjgl3/src/main/java/com/staraxis/game/client/
└── ui/                          # 客户端 UI 与渲染
    ├── view/                    # 世界渲染视口 / 摄像机控制
    └── screens/                 # MainMenu / NewGameConfig / WorldScreen
```

**Structure Decision**: 逻辑层（`core/shared`）负责世界生成、数据模型与统计；客户端（`lwjgl3`）负责配置 UI、渲染与输入拾取。通过数据结构/消息/事件边界避免 UI 与逻辑耦合。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
