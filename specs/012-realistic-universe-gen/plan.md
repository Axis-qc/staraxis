# Implementation Plan: 012 真实比例宇宙生成（Real-Scale Universe Generation）

**Branch**: `012-realistic-universe-gen` | **Date**: 2026-01-08 | **Spec**: [/specs/012-realistic-universe-gen/spec.md](spec.md)
**Input**: Feature specification from `/specs/012-realistic-universe-gen/spec.md`

## Summary

本功能在客户端和服务器端生成并渲染符合真实天文学尺度的银河 → 星区 → 恒星系 → 行星/卫星数据，并保证在最大缩放时 1px = 1km。核心工作包括：
1. 依照「分层坐标 `{sectorId, localX,Y,Z}` + km 单位」的数据模型实现世界坐标体系。
2. 根据开局配置（星区总量、六边形半径、恒星系 : 深空比例、随机种子）生成银河数据。
3. 提供可复现的生成器 API（输入种子 → 输出世界数据结构）。
4. 渲染层实现离散阶段 LOD 与阶段内线性缩放，确保视觉连续。
5. 暴露配置与调试接口（F3 坐标轴、参数注入）。

## Technical Context

**Language/Version**: Java 17（LibGDX 1.12.x）
**Primary Dependencies**: LibGDX Core & Renderer、Jackson / Kryo（序列化）、JUnit 5、Gradle 8
**Storage**: 本地存档 JSON/二进制；服务器端可选嵌入式 DB 或文件；当前阶段采用文件
**Testing**: JUnit 5 + AssertJ；性能基准使用 JMH 1.37（Gradle jmh 插件）
**Target Platform**: Desktop (Windows/macOS/Linux) + Headless Server JVM
**Project Type**: 单仓库多模块（core/shared/server/client），本功能主要落在 `shared`（生成逻辑）与 `client`（渲染桥接）
**Performance Goals**: 生成 10⁵ 个恒星系 ≤ 5 s（i7-12700 单线程）；客户端渲染 60 FPS@1080p
**Constraints**: 浮点误差 <1 km；内存占用 <256 MB（仅世界数据）；加载首帧 ≤ 3 s
**Scale/Scope**: 默认 5×10⁴–2×10⁵ 恒星系；星区 ~10³；行星/卫星 ~10⁶ 对象级别

## Constitution Check

| 宪章原则 | 评估 | 备注 |
|----------|------|------|
| 模块化与可维护性 | ✅ | 世界生成逻辑封装在 `shared.universegen` 模块 |
| 分层架构 & C/S 分离 | ✅ | 生成逻辑无 UI 依赖；渲染仅在 `client` 使用只读接口 |
| 命名与注释 | ✅ | 按英文标识符+中文注释执行 |
| Mod 支持 | ⚠️ | 生成器配置接口需开放脚本化（NEEDS CLARIFICATION 脚本格式） |
| Simulation-Driven | ✅ | 生成为离线步骤，不影响帧循环 |
| UI 层独立 | ✅ | UI 未涉及 |

GATE 通过，但需澄清脚本化扩展格式（JSON/Lua/其他）。

## Project Structure

### Documentation (this feature)

```text
specs/012-realistic-universe-gen/
├── plan.md          # 本文件
├── research.md      # Phase 0 输出
├── data-model.md    # Phase 1 输出
├── quickstart.md    # Phase 1 输出
├── contracts/       # Phase 1 输出
└── tasks.md         # Phase 2 (/speckit.tasks)
```

### Source Code (repository root)

```text
shared/
└── src/
    ├── main/java/com/staraxis/universegen/
    │   ├── GalaxyGenerator.java
    │   ├── SectorGenerator.java
    │   ├── StarSystemGenerator.java
    │   ├── PlanetGenerator.java
    │   ├── CoordinateSystem.java  # 分层坐标实现
    │   └── config/
    │       └── UniverseGenConfig.java
    └── test/java/...  # 单元/性能测试

client/
└── src/
    └── main/java/com/staraxis/render/universe/
        ├── UniverseRenderer.java
        ├── LODManager.java
        ├── CoordinateAxisOverlay.java  # F3 调试轴
        └── ...

server/
└── src/
    └── main/java/com/staraxis/sim/universe/
        └── UniverseRepository.java  # 读取生成结果并提供查询
```

**Structure Decision**: 保持现有 multi-module（shared/client/server），仅新增 `universegen` 包；渲染桥接在 client；服务器读取只读数据。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Lua/脚本扩展 (潜在) | 满足 Mod 作者扩展生成逻辑 | 仅 JSON 配置无法表达动态逻辑（例如按星系标签条件生长） |

> 其余部分待 Phase 0 研究后补充。
