# Implementation Plan: 014 - 基础坐标系与比例尺

**Branch**: `014-coordinate-system-scale` | **Date**: 2026-01-08 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `G:\games\staraxis\specs\014-coordinate-system-scale\spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

本计划旨在为游戏实现一个统一的世界坐标系与动态比例尺系统。核心技术是采用分层坐标（`WorldCoord` 大尺度整型网格 + `LocalOffset` 局部浮点偏移）来支持星系级的巨大数值范围，同时保证近景渲染精度。功能交付物包含一个核心的 `CoordinateService` 用于坐标转换与比例尺计算，以及一个 F3 调试工具，该工具不仅在 UI 悬浮窗中显示坐标信息，还会在世界空间中实际渲染坐标轴与自适应网格，以直观验证系统的正确性。

## Technical Context

**Language/Version**: Java 21（见根 `build.gradle` 的 toolchain）
**Primary Dependencies**: libGDX 1.12.1 + LWJGL3 backend（见 `lwjgl3/build.gradle`）
**Storage**: N/A
**Testing**: JUnit 5（见 `core/build.gradle`）
**Target Platform**: Desktop（LWJGL3：Windows/macOS/Linux）
**Project Type**: multi-module Gradle（`core/shared/client/server/lwjgl3`）
**Performance Goals**: 游戏稳定在 60 FPS；F3 调试工具的开启/关闭对帧率影响 < 5%（Spec: SC-4）。
**Constraints**: 在星系级坐标范围下，近景渲染与拾取需保持精度（通过分层坐标与渲染局部化）。
**Scale/Scope**: 坐标系统需支持整个星系尺度（大数值范围 + 近景高精度）。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. 模块化与可维护性 | ✅ PASS | `CoordinateService` 的设计符合模块化原则。 |
| II. 架构分层与端侧分离 | ✅ PASS | `CoordinateService` (逻辑) 与 `DebugOverlay` (UI) / `DebugRenderer` (视觉) 分离。 |
| III. 规范化命名与注释 | ✅ PASS | 遵循。本计划及后续实现将使用中文文档/注释和英文代码标识符。 |
| IV. 扩展性与 Mod 支持 | ✅ PASS | `CoordinateService` 作为基础服务，具备良好的扩展潜力。 |
| V. 游戏模拟驱动 | ✅ PASS | 坐标计算独立于帧率。 |
| VI. UI 层独立性 | ✅ PASS | `DebugOverlay` 仅作为数据展示，与核心逻辑解耦。 |
| VII. 多核性能优化 | ✅ PASS | 当前功能不涉及高并发瓶颈，但 `CoordinateService` 的设计应是线程安全的，以便未来在多线程环境（如多线程渲染/物理）中使用。 |

## Project Structure

### Documentation (this feature)

```text
specs/014-coordinate-system-scale/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
# Multi-module（与仓库现状一致）
core/src/main/java/           # 纯逻辑（禁止 graphics 依赖）
client/src/main/java/         # 客户端表现层（含渲染、UI、输入桥接）
shared/src/main/java/         # 共享数据结构/协议
lwjgl3/src/main/java/         # 桌面启动器（LWJGL3 backend）

core/src/test/java/
client/src/test/java/
```

**Structure Decision**: 采用仓库既有的 multi-module 分层：
- `core`：坐标/比例尺的核心数据与算法（不依赖 `com.badlogic.gdx.graphics`，符合宪章“端侧分离/核心无图形依赖”）。
- `client`：F3 DebugOverlay（UI）与世界空间坐标轴/网格渲染（ShapeRenderer/线段批次），以及输入监听与调试开关。
- `shared`：必要的公共 DTO/常量（如单位换算常量可共享）。
- `lwjgl3`：运行入口。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| (无) | - | - |
