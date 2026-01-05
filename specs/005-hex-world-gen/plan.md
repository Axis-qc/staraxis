# Implementation Plan: Hex World Generation & New Game Config (六边形世界生成与新游戏配置)

**Branch**: `005-hex-world-gen` | **Date**: 2026-01-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/005-hex-world-gen/spec.md`

## Summary

实现六边形星域大地图（Cubic 坐标、顶部正交视角、语义缩放），并在主菜单“新游戏”进入世界生成配置界面，支持配置：地图大小（半径 R）、宜居星球比例（按星系格概率）、地图种子（字符串转数值，空则随机），以及 AI 数量/技术等级占位（显示但禁用）。世界生成逻辑归属服务端/逻辑层，客户端负责渲染与配置 UI。

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 21  
**Primary Dependencies**: libGDX 1.14.0（含 Scene2D UI / gdx-freetype）  
**Storage**: N/A（本期配置仅用于启动本局；是否持久化到偏好设置后续再定）  
**Testing**: JUnit 5（数学/坐标转换/确定性生成的单元测试），手动 UI 验证  
**Target Platform**: Desktop（`lwjgl3`），逻辑层在 `core/shared` 可 headless 运行  
**Project Type**: Multi-project Gradle（`core`/`shared`/`lwjgl3`）  
**Performance Goals**: 1080p 渲染 60FPS；生成 100x100 级别地图 < 200ms（目标）  
**Constraints**: 严格遵循 C/S 分离与 UI 独立性；逻辑层不得依赖 Scene2D/UI；生成必须可复现（同配置同种子同结果）  
**Scale/Scope**: 首版仅实现世界生成配置与基础六边形地图渲染（含拾取/高亮/语义缩放），不包含真实玩法系统

## Constitution Check

- **模块化与可维护性 (Modularization & Maintainability)**: 是。世界生成、坐标转换、渲染、UI 配置分别隔离到独立模块/类。
- **架构分层与端侧分离 (Layered Architecture & C/S Separation)**: 是。世界生成与状态数据归属逻辑层；客户端仅渲染与收集配置输入。
- **规范化命名与注释 (Naming & Documentation)**: 是。遵循现有项目中文说明与职责命名规范。
- **扩展性与 Mod 支持 (Extensibility & Mod Support)**: 是。配置模型与生成器接口预留扩展字段，便于后续 AI/科技等接入。
- **游戏模拟驱动 (Simulation-Driven Logic)**: 是。世界生成作为一次性初始化流程，不依赖帧率；后续状态仍由 tick 驱动。
- **UI 层独立性 (Independent UI Layer)**: 是。新游戏配置 UI 仅操作配置模型与触发开始，不包含世界规则计算。

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

## Project Structure

### Documentation (this feature)

```text
specs/005-hex-world-gen/
├── plan.md              # This file (/speckit.plan)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
core/src/main/java/com/staraxis/game/core/
└── world/                       # 世界生成与世界数据（逻辑层）

shared/src/main/java/com/staraxis/game/shared/
├── model/                       # 可序列化的世界数据结构（若需要下发给客户端）
└── network/                     # 本局开始/世界生成请求响应（如采用消息驱动）

lwjgl3/src/main/java/com/staraxis/game/client/
└── ui/                          # 客户端 UI 与渲染
    ├── view/                    # 世界渲染视口 / 摄像机控制
    └── screens/                 # MainMenu / NewGameConfig / WorldScreen
```

**Structure Decision**: 逻辑层（`core/shared`）负责世界生成与数据；客户端（`lwjgl3`）负责配置 UI、渲染与输入拾取。通过数据结构/消息/事件边界避免 UI 与逻辑耦合。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |

