# Phase 0 Research: 001-game-design-bible

**Date**: 2026-01-09  
**Spec**: [spec.md](spec.md)

> 目标：把澄清阶段的关键决策固化为“为什么这样选”，并为 Phase 1 的文档拆分与示例产出提供依据。

## Decision 1: 文档交付形态（混合结构）

- **Decision**: 核心总览单文档 + 细节按主题拆分（混合）
- **Rationale**: 总览便于快速对齐；细节拆分利于长期维护与多人协作检索。
- **Alternatives considered**:
  - 单一大文档：检索成本高、冲突多
  - 纯拆分无总览：缺少统一入口与关键结论

## Decision 2: 权威性与冲突解决

- **Decision**: Design Bible 为最新权威；若与输入文档冲突，以 Design Bible 为准并注明差异与来源
- **Rationale**: 确保口径唯一、可追溯，降低实现阶段争议。
- **Alternatives considered**:
  - 永远以输入文档为准：会造成 Design Bible 无法成为统一口径
  - 分层权威：可行但需要额外治理流程，本阶段先简化为“Design Bible 最新优先”

## Decision 3: 数据驱动优先范围

- **Decision**: 首要范围覆盖核心数值 + 世界生成规则 + 文本与本地化
- **Rationale**: 这三类内容迭代频繁，且对 Mod 支持最关键；提前数据化可减少硬编码与返工。
- **Alternatives considered**:
  - 仅数值：世界生成与本地化仍会硬编码
  - 仅世界生成：平衡与内容迭代成本高

## Decision 4: 双坐标系防浮点抖动（写到可照做）

- **Decision**: 在 Design Bible 中给出可执行的双坐标系策略（定义、转换、使用域、rebase 触发、误差验收口径）
- **Rationale**: 世界尺度很大（星区/银河级），坐标抖动会破坏交互与战斗；需要足够明确以避免各模块各做一套。
- **Alternatives considered**:
  - 只写概念：后续实现容易分裂
  - 只写口径不写策略：仍会出现多种不兼容实现

## Decision 5: 时间停止（分域冻结）

- **Decision**: 冻结模拟推进，但 UI/输入/镜头/视觉可继续；提供例外清单与验收场景
- **Rationale**: 满足“正常情况下禁止暂停”原则同时允许技能特例；避免逻辑/表现不同步。
- **Alternatives considered**:
  - 全局冻结：会影响 UI 体验与可操作性
  - 只冻结战斗：口径复杂且容易产生策略层/战斗层不一致

## Decision 6: 经济日结算（写到公式与示例）

- **Decision**: 结算规则写到可复算级别（输入项、流水线顺序、公式、舍入规则、至少 1 个完整示例）
- **Rationale**: 经济系统跨模块（生产/运输/市场/维护），必须避免“同一天不同算法”的不一致。
- **Alternatives considered**:
  - 只写一句日结算：无法指导实现与验收
  - 只写范围不写公式：仍会产生不同实现版本

## Decision 7: 术语一致性治理

- **Decision**: 关键概念指定唯一规范术语 + 英文名/缩写，并列旧称/禁用同义词；首次出现旧称需标注一次，随后统一
- **Rationale**: 避免“星区/星域”等混用造成沟通与实现偏差。
- **Alternatives considered**:
  - 仅术语表无禁用项：正文仍可能持续混用

## Decision 8: 命名规范示例覆盖类别

- **Decision**: 命名示例 ≥ 20 且必须覆盖：坐标、时间、地图层级、经济资源、舰队战斗、UI 模型、配置与常量、事件与状态
- **Rationale**: 避免示例零散、关键领域缺失，保证落地可复用。
- **Alternatives considered**:
  - 只要求数量：容易偏科（只覆盖少数模块）
