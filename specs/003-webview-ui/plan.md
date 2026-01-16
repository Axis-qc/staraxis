# Implementation Plan: WebView 嵌入与开始界面

**Branch**: `main` | **Date**: 2026-01-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-webview-ui/spec.md`

## Summary

本期实现桌面客户端的“开始界面”（主菜单）UI：在客户端窗口中嵌入 WebView 来渲染 UI。

开始界面包含 6 个按钮：新游戏、加载游戏、多人游戏、舰船编辑器、设置、退出游戏。

其中：
- 新游戏 / 加载游戏 / 多人游戏 / 舰船编辑器 / 设置：点击后弹出“开发中”提示（可手动关闭且会自动消失）。
- 退出游戏：点击后弹出确认对话框（确认/取消）；确认后客户端退出。
- WebView 加载失败：展示错误页/错误层（错误文案 + “退出游戏”按钮），用户仍可退出。

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: WebView 容器（本期新增接入；具体实现方案在实现阶段确定）  
**Storage**: N/A（本期不涉及持久化数据要求）  
**Testing**: 手工验证为主（不强制自动化测试）  
**Target Platform**: 桌面端（Windows/macOS/Linux；优先以当前开发环境跑通）  
**Project Type**: 单体桌面客户端（Gradle 多模块）  
**Performance Goals**: 满足 spec 的交互与退出指标（SC-W2/SC-W3）  
**Constraints**: UI 资源本地加载，资源根目录 `assets/ui/`；开始界面主入口 `assets/ui/start-menu/index.html`；本期不实现键盘操作，仅鼠标交互  
**Scale/Scope**: 单屏开始界面 + 若干弹窗；不实现实际游戏流程

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **计划先行**：本文件为实现计划，满足门禁。
- **架构分层/端侧分离**：本期仅做客户端 UI，不引入任何游戏逻辑计算到 UI 层；通过 UI 触发“退出”属于客户端生命周期管理，符合分层。
- **禁止硬编码/数据驱动**：UI 资源路径以 `assets/ui/` 为根目录，需避免散落式硬路径读取；通过统一资源入口加载。
- **命令限制**：除测试/验证外不依赖终端命令；实现阶段优先使用项目既有构建脚本/任务。
- **质量门禁**：必须保证项目可编译并能启动进入开始界面。

结论：**通过**（无必须违规项）。

## Project Structure

### Documentation (this feature)

```text
specs/003-webview-ui/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
# 客户端实际目录结构 NEEDS CLARIFICATION（待扫描仓库后补全）

assets/
└── ui/
    └── ...

# 代码入口/窗口创建/渲染循环/客户端退出逻辑位置 NEEDS CLARIFICATION
```

**Structure Decision**: 由于尚未扫描源码目录，本计划先以“单体桌面客户端 + assets/ui 静态资源”作为结构假设；Phase 0 将通过代码检索补全真实路径。

## Phase 0: Outline & Research (产出 research.md)

需要消除的 NEEDS CLARIFICATION：
- WebView 具体方案与生命周期管理方式（创建/销毁/线程模型）。
- 目标平台优先级（至少明确本期必须跑通的平台）。

已通过项目扫描确认：
- Java 版本与构建工具：Java 21 + Gradle。
- 客户端窗口/启动入口与挂载点：`lwjgl3/src/main/java/staraxis/lwjgl3/Lwjgl3Launcher.java` 创建窗口；`client/src/main/java/staraxis/ClientGame.java` 的 `create()` / `render()` / `dispose()` 可作为 WebView 挂载与资源释放位置。
- 测试策略：本期以手工验收为主（不强制自动化测试）。

## Phase 1: Design & Contracts (产出 data-model.md / contracts/* / quickstart.md)

- 设计开始界面 UI 的状态模型（按钮、弹窗、错误层）及状态转换。
- 设计“退出”触发路径：退出确认框由 UI 渲染；从 UI 点击到客户端关闭的调用链。
- 定义 UI 与宿主（客户端）之间的最小交互契约（见 `specs/003-webview-ui/contracts/ui-host-contract.md`）：
  - 占位弹窗触发
  - 退出请求
  - 退出确认结果回传
  - WebView 加载失败事件
- 写 Quickstart：如何放置/加载 `assets/ui/` 下的开始界面资源并启动到可见。

## Phase 2: Implementation Planning (交给 /speckit.tasks)

- 将实现拆成可交付任务：
  - WebView 宿主窗口嵌入
  - UI 资源加载（assets/ui）
  - 开始界面渲染与按钮事件
  - “开发中”提示弹窗
  - 退出确认对话框 + 客户端退出
  - WebView 失败兜底错误层
  - 手工验收步骤
