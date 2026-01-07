# Implementation Plan: 012 真实比例宇宙生成（Real-Scale Universe Generation）

**Branch**: `012-realistic-universe-gen` | **Date**: 2026-01-07 | **Spec**: [/specs/012-realistic-universe-gen/spec.md](spec.md)
**Input**: Feature specification from `/specs/012-realistic-universe-gen/spec.md`

## Summary

在《010 Galaxy World Scaling》与《011 Astronomical Units》两项前置规格的基础上，本功能将在**服务器端**按真实天文学尺度程序化生成从银河到行星的完整层级宇宙数据，并在**客户端渲染层**强制执行 **1 px = 1 km** 的比例。核心技术要点：

1. **分段坐标系 (Segmented Space)** + **浮动原点 (Floating Origin)** 组合，解决高精度与渲染抖动。
2. 基于种子 (Seed) 的确定性随机生成器，支持可重复宇宙。
3. 星区以六边形网格分区 (edge 3–30 ly 可配置)；恒星系内部行星轨道遵循开普勒第三定律。
4. 纯数据驱动：生成参数与结果通过二进制 *.unv* 文件存储，暴露 JSON-like API 供工具链与 Mod 使用。

## Technical Context

**Language/Version**: C# 10 (兼容 Unity 2022 LTS)，服务器工具链同样使用 .NET 7
**Primary Dependencies**: Unity 2022 LTS、DOTS (ECS + Burst)、Unity.Mathematics、MessagePack-CSharp（数据序列化）
**Storage**: 分片二进制文件 `.unv` (可流式加载) + ScriptableObject 配置
**Testing**: Unity Test Framework (NUnit 3)
**Target Platform**: Windows 10+/macOS 13+/Linux（桌面）；后续可扩展 WebGL（非目标）
**Project Type**: Single (mono-repo，游戏客户端 + Editor 工具 + 服务端逻辑 位于同一 Unity Project 内的独立 Assembly Definition）
**Performance Goals**:
- 渲染：最低配置 60 FPS (1080p)
- 生成：新建存档 ≤10 s 内完成全银河数据落盘 (N≤50 k Star Systems)
**Constraints**:
- 1 px = 1 km 不得被打破（只允许逻辑缩放视口）
- 坐标计算误差 <1 km（~1 px）
- 运行期内内存占用 ≤4 GB（含纹理）
**Scale/Scope**:
- 星系数 1–10（可配置）
- 星区 ~104 – 106 个
- 恒星系 ≤5×105 个

## Constitution Check

| Principle | Assessment |
|-----------|-----------|
| 模块化与可维护性 | 生成器将作为独立 *UniverseGenerator* Assembly，参数通过 ScriptableObject 注入；符合原则。 |
| 分层架构 & C/S 分离 | 生成逻辑与渲染逻辑分立；渲染层不读取生成算法，只消费只读数据；符合。 |
| 规范化命名与注释 | C# PasalCase/camelCase  + 全中文文档；符合。 |
| 扩展性与 Mod 支持 | 生成参数、数据格式公开；符合。 |
| 模拟驱动逻辑 | 生成阶段离线，不影响帧率；运行期坐标转换在 Update 中 O(𝑁)≈O(星体可见)；符合。 |
| UI 层独立性 | 不涉及 UI 代码；符合。 |

**Gate Result**: ✅ 全部通过。若后续设计变更违反宪章，需在 *Complexity Tracking* 中说明理由。

## Project Structure

```text
src/
├── universe-generator/          # Assembly: 生成算法、数据模型、序列化
├── universe-runtime/            # Assembly: 运行期坐标变换、LOD、查询接口
├── editor/                      # Unity Editor 工具（菜单、可视化调试）
└── rendering/                   # 专用于渲染层（材质、着色器、系统）

tests/
├── unit/
├── integration/
└── contract/
```

**Structure Decision**: 采用单工程多 AssemblyDefinition 模式，保持 Unity 项目内部模块化；避免多仓库复杂度。

## Complexity Tracking

*目前无需豁免。*
