<!--
Sync Impact Report:
- Version change: 0.0.0 → 1.0.0
- List of modified principles:
  - [PRINCIPLE_1_NAME] → I. 模块化与可维护性 (Modularization & Maintainability)
  - [PRINCIPLE_2_NAME] → II. 客户端与服务端分离 (Client-Server Separation)
  - [PRINCIPLE_3_NAME] → III. 规范化命名与注释 (Standardized Naming & Documentation)
  - [PRINCIPLE_4_NAME] → IV. 扩展性与 Mod 支持 (Extensibility & Mod Support)
  - [PRINCIPLE_5_NAME] → V. 游戏模拟驱动 (Simulation-Driven Logic)
- Added sections:
  - 技术标准与约定 (Technical Standards & Conventions)
  - 开发工作流 (Development Workflow)
- Removed sections: None
- Templates requiring updates:
  - ✅ updated: .specify/memory/constitution.md
  - ⚠ pending: .specify/templates/plan-template.md
  - ⚠ pending: .specify/templates/spec-template.md
  - ⚠ pending: .specify/templates/tasks-template.md
- Follow-up TODOs: 
  - TODO(RATIFICATION_DATE): 初始确立日期需确认，暂定为 2026-01-05。
-->

# StarAxis Constitution

## Core Principles

### I. 模块化与可维护性 (Modularization & Maintainability)
所有功能必须以模块化方式设计。在创建新方法或字段前，必须先检索现有代码库以避免重复。禁止硬编码 (Hardcoding) 和硬枚举 (Hard Enums)，所有配置应当参数化或数据驱动，以方便未来的修改与维护。

### II. 客户端与服务端分离 (Client-Server Separation)
系统架构必须严格区分服务端演算与客户端渲染/交互。服务端负责所有核心逻辑、状态模拟和数值演算；客户端仅负责视觉表现和用户输入转发。这一原则是实现未来多人模式及专用服务器的基础，不容妥协。

### III. 规范化命名与注释 (Standardized Naming & Documentation)
代码可读性优于简洁性。所有变量、方法及字段命名后需紧跟括号说明其具体职责。每个文件头必须包含标准注释块，详述文件作用、所依赖的接口及其提供的对外接口功能。所有技术交流与代码注释均使用中文。

### IV. 扩展性与 Mod 支持 (Extensibility & Mod Support)
架构设计必须预留扩展接口。虽然当前不要求兼容旧版本，但必须在底层设计中考虑 Mod 加载机制和 API 暴露，确保游戏生态的长期生命力。

### V. 游戏模拟驱动 (Simulation-Driven Logic)
游戏逻辑必须严格遵循游戏模拟时间，而非依赖帧率 (FPS)。尽量减少在 `update` (每帧更新) 方法中编写非必要逻辑，以优化性能并确保多端同步的一致性。

## 技术标准与约定 (Technical Standards & Conventions)

### 坐标系与轴向
- **方向**：0° 向右，90° 向上，180°/-180° 向左，-90° 下。
- **轴向**：X 轴向右为正，Y 轴向上为正。

### 人口与资源单位
- **存储单位**：人口相关字段统一以“百万人”为单位（1 个体 = 0.000001 百万人）。
- **显示规范**：UI 层面需通过辅助函数动态转换为 个/K/M/G 格式，保持视觉上的直观性。

## 开发工作流 (Development Workflow)

### 修改原则与质量门禁
- **计划先行**：所有修改必须先制定详细的修改计划，严禁在未经允许的情况下进行大范围或跨模块修改。
- **命令限制**：严禁在非测试场景下频繁使用终端命令。
- **职责隔离**：禁止对当前分配任务外的其他模块进行随意变动，确保变更的可追溯性和局部性。

## Governance

本宪章是 StarAxis 项目的最高开发准则，所有代码提交、方案评审及任务拆解均须遵循上述原则。原则的修改需经过文档化记录并更新版本号。

**Version**: 1.0.0 | **Ratified**: 2026-01-05 | **Last Amended**: 2026-01-05
