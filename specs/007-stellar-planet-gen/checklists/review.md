# Requirements Checklist: 007 Stellar/Planet (PR Review)

**Purpose**: Unit tests for requirements writing (PR review gate) — validate spec clarity/completeness/consistency before implementation
**Created**: 2026-01-06
**Feature**: [Link to spec.md](../spec.md)
**Focus**:
- 生成规则与确定性（Seed/统计/可复现）
- 数据模型与扩展性（数据驱动、避免硬编码/硬枚举、为 Mod 预留）
- 要求明确 Out-of-scope

## Requirement Completeness

- [x] **CHK001**: 是否为 `WorldGenConfig` 的每个字段都定义了“含义 + 合法范围 + 默认值/空值处理”？[Completeness] [Spec §Requirements/FR-002]
- [x] **CHK002**: 是否明确 `StarDensity` 影响的是“星系区块占比/数量”还是“星系区块内恒星数量”，并避免两者混用？[Completeness] [Spec §Requirements/FR-004]
- [x] **CHK003**: 是否明确 `NebulaRatio` 与其它区块类型（星系/深空）之间的分配关系（例如是否三者权重归一/剩余分配）？[Completeness] [Spec §Requirements/FR-002]
- [x] **CHK004**: 是否明确 `PlanetComplexity` 的可观察效果（例如影响每颗恒星行星数的均值/分布/上限）？[Completeness] [Spec §Requirements/FR-005]
- [x] **CHK005**: 是否明确“星系类区块”的判定规则（例如 `SectorType == galaxy` 才允许生成 `StarSystem`）？[Completeness] [Spec §Requirements/FR-003]
- [x] **CHK006**: 是否明确多恒星系统（1..3）在生成时的分布规则（例如概率/权重），而不仅仅是范围？[Completeness] [Spec §Requirements/FR-004]
- [x] **CHK007**: 是否明确行星归属到恒星的规则细节（例如是否允许某些恒星 0 行星、是否允许所有行星都落在主星）？[Completeness] [Spec §Requirements/FR-005]
- [x] **CHK008**: 是否明确“统计与回归验证支持”需要输出的**最小统计集合**（字段名/口径）以便实现与测试对齐？[Completeness] [Spec §Requirements/FR-008]

## Requirement Clarity

- [x] **CHK009**: 规格中所有“显著/可预期/合理/清晰”等形容词是否都能映射到可量化或可判定的条件？[Clarity] [Spec §User Story 3, §FR-006]
- [x] **CHK010**: “可验证的上下限”是否被具体化为数值或可由配置推导的阈值（例如默认地图预设下恒星总数范围）？[Clarity] [Spec §FR-004]
- [x] **CHK011**: “同一地图大小下可对比”是否定义了对比方法（计数口径、采样方式、阈值判定）？[Clarity] [Spec §User Story 2]
- [x] **CHK012**: `Seed` 的“字符串到数值”的确定性规则是否被明确（例如同字符串必映射到同 `seedValue`）？[Clarity] [Spec §FR-007, §User Story 4]

## Requirement Consistency

- [x] **CHK013**: `spec.md` 中的 Key Entities 与 `data-model.md` 的实体/字段是否一致（命名、关系、约束如 1..3 恒星、行星归属）？[Consistency] [Spec §Key Entities + Data Model]
- [x] **CHK014**: 性能目标（生成 <300ms、60FPS）在 `spec.md` 与 `plan.md` 是否一致且无冲突？[Consistency] [Spec §Success Criteria + Plan §Technical Context]
- [x] **CHK015**: “封闭边界/半径 R”是否在 spec、data model、contracts 里口径一致（不会出现矩形/无限世界描述混入）？[Consistency] [Spec §Clarifications + Data Model + Contracts]

## Acceptance Criteria Quality

- [x] **CHK016**: 每个 User Story 的 Acceptance Scenarios 是否都能在不引入实现细节的情况下被验证（输入/输出可观察）？[Measurability] [Spec §User Scenarios]
- [x] **CHK017**: `SC-003`（3 次生成统计一致）是否定义了“关键统计指标”的具体列表与比较规则（全等/允许浮动）？[Measurability] [Spec §Success Criteria]
- [x] **CHK018**: `SC-004`（修改任意参数能体现变化）是否定义了“体现变化”的判定方式（变化方向/阈值）？[Measurability] [Spec §Success Criteria]

## Scenario Coverage

- [x] **CHK019**: 是否覆盖了“Seed 为空时”的用户流程与验收口径（是否回显 seedValue、是否可复制复现）？[Coverage] [Spec §FR-002 + §User Story 1]
- [x] **CHK020**: 是否覆盖了“参数被 clamp/自动修正”的用户可见行为（提示/回退）？[Coverage] [Spec §Edge Cases]
- [x] **CHK021**: 是否覆盖了“生成耗时较长”的加载态/用户反馈（至少写清楚是否要求）？[Coverage] [Gap] [Spec §User Scenarios]

## Edge Case Coverage

- [x] **CHK022**: 是否明确“极端密度”（StarDensity≈0/1）时的最小多样性规则的判定口径（至少哪两类、如何保证）？[Edge Cases] [Spec §Edge Cases]
- [x] **CHK023**: 是否明确“超大地图”时哪些部分必须降级/裁剪（仅需求层面描述即可）？[Edge Cases] [Spec §Edge Cases]
- [x] **CHK024**: 是否明确“多恒星系统”与“无行星恒星”的边界行为（允许/禁止）以避免实现分歧？[Edge Cases] [Spec §FR-004/FR-005]

## Non-Functional Requirements

- [x] **CHK025**: 是否明确生成性能指标的测量口径（触发点、计时范围、默认地图预设）？[Non-Functional] [Spec §SC-001]
- [x] **CHK026**: 是否明确渲染性能指标的测量口径（分辨率、场景负载、观察方式）？[Non-Functional] [Spec §SC-002]
- [x] **CHK027**: 是否明确“俯视可读”的最低要求（例如网格边界在所有缩放级别都必须可见；高亮不得被特效遮挡）并避免主观描述？[Non-Functional] [Spec §FR-006]

## Dependencies & Assumptions

- [x] **CHK028**: 是否明确复用既有六边形坐标/世界生成基础设施的依赖点（哪些能力必须已存在）？[Dependencies] [Spec §Assumptions]
- [x] **CHK029**: 是否明确数据驱动来源（预设/类型表）在需求层面的要求（例如“必须可配置/可扩展”，避免硬编码/硬枚举）？[Dependencies] [Spec §Assumptions + Constitution]

## Ambiguities & Conflicts

- [x] **CHK030**: 是否提供明确的 **Out-of-scope** 清单（本期不做：例如系统重心轨道、无限地图、玩法系统、联机同步等），并与计划/研究文档一致？[Clarity] [Gap] [Spec §Scope]
- [x] **CHK031**: 是否消除“可能/可选/后续再定”等会影响验收的表述，或将其转化为明确的需求（必须/不需要）？[Clarity] [Spec §Requirements + §Success Criteria]
