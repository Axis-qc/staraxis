# Implementation Plan: 星系与世界规模系统完善

**Branch**: `010-galaxy-world-scaling` | **Date**: 2026-01-06 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/010-galaxy-world-scaling/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

在现有星系生成和世界生成系统基础上，完善星系规模配置系统（预设档位+自定义范围）、世界六边形区块规模配置系统（预设档位+自定义范围）、星系与区块协调机制、行星轨道系统完善（轨道参数扩展、位置计算、周期计算、多体系统支持、基于物理约束的稳定性检查）、配置验证机制（数值范围+性能阈值）和性能保护机制。

## Technical Context

**Language/Version**: Java 21（Gradle Toolchain）  
**Primary Dependencies**: libGDX 1.14.0（shared 模块）、Kryo 5.5.0、Jackson 2.16.1（server/lwjgl3 使用）、LWJGL3 backend（客户端）  
**Storage**: N/A（本特性阶段只涉及内存数据结构和配置文件；序列化由现有 Kryo 体系承接）  
**Testing**: JUnit 5.10.0（`:core:test`、`:shared:test`、`:server:test`）  
**Target Platform**: 桌面端（Windows/macOS/Linux 的 LWJGL3 客户端）+ Headless 逻辑（core）+ 独立 server 模块  
**Project Type**: Gradle 多模块项目（core/shared/server/lwjgl3）  
**Performance Goals**: 
- 小型星系配置（10-20 个恒星系统）生成时间 <= 2 秒
- 大型星系配置（100-200 个恒星系统）生成时间 <= 10 秒
- 小型地图配置（50x50 区块）生成时间 <= 1 秒
- 大型地图配置（500x500 区块）生成时间 <= 5 秒
- 保持同平台同版本确定性

**Constraints**: 
- core 模块保持 headless（禁止 graphics 依赖）
- 避免在高频 update/渲染路径产生临时对象
- 配置数据驱动且"新生成时加载"
- 配置验证规则和性能阈值在配置文件中定义

**Scale/Scope**: 
- 星系规模：支持预设档位（小型/中型/大型）和自定义范围（恒星系统数量范围、空间范围）
- 区块规模：支持预设档位（小型/中型/大型）和自定义范围（区块数量、区块大小）
- 轨道系统：支持完善的轨道参数（半长轴、偏心率、倾角、升交点经度、近地点幅角、真近点角）和基于物理约束的稳定性检查

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **模块化与可维护性 (Modularization & Maintainability)**: ✅ 是，避免重复代码；避免硬编码/硬枚举；配置数据驱动；预设档位和自定义范围分离设计
- **架构分层与端侧分离 (Layered Architecture & C/S Separation)**: ✅ 是，逻辑层不包含 UI 条件；客户端只负责渲染与输入；规模配置和验证在 core 层
- **规范化命名与注释 (Naming & Documentation)**: ✅ 是，变量/方法/字段命名后带职责括号说明；文件头包含标准注释块；注释为中文
- **扩展性与 Mod 支持 (Extensibility & Mod Support)**: ✅ 是，配置系统支持扩展；预留扩展接口；避免阻塞未来 Mod 接入
- **游戏模拟驱动 (Simulation-Driven Logic)**: ✅ 是，逻辑基于模拟时间而非帧率；避免每帧非必要逻辑
- **UI 层独立性 (Independent UI Layer)**: ✅ 是，规模配置和验证在 core 层，UI 层仅负责显示和输入转发
- **版本控制与合并纪律 (Version Control & Merge Discipline)**: ✅ 是，顶层架构入库；在完成实现+测试+文档对齐后再 push；在质量门禁通过后再合并

**Post-Design Re-check (after Phase 1)**: ✅ 通过（Phase 1 产物仅引入数据模型/契约/快速验证文档，未触碰 UI 逻辑与渲染耦合；核心约束与分层仍满足宪章；规模配置和验证在 core 层，UI 层仅负责显示和输入转发）。

## Project Structure

### Documentation (this feature)

```text
specs/010-galaxy-world-scaling/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
assets/
  i18n/
    galaxy-scale-config.properties    # 星系规模配置（预设档位定义）
    world-block-scale-config.properties  # 区块规模配置（预设档位定义）
    scale-validation-config.properties    # 规模验证配置（数值范围+性能阈值）
core/
  src/main/java/com/staraxis/game/core/world/
    DefaultWorldGenerator.java         # 扩展：支持规模配置和协调机制
    scale/
      GalaxyScaleConfigLoader.java     # 星系规模配置加载器
      WorldBlockScaleConfigLoader.java # 区块规模配置加载器
      ScaleConfigValidator.java        # 规模配置验证器
      GalaxyBlockCoordinator.java      # 星系与区块协调器
  src/main/java/com/staraxis/game/core/world/stellar/
    StellarGenerator.java             # 扩展：支持规模配置
    orbit/
      OrbitCalculator.java            # 轨道计算器（位置、周期）
      OrbitStabilityChecker.java      # 轨道稳定性检查器（基于物理约束）
  src/test/java/com/staraxis/game/core/world/
    scale/
      GalaxyScaleConfigLoaderTest.java
      WorldBlockScaleConfigLoaderTest.java
      ScaleConfigValidatorTest.java
      GalaxyBlockCoordinatorTest.java
    stellar/orbit/
      OrbitCalculatorTest.java
      OrbitStabilityCheckerTest.java
shared/
  src/main/java/com/staraxis/game/shared/world/
    WorldGenConfig.java               # 扩展：添加规模配置字段
    scale/
      GalaxyScaleConfig.java          # 星系规模配置数据模型
      WorldBlockScaleConfig.java      # 区块规模配置数据模型
      ScalePreset.java                # 规模预设档位定义
  src/main/java/com/staraxis/game/shared/world/stellar/orbit/
    Orbit.java                        # 扩展：添加完整轨道参数
server/
  src/main/java/com/staraxis/game/server/
    (无新增，使用 core 层逻辑)
lwjgl3/
  src/main/java/io/staraxis/lwjgl3/
    (无新增，UI 层仅消费配置和生成结果)
```

**Structure Decision**: 采用 Gradle 多模块结构，核心规模配置、验证和协调机制放在 `core`；可共享的数据模型放在 `shared`；配置数据驱动，存储在 `assets` 目录下的 properties 文件；`lwjgl3` 负责桌面端渲染与输入；`server` 负责独立服务端运行入口。规模配置系统、验证机制和协调逻辑优先落在 `core/shared`，保证 headless 可测试。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 无 | 无 | 无 |
