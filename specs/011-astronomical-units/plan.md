# Implementation Plan: 真实天文单位系统重构

**Branch**: `011-astronomical-units` | **Date**: 2026-01-07 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/011-astronomical-units/spec.md`

## Summary

本功能将重构游戏的单位系统，使用真实天文单位（AU）作为基础单位，并基于真实宇宙比例重新定义星区、轨道、恒星和行星的大小。核心技术方案包括：使用定点数（Fixed-point）表示天文单位以确保确定性，提供完全迁移机制将现有数据转换为新单位系统，实现自动缩放+手动调整的可视化缩放机制。

## Technical Context

**Language/Version**: Java (与项目现有代码保持一致，使用 Java 标准库)  
**Primary Dependencies**: 
- libGDX（现有游戏框架）
- JUnit 5（测试框架，项目标准）
- Java 标准库（数学计算、文件 I/O）

**Storage**: 
- 配置文件：`assets/i18n/` 目录下的 properties 文件
- 游戏数据：现有存档格式（需要迁移）

**Testing**: JUnit 5（单元测试、集成测试）  
**Target Platform**: 桌面平台（LWJGL3），与现有项目保持一致  
**Project Type**: 游戏项目（Gradle 多模块：core, shared, lwjgl3, server）  
**Performance Goals**: 
- 单位转换计算：< 1ms（单次转换）
- 迁移工具：批量转换 1000 个存档 < 10 秒
- 渲染性能：保持现有 60 FPS 目标

**Constraints**: 
- 必须保持确定性（相同输入产生相同输出）
- 必须向后兼容现有数据（通过迁移工具）
- 必须遵循项目架构分层（C/S 分离、UI 层独立）
- 禁止硬编码，所有配置必须数据驱动

**Scale/Scope**: 
- 重构现有所有距离和大小定义
- 支持星区、轨道、恒星、行星四种实体的大小定义
- 支持 AU、光年、秒差距三种单位转换
- 迁移工具需要处理所有现有游戏数据

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. 模块化与可维护性 (Modularization & Maintainability)
- ✅ **检查通过**：所有单位系统、转换器、配置加载器将作为独立模块实现
- ✅ **检查通过**：在创建新方法前将检索现有代码库（如 `UnitConverter`、`WorldGenDefinitions`）
- ✅ **检查通过**：所有配置参数化，通过 properties 文件定义，禁止硬编码

### II. 架构分层与端侧分离 (Layered Architecture & C/S Separation)
- ✅ **检查通过**：单位系统位于 `shared` 模块（数据模型）和 `core` 模块（逻辑）
- ✅ **检查通过**：渲染转换位于 `lwjgl3` 模块（客户端），逻辑层不包含 UI 相关代码
- ✅ **检查通过**：可视化缩放通过 UI 层实现，逻辑层仅提供数据接口

### III. 规范化命名与注释 (Standardized Naming & Documentation)
- ✅ **检查通过**：所有代码注释使用简体中文
- ✅ **检查通过**：标识符使用英文（camelCase/PascalCase/UPPER_SNAKE_CASE）
- ✅ **检查通过**：文件/文件夹使用 kebab-case
- ✅ **检查通过**：复杂逻辑和业务判断将添加中文注释

### IV. 扩展性与 Mod 支持 (Extensibility & Mod Support)
- ✅ **检查通过**：配置系统设计为可扩展，支持通过配置文件添加新的单位类型或大小预设
- ✅ **检查通过**：单位转换器设计为可插拔，便于未来扩展

### V. 游戏模拟驱动 (Simulation-Driven Logic)
- ✅ **检查通过**：单位转换和大小计算不依赖帧率，基于游戏模拟时间
- ✅ **检查通过**：避免在渲染循环中进行复杂计算

### VI. UI 层独立性 (Independent UI Layer)
- ✅ **检查通过**：可视化缩放机制位于 UI 层，通过数据绑定获取逻辑层数据
- ✅ **检查通过**：UI 层不包含游戏规则计算，仅负责显示和用户交互

### 开发工作流检查
- ✅ **检查通过**：本计划文档已创建，符合"计划先行"原则
- ✅ **检查通过**：所有修改将通过 Gradle 任务或脚本完成，不使用终端命令
- ✅ **检查通过**：仅修改与天文单位系统相关的模块，不涉及其他模块

### 版本控制与合并纪律
- ✅ **检查通过**：本计划文档将纳入 Git
- ✅ **检查通过**：将在任务完成后再统一上传

**Gate Status**: ✅ **PASSED** - 所有宪法检查通过，可以进入 Phase 0 研究阶段

## Project Structure

### Documentation (this feature)

```text
specs/011-astronomical-units/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
shared/src/main/java/com/staraxis/game/shared/world/astronomical/
├── AstronomicalUnit.java              # 天文单位基础类（定点数表示）
├── UnitConverter.java                 # 单位转换器（AU/光年/秒差距）
├── SectorSizeDefinition.java          # 星区大小定义
├── OrbitSizeDefinition.java           # 轨道大小定义
├── StarSizeDefinition.java            # 恒星大小定义
├── PlanetSizeDefinition.java          # 行星大小定义
└── VisualScaleConfig.java             # 可视化缩放配置

core/src/main/java/com/staraxis/game/core/world/astronomical/
├── AstronomicalUnitSystem.java        # 天文单位系统（配置加载、初始化）
├── SizePresetLoader.java              # 大小预设加载器
└── MigrationTool.java                 # 数据迁移工具

core/src/test/java/com/staraxis/game/core/world/astronomical/
├── AstronomicalUnitTest.java          # 天文单位测试
├── UnitConverterTest.java             # 单位转换器测试
├── SizeDefinitionTest.java            # 大小定义测试
└── MigrationToolTest.java             # 迁移工具测试

lwjgl3/src/main/java/com/staraxis/game/client/ui/view/astronomical/
└── AstronomicalScaleRenderer.java     # 天文单位渲染转换器

assets/i18n/
├── astronomical-units-config.properties    # 天文单位系统配置
├── sector-size-config.properties           # 星区大小配置
├── orbit-size-config.properties            # 轨道大小配置
├── star-size-config.properties             # 恒星大小配置
├── planet-size-config.properties           # 行星大小配置
└── visual-scale-config.properties          # 可视化缩放配置
```

**Structure Decision**: 采用 Gradle 多模块结构，与现有项目保持一致。单位系统核心类位于 `shared` 模块（数据模型），逻辑处理位于 `core` 模块，渲染转换位于 `lwjgl3` 模块（客户端）。所有配置通过 properties 文件数据驱动，符合项目规范。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无违反项，所有设计均符合宪法要求。

---

## Phase 0 & Phase 1 Completion

### Phase 0: Research ✅

**完成日期**: 2026-01-07

**生成文档**: `research.md`

**研究内容**:
- ✅ 定点数实现方案（使用 long + 缩放因子）
- ✅ 单位转换精度和舍入策略
- ✅ 数据迁移工具设计
- ✅ 可视化缩放机制实现
- ✅ 配置系统设计
- ✅ 逻辑单位到渲染单位的转换比例

**所有 NEEDS CLARIFICATION 已解决**

### Phase 1: Design & Contracts ✅

**完成日期**: 2026-01-07

**生成文档**:
- ✅ `data-model.md` - 数据模型定义
- ✅ `contracts/astronomical-units-api.md` - 内部 API 合约
- ✅ `quickstart.md` - 快速开始指南

**设计内容**:
- ✅ 所有实体定义（AstronomicalUnit, UnitConverter, SizeDefinitions 等）
- ✅ 数据模型关系和验证规则
- ✅ API 契约和错误处理
- ✅ 集成点和状态转换

### Constitution Check Re-evaluation (Post-Design)

**重新检查结果**: ✅ **PASSED**

所有设计均符合宪法要求：
- ✅ 模块化设计：所有类独立，职责清晰
- ✅ 架构分层：逻辑在 core/shared，渲染在 lwjgl3
- ✅ 命名规范：遵循项目命名约定
- ✅ 数据驱动：所有配置通过 properties 文件
- ✅ 无硬编码：所有值可配置
- ✅ UI 层独立：可视化缩放在 UI 层实现

**Gate Status**: ✅ **PASSED** - 可以进入 Phase 2（任务分解）
