# Implementation Plan: StarAxis 设定与开发准备文档（Design Bible）

**Branch**: `001-game-design-bible` | **Date**: 2026-01-09 | **Spec**: [spec.md](spec.md)
**Input**: 根据@游戏大纲.md @.specify/memory/constitution.md 创建游戏的设定文档，游戏设计，交互，数值，坐标轴，术语，变量命名等开发的准备工作

## Summary

创建一份集中、可检索、可版本控制的设定与设计文档（Design Bible），用于对齐游戏的核心设计决策、交互模式、数值口径与开发规范。文档采用“核心总览 + 细节拆分”的混合结构，确保团队对游戏设计有统一理解，并为后续开发提供明确约束与指导。

## Technical Context

**语言/版本**：Markdown + Mermaid 图表（兼容 GitHub/GitLab）  
**主要依赖**：无（纯文档）  
**存储**：Git 版本控制（与代码同仓库）  
**目标平台**：Web 可访问（GitHub Pages/GitLab Pages）  
**项目类型**：技术文档  
**性能目标**：文档加载时间 < 3s（单页核心总览 < 1MB）  
**约束**：
- 必须支持离线编辑与版本对比
- 必须支持中英双语术语对照
- 必须包含可复制的公式与计算示例

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 符合性 | 说明 |
|------|--------|------|
| 模块化与可维护性 | ✅ | 文档按主题拆分，避免单文件过长 |
| 架构分层与端侧分离 | ✅ | 明确定义服务端/客户端/UI 责任边界 |
| 规范化命名与注释 | ✅ | 术语表强制中英对照，禁用同义词 |
| 扩展性与 Mod 支持 | ✅ | 数据驱动设计，预留 Mod 扩展点 |
| 游戏模拟驱动 | ✅ | 时间推进规则与模拟循环明确定义 |
| UI 层独立性 | ✅ | 交互与表现分离，UI 逻辑不侵入核心规则 |
| 多核性能优化 | 🔶 | 文档不直接涉及，但需考虑多线程数据一致性 |

## Project Structure

### 文档结构（本特性）

```text
specs/001-game-design-bible/
├── spec.md                # 核心总览（链接到各子文档）
├── plan.md                # 本实现计划
├── research.md            # 研究记录（如坐标系算法选型）
├── data-model/            # 数据模型定义
│   ├── coordinates.md     # 坐标系与单位
│   ├── economy.md         # 经济系统口径
│   ├── combat.md          # 战斗系统口径
│   └── ...
├── conventions/           # 规范与约定
│   ├── naming.md          # 命名规范与示例
│   ├── terminology.md     # 术语表（中英对照）
│   └── time-advancement.md# 时间推进规则
└── examples/              # 可执行示例
    ├── coordinate-demo/   # 坐标系转换示例
    └── economy-sim/       # 经济结算模拟
```

### 代码仓库结构（相关部分）

```text
docs/design-bible/         # 编译后的 HTML/PDF 输出
  └── assets/              # 图片/公式资源

src/
└── core/                  # 游戏核心（引用设计文档中的术语与规则）
    ├── simulation/        # 模拟系统（时间/经济/战斗）
    ├── world/             # 世界生成与坐标系统
    └── data/              # 数据驱动配置（数值表/本地化）

tools/validation/          # 设计规则校验脚本
  ├── check_terminology.py # 术语一致性检查
  └── validate_formulas.py # 公式可执行性验证
```

**结构决策**：
1. 采用“核心总览 + 主题拆分”结构，便于多人协作与定向查阅
2. 所有文档通过 `spec.md` 中的链接互相关联，保持可导航性
3. 示例代码与验证工具确保设计文档的可执行性与一致性

## Complexity Tracking

> **无宪法违反需要额外说明**

## 阶段规划

### Phase 0: 研究（已完成）
- [x] 确定文档范围与交付形态（Q1）
- [x] 明确权威性与冲突解决规则（Q2）
- [x] 确定数据驱动范围（Q3）
- [x] 澄清双坐标系实现（Q6）
- [x] 定义时间停止行为（Q7）
- [x] 制定术语一致性规则（Q9）

### Phase 1: 核心文档编写（当前阶段）
- [ ] 编写 `spec.md` 核心总览
- [ ] 拆分数据模型文档（坐标/经济/战斗等）
- [ ] 制定命名规范与术语表
- [ ] 添加可执行示例

### Phase 2: 验证与评审
- [ ] 运行术语一致性检查
- [ ] 验证公式可执行性
- [ ] 团队评审与反馈整合

### Phase 3: 发布与维护
- [ ] 生成 HTML/PDF 版本
- [ ] 设置自动化校验（CI）
- [ ] 建立更新与版本控制流程