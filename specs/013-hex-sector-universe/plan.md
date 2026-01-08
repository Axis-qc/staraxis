# Implementation Plan: 013 - 真实宇宙生成与六边形网格集成

**Branch**: `013-hex-sector-universe` | **Date**: 2026-01-08 | **Spec**: [spec.md](./spec.md)

## Summary

将 `universegen` 真实星系生成模块接入六边形星区（Sector）网格系统，替换现有占位符生成器。每个六边形星区最多包含 1 个恒星系（允许单/双/三恒星），或为星云/深空。开局配置提供“恒星系/星云/深空”三滑条比例（总和≤1，剩余补深空）。本特性允许对世界快照协议与客户端解析/渲染进行破坏性变更，不要求向后兼容。

## Technical Context

**语言/版本**：Java 21

**主要依赖**：
- 客户端渲染：LibGDX（LWJGL3）
- 序列化：Jackson
- 真实星系生成：`shared/src/main/java/com/staraxis/universegen/**`

**存储**：内存世界模型 + 网络传输“世界快照”（新版协议）

**测试**：JUnit 5（单元/集成），并补充可重复的性能基准与压力测试

**目标平台**：
- 服务端：Java 21（内置 HttpServer）
- 客户端：Desktop（LWJGL3）

**项目类型**：多模块 Gradle 项目（server/core/shared/lwjgl3）

**性能目标**：
- 中等地图：服务端世界生成耗时 ≤ 15 秒（符合 spec SC-3）
- 并行优先：生成阶段显式使用多核并行（符合宪章 VII）

**约束**：
- 六边形网格是抽象星图，不代表真实空间距离。
- “无需向后兼容”：允许破坏性变更快照协议与字段，但必须保证“新游戏 → 进入世界”主流程可用。
- 恒星系内部允许单/双/三恒星系统。

**规模/范围**：
- 覆盖新游戏生成主链路：StartNewGameHandler → StartNewGameUseCase → 新生成器 → 新世界快照。
- 客户端适配：解析新版快照并渲染到 WorldScreen。
- UI 改造：新游戏配置改为“三滑条比例联动”。

## Constitution Check

*GATE: 必须通过宪章检查；若有冲突必须在计划/规范/任务中修正。*

| 宪章条款 | 对齐状态 | 说明 |
|---------|---------|------|
| I. 模块化与可维护性 | ✅ | universegen 与世界快照映射分层；避免硬编码与硬枚举，比例/预设参数化。 |
| II. 架构分层与端侧分离 | ✅ | 服务端负责生成与数据；客户端仅解析快照并渲染。 |
| III. 规范化命名与注释 | ✅ | 文档/注释简体中文；代码标识符英文；复杂逻辑注明“为什么”。 |
| IV. 扩展性与 Mod 支持 | ✅ | 允许覆盖迭代，同时预留参数化与数据驱动入口以便未来 Mod。 |
| V. 游戏模拟驱动 | ✅ | 生成与帧率无关；不把生成塞进渲染 update。 |
| VI. UI 层独立性 | ✅ | UI 只负责收集参数与展示，不包含生成规则计算。 |
| VII. 多核性能优化 | ⚠️ | 需要在实现中提供并行生成 + 基准测试证明（见 tasks：T020/T022）。 |

## Project Structure

### 目标文档结构（本特性）

```text
specs/013-hex-sector-universe/
├── spec.md
├── plan.md
├── tasks.md
```

### 相关源代码目录（仓库根）

```text
server/src/main/java/com/staraxis/game/server/http/
  StartNewGameHandler.java

core/src/main/java/com/staraxis/game/core/worldgen/
  StartNewGameUseCase.java
  (新增) HexSectorUniverseGenerator.java
  (新增) SectorTypeDistributor.java
  (新增/替换) *SnapshotMapper.java

shared/src/main/java/com/staraxis/game/shared/net/worldgen/
  StartNewGameRequest.java
  StartNewGameResponse.java
  SchemaVersions.java

shared/src/main/java/com/staraxis/game/shared/net/worldgen/snapshot/
  (新增/替换) UniverseSnapshot*.java / SectorSnapshot*.java ...

lwjgl3/src/main/java/com/staraxis/game/client/net/
  WorldGenApiClient.java

lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/
  NewGameConfigScreen.java
  WorldScreen.java
```

## Architecture & Data Flow

1) 客户端 NewGameConfigScreen 收集参数（三滑条比例 + 地图尺寸 + 种子 + 行星复杂度等）
2) 客户端调用 `POST /worldgen/startNewGame`
3) 服务端在 StartNewGameUseCase 内：
   - 生成六边形星区网格（全图）
   - 按三项比例决定星区类型
   - 对“恒星系星区”调用 universegen 生成恒星系（允许 1~3 恒星）
   - 组装新版“世界快照”并返回
4) 客户端解析新版快照并渲染

## Key Technical Decisions

### 1) 快照协议与字段变更（破坏性变更允许）

- 允许对 `StartNewGameRequest/Response` 与快照 DTO 做破坏性变更。
- schemaVersion 必须明确反映新版快照。

### 2) 星区类型命名与术语对齐

- 星区类型必须统一三类：恒星系 / 星云 / 深空。
- 若底层仍需 `typeId` 字段，必须在文档与代码中保证其语义等价且一致。

### 3) 并行生成策略（符合宪章 VII）

- 按星区并行：每个星区生成互不共享可变状态。
- 线程池：固定或受控大小，避免无限制创建线程。
- 必须提供基准测试：对比单线程/并行耗时，并记录测试条件。

## Milestones

- M1（MVP）：服务端新生成器 + 新快照协议 + 客户端可进入 WorldScreen（对应 tasks：Phase 2~4）
- M2：新游戏配置三滑条 UI 联动并完整串通（对应 tasks：Phase 5）
- M3：性能/稳定性验证与清理旧路径（对应 tasks：Phase 6）
