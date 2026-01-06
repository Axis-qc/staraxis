<!--
Sync Impact Report:
- Version change: 1.2.0 -> 1.3.0
- List of modified principles:
  - 开发工作流 (Development Workflow) -> 版本控制与合并纪律 (Version Control & Merge Discipline)
- Added sections: None
- Templates requiring updates:
  -  updated: .specify/memory/constitution.md
  -  updated: .specify/templates/plan-template.md
  -  updated: .specify/templates/spec-template.md
  -  updated: .specify/templates/tasks-template.md
- Follow-up TODOs:
  - TODO(RATIFICATION_DATE): 初始确立日期需确认，暂定为 2026-01-05。
-->
# StarAxis Constitution

## Core Principles

### I. 模块化与可维护性 (Modularization & Maintainability)
所有功能必须以模块化方式设计。在创建新方法或字段前，必须先检索现有代码库以避免重复。禁止硬编码 (Hardcoding) 和硬枚举 (Hard Enums)，所有配置应当参数化或数据驱动，以方便未来的修改与维护。

### II. 架构分层与端侧分离 (Layered Architecture & C/S Separation)
系统架构必须严格遵循分层原则。服务端负责核心逻辑、状态模拟和数值演算；客户端仅负责视觉表现和用户输入转发。严禁在游戏逻辑层（Core Logic）中直接构建 UI 相关条件或状态。逻辑层应当通过接口或事件向外暴露状态，由 UI 层监听并决定如何显示。

### III. 规范化命名与注释 (Standardized Naming & Documentation)
代码可读性优于简洁性，规则统一且可执行：
- **语言约定**：所有回复、说明、文档、技术交流与代码注释必须使用简体中文。
- **标识符约定**：代码中的标识符保持英文；禁止使用拼音命名；错误信息、日志内容允许为英文。
- **命名约定**：变量/函数使用 camelCase；类/接口/组件使用 PascalCase；常量使用 UPPER_SNAKE_CASE；文件/文件夹使用 kebab-case；命名需语义清晰，禁止随意缩写。
  - **示例**：变量 `playerId`；函数 `loadSaveGame()`；类 `FleetController`；接口 `ShipRepository`；组件 `MainMenuPanel`；常量 `MAX_SHIP_COUNT`；文件夹 `combat-simulator/`；文件 `fleet-controller.java`。
- **注释约定**：注释用于解释“为什么这样设计”，而不是代码字面含义；复杂逻辑、业务判断、边界条件必须写注释；禁止无意义注释；统一使用 `TODO` / `FIXME` / `NOTE` / `HACK` 标记。
注释以中文为主，必要术语可附英文

### IV. 扩展性与 Mod 支持 (Extensibility & Mod Support)
架构设计必须预留扩展接口。虽然当前不要求兼容旧版本，但必须在底层设计中考虑 Mod 加载机制和 API 暴露，确保游戏生态的长期生命力。

### V. 游戏模拟驱动 (Simulation-Driven Logic)
游戏逻辑必须严格遵循游戏模拟时间，而非依赖帧率 (FPS)。尽量减少在 `update` (每帧更新) 方法中编写非必要逻辑，以优化性能并确保多端同步的一致性。

### VI. UI 层独立性 (Independent UI Layer)
UI 层必须作为一个独立的层级存在，通过特定的 UI 模型（UI Model）或数据绑定与游戏逻辑解耦。UI 逻辑不应包含任何游戏规则的计算，仅负责响应数据变化并更新视图组件。所有 UI 组件必须具备高度可重用性，且支持本地化文本系统。

## 技术标准与约定 (Technical Standards & Conventions)

### 坐标系与轴向
- **方向**：0 向右，90 向上，180/-180 向左，270 向下。
- **轴向**：X 轴向右为正，Y 轴向上为正。

### 人口与资源单位
- **存储单位**：人口相关字段统一以百万人为单位（1 个体 = 0.000001 百万人）。
- **显示规范**：UI 层面需通过辅助函数动态转换为 K/M/G 格式，保持视觉上的直觉性。

### 性能与资源管理（游戏开发）
- **避免帧内分配**：在高频 `update` / 渲染路径中避免创建临时对象与隐式装箱；必要时使用对象池或复用容器。
- **资源加载可控**：资源加载必须可追踪、可释放、可复现；避免散落式的直接 IO/硬路径读取，优先通过统一的资源/资产管理入口（例如 AssetManager 或等价封装）。
- **数据驱动优先**：数值、平衡、文本、本地化与可配置内容应数据化（文件/表/配置）而非硬编码，便于调参与 Mod 扩展。

## 开发工作流 (Development Workflow)

### 修改原则与质量门禁
- **计划先行**：所有修改必须先制定详细的修改计划，严禁在未经允许的情况下进行大范围或跨模块修改。
- **命令限制**：严禁在非测试场景以及未指定使用终端下使用终端命令。所有非测试性的环境变更、构建触发或资源处理必须优先通过 Gradle 任务或专门的自动化脚本完成，确保操作的可审计性和幂等性。
- **职责隔离**：禁止对当前分配任务外的其他模块进行随意变动，确保变更的可追溯性和局部性。

### 版本控制与合并纪律 (Version Control & Merge Discipline)
- **顶层架构入库**：顶层架构/整体设计文档必须纳入 Git 并随架构变更同步更新（避免只存在于个人笔记或聊天记录）。
- **上传时机**：默认在当前任务/用户故事全部完成（实现 + 测试 + 文档对齐）后再统一上传（push），避免在未完成阶段频繁推送造成主干噪音。
- **合并时机**：合并分支必须发生在工作完成之后（任务清单完成、测试通过、必要的手工验证完成）。禁止在未达成质量门禁时合并到主分支。
- **例外流程**：若确需中途上传用于协作/备份，必须显式标注 WIP（例如 commit message/PR 标题包含 WIP），且不得合并。

## Governance

本宪章是 StarAxis 项目的最高开发准则，所有代码提交、方案评审及任务拆解均须遵循上述原则。原则的修改需经过文档化记录并更新版本号。

**Version**: 1.3.0 | **Ratified**: 2026-01-05 | **Last Amended**: 2026-01-06
