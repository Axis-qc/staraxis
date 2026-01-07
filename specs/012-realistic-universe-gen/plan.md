# Implementation Plan: 无缝宇宙生成（4X 大战略）

**Branch**: `012-realistic-universe-gen` | **Date**: 2026-01-07 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/012-realistic-universe-gen/spec.md`

## Summary

本功能将实现在服务器端一次性（或分块惰性）生成银河地图：
1. 先按 `preset-systems.json` 配置生成所有 **预设恒星系**（固定或随机星区坐标）。
2. 再根据 `starDensity` 参数为剩余 `galaxy` 类型星区生成随机 **恒星系**。
3. 客户端通过网络同步只读数据并以 LOD 渲染，无缝缩放至行星表面。

技术路线：复用现有 `GalaxyScaleConfig` 与 `StarSystem` 数据结构，在 `core` 模块新增 `GalaxyGenerator` 服务和 `PresetSystemLoader`，并在 `server` 模块暴露生成 API。采用种子控制确保可复现；通过数据驱动支持 Mod 扩展。

## Technical Context

**Language/Version**: Java 17 (Kotlin 1.9 仅限客户端 UI)
**Primary Dependencies**: libGDX 1.12、Jackson 2.x、JUnit 5、Gradle Kotlin DSL
**Storage**: 内存数据结构（生成后可序列化为二进制/JSON 存档）
**Testing**: JUnit 5 + Testcontainers（如需文件存储）
**Target Platform**: Windows / Linux 桌面；未来 WebAssembly (GWT) 只读
**Project Type**: 多模块 Gradle (core/shared/server/lwjgl3)
**Performance Goals**: 生成 1000 星区银河 ≤ 5 s；缩放过程 > 45 FPS (中端硬件)
**Constraints**: 运行期内存占用 ≤ 2 GB；无第三方闭源库；遵守 LGPL (libGDX)
**Scale/Scope**: 单局存档包含 ≥ 100 k 星区；线程并行度 ≤ CPU 核心数

## Constitution Check

| 宪章原则 | 覆盖情况 |
|----------|----------|
| 模块化与可维护性 | ✅ 生成逻辑封装在 `core.world.generation`，无硬编码常量，数据驱动 |
| 分层架构 & C/S 分离 | ✅ 生成逻辑仅服务器端调用；客户端只渲染 |
| 规范命名与注释 | ✅ 遵守 camelCase / PascalCase；中文注释解释 WHY |
| 扩展性 & Mod 支持 | ✅ 预设系统、概率参数均配置文件化，可热加载 |
| 模拟驱动逻辑 | ✅ 生成阶段独立于帧循环；运行时只读 |
| UI 层独立性 | ✅ 不在此功能涉及 |

> *Gate Passed*: 无违反项，进入 Phase 0。

## Project Structure

### Documentation (this feature)

```text
specs/012-realistic-universe-gen/
├── plan.md              # 本文件
├── research.md          # Phase 0 输出
├── data-model.md        # Phase 1 输出
├── quickstart.md        # Phase 1 输出
├── contracts/           # Phase 1 输出
└── tasks.md             #由 /speckit.tasks 生成
```

### Source Code (repository root)

```text
core/src/main/java/com/staraxis/game/core/world/generation/
├── GalaxyGenerator.java           # 总控入口
├── PresetSystemLoader.java        # 解析 preset-systems.json
├── RandomStarSystemFactory.java   # 随机生成算法
└── sampling/
    ├── StarSampler.java
    ├── PlanetSampler.java
    └── OrbitSampler.java

server/src/main/java/com/staraxis/game/server/api/
└── GalaxyGenerationService.java   # 服务端 API，供 CLI/HTTP 调用

tests/
├── unit/
│   └── generation/
│       ├── GalaxyGeneratorTest.java
│       └── PresetSystemLoaderTest.java
└── integration/
    └── GalaxyGenerationPerformanceTest.java
```

**Structure Decision**: 基于现有多模块 Gradle 项目，在 `core` 模块新增 generation 包；在 `server` 暴露 API；测试均放置于 `tests` 目录下，保持单仓。

## Complexity Tracking

无需额外复杂度，未引入新项目或框架。

---

# Phase 0: Research

所有关键决策已明确，无 NEEDS CLARIFICATION。研究任务集中于算法验证与数据来源：

| 决策 | 理由 | 备选方案 |
|-------|------|-----------|
| 预设系统 JSON 格式 | 简单直观，易于 Mod 编辑 | YAML (多行字符处理麻烦) |
| 行星轨道采样算法采用 log 分布 | 贴合现实行星间距 | 等距或线性分布 |
| 并行生成使用 ForkJoinPool | Java 原生，无外部依赖 | Akka / RxJava (过重) |

详见 [research.md](./research.md)。

---

# Phase 1: Design & Contracts

已在 `data-model.md`、`contracts/galaxy-generation-api.md`、`quickstart.md` 输出设计细节。完成后将再次进行宪章检查。

---

*后续*: 通过 `/speckit.tasks` 生成任务清单并进入实现阶段。