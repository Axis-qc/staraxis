# Implementation Plan: 015 - 真实比例六边形星区与内容分配

**Branch**: `015-realistic-galaxy-sector-gen` | **Date**: 2026-01-08 | **Spec**: `specs/015-realistic-galaxy-sector-gen/spec.md`
**Input**: Feature specification from `specs/015-realistic-galaxy-sector-gen/spec.md`

## Summary

本特性在“开局生成”阶段，为星图生成一个真实比例（六边形边长=1光年）的六边形星区网格，并按“预设优先 → 剩余星区按比例分配内容类型 → 生成对应内容占位数据”的顺序完成内容分配。

关键决策：
- 星系大小参数为六边形半径 R（圈数），总星区数 = `1 + 3R(R+1)`。
- 内容类型本期只包含：恒星系 / 星云 / 深空；但必须数据驱动可扩展。
- 内容类型使用字符串 ID（例如 `star-system` / `nebula` / `deep_space`），并由 JSON/数据表注册。
- 预设（GalaxyPreset）来源为 JSON（支持随游戏/Mod 加载）。
- 分配必须确定性：按 HexCoord 排序 + `seed + HexCoord` 派生随机，避免遍历顺序/并行导致结果变化。
- 渲染/拾取消费 shared 层只读快照（UniverseSnapshot/SectorSnapshot 等），客户端通过 adapter 转为渲染模型，满足 C/S 分离。

## Technical Context

**Language/Version**: Java（Gradle 多模块：`shared/` + `lwjgl3/`）
**Primary Dependencies**: LWJGL3（客户端渲染/输入），shared 内使用 Kryo（已有 KryoSerializer），日志 logback
**Storage**: N/A（本特性仅开局生成数据结构；持久化/存档不在本计划范围）
**Testing**: JUnit（shared 模块已有 test 结构），必要时补充基准（JMH 目录已存在）
**Target Platform**: 桌面端（Windows 为主，Gradle 可跨平台）
**Project Type**: 多模块游戏项目（shared：逻辑/数据/生成；lwjgl3：客户端渲染与调试 UI）
**Performance Goals**: 默认配置下开局生成 + 进入星图界面 ≤ 3 秒（SC-001）
**Constraints**:
- 严格 C/S 分离：生成与快照在 shared；lwjgl3 仅做适配与渲染
- 数据驱动优先：内容类型注册、预设定义使用 JSON
- 可复现：同 seed+配置必须完全一致（SC-004）
**Scale/Scope**:
- 星区数 N = `1 + 3R(R+1)`
- 典型验收规模：N ≥ 200 时分配比例偏差 ≤ ±10%（SC-003）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- ✅ 全中文：计划/文档将保持中文；代码标识符英文。
- ✅ 严格 C/S 分离：生成、快照在 shared；lwjgl3 仅消费快照并渲染。
- ✅ 范围硬约束：仅实现 015 spec 定义内容（星区网格、分配、占位符、快照接口、调试验收）。
- ✅ 数据驱动：内容类型与预设走 JSON/数据表；避免硬枚举。
- ✅ 计划先行：本 plan 产出 research/data-model/contracts/quickstart 后再 tasks/implement。
- ⚠️ 多核性能优化：本特性可并行生成，但因“按 HexCoord 派生随机”已保证顺序无关；并行实现是否必要将放入 research 与后续可选项（若不并行也可满足 3 秒目标，则不强制）。

## Project Structure

### Documentation (this feature)

```text
specs/015-realistic-galaxy-sector-gen/
├── plan.md              # 本文件
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/           # Phase 1
└── tasks.md             # Phase 2 (/speckit.tasks 生成)
```

### Source Code (repository root)

```text
shared/
├── src/main/java/
│   ├── com/staraxis/universegen/...
│   ├── com/staraxis/game/shared/net/worldgen/snapshot/...
│   └── ...
└── src/test/java/

lwjgl3/
└── src/main/java/com/staraxis/game/client/
    ├── world/...
    └── ui/view/debug/...
```

**Structure Decision**: 继续沿用既有结构：
- shared：宇宙生成（universegen）+ 快照（game.shared.net.worldgen.snapshot）
- lwjgl3：只做快照到渲染模型适配（client/world）与调试显示（client/ui/view/debug）

## Phase 0: Outline & Research (research.md)

本阶段目标：将 plan 中仍需明确、会影响实现正确性的决策固化为结论，避免在实现期反复调整。

研究主题（需要产出“决策/理由/备选方案”）：
1. 1ly 到世界单位（km）的换算与 014 坐标/比例尺体系如何精确对齐（确保 SC-002 可测）。
2. HexCoord 排序的规范（字典序？环序？）与稳定性要求（确保跨 JVM/平台一致）。
3. `seed + HexCoord` 派生随机的实现方式（哈希/分裂 RNG），要求：
   - 可复现
   - 与并发无关
   - 低碰撞/均匀
4. 分配算法：在满足比例（SC-003）的同时，保持确定性与可解释性（例如“配额 + 坐标派生随机打分排序”）。
5. JSON 数据驱动：
   - 内容类型注册表 schema（typeId、显示名、渲染颜色/图标 key 等）
   - GalaxyPreset schema（占用规则：固定 HexCoord / 随机 HexCoord；冲突覆盖顺序；与 seed 的关系）
6. 快照结构选择：优先复用现有 `UniverseSnapshot/SectorSnapshot`，确认字段是否足够承载：
   - HexCoord
   - 世界坐标（或可由客户端按规则计算？）
   - contentTypeId
   - starSystemId（占位）

## Phase 1: Design & Contracts

### 1) Data Model (data-model.md)

从 spec 提取并固化实体、字段、约束与关系：
- Galaxy
- Sector（含 HexCoord、worldCenter、contentTypeId、占用来源/预设 id 可选）
- 内容类型注册表（SectorContentTypeDefinition）
- GalaxyPreset（JSON schema）
- UniverseSnapshot / SectorSnapshot（shared DTO）

需要特别写清：
- 唯一性：SectorId 生成规则；starSystemId 生成规则（占位）
- worldCenter 计算：HexCoord → 世界坐标（XY 平面，Z=0）

### 2) Contracts (contracts/)

本项目不是 Web API；contracts 用“模块接口契约”表达（Markdown/伪代码即可），重点是：
- shared：生成入口与返回值（UniverseSnapshot）
- lwjgl3：快照适配接口（UniverseModelToWorldMapAdapter 等）

建议输出：
- `contracts/worldgen.md`：世界生成契约（输入：GameStartSettings + seed + presets + contentTypeRegistry；输出：UniverseSnapshot）
- `contracts/snapshots.md`：快照字段与版本策略（本期无需向后兼容，但需可扩展）

### 3) Quickstart (quickstart.md)

提供最小可运行/可验收路径：
1. 如何配置开局参数（R、比例、seed、预设 JSON）
2. 如何运行客户端并打开 F3 调试
3. 如何验证：
   - 星区数与 R 匹配
   - 相邻中心距离误差 ≤ 1%（SC-002）
   - N≥200 时比例偏差 ≤±10%（SC-003）

### 4) Agent context update

运行：
- `.specify/scripts/powershell/update-agent-context.ps1 -AgentType cursor-agent`

## Phase 2: Planning Stop Point

本命令在完成 Phase 1 设计产物后停止。下一步使用：
- `/speckit.tasks @specs/015-realistic-galaxy-sector-gen/plan.md`

