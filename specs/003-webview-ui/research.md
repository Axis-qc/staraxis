# Phase 0 Research: WebView 嵌入与开始界面

**Feature**: [spec.md](./spec.md)  
**Plan**: [plan.md](./plan.md)  
**Date**: 2026-01-15

本文件用于消除 `plan.md` 中的 NEEDS CLARIFICATION，并把关键决定沉淀为后续 Phase 1 设计与 Phase 2 任务拆解的输入。

## Decision 1: Java 版本与构建工具

- **Decision**: 使用项目现有的 Java 版本与构建工具（以仓库配置为准）。
- **Rationale**: 本期目标是把 WebView 开始界面接入现有客户端，优先遵循既有工程约束，避免引入跨工具链的额外成本。
- **Alternatives considered**:
  - 强行升级 Java 版本：会引入额外兼容性与 CI/本地环境成本。


## Decision 2: WebView 方案与生命周期

- **Decision**: 采用项目既定 WebView 容器方案（以代码库/依赖配置为准），并实现“可创建/可销毁”的生命周期管理：
  - 客户端启动：初始化 WebView 并加载开始界面资源
  - 客户端退出：释放 WebView 资源，确保无残留进程
- **Rationale**: 退出可用是本期核心验收项之一；WebView 资源释放不完整容易造成残留进程与下次启动异常。
- **Alternatives considered**:
  - 只做一次性创建不做销毁：不满足退出/资源回收质量门禁。

- **Decision（更新）**: WebView 具体方案选用 JCEF（Chromium），Windows 平台原生包随项目管理。

## Decision 3: 客户端窗口框架与挂载点

- **Decision**: 将开始界面 WebView 作为客户端 UI 层的一部分挂载到“启动后默认展示”的窗口/场景中。
- **Rationale**: spec 明确开始界面是进入游戏的第一屏；因此挂载点必须在客户端启动流程中可确定且稳定。
- **Alternatives considered**:
  - 通过调试入口单独打开：不满足“启动进入开始界面”的用户场景。

- **已确认**: 图形化客户端窗口入口为 `lwjgl3/src/main/java/staraxis/lwjgl3/Lwjgl3Launcher.java`；主循环/挂载点为 `client/src/main/java/staraxis/ClientGame.java`（`create()`/`render()`/`dispose()`）。

## Decision 4: 目标平台优先级

- **Decision**: 以项目当前开发环境/首要运行环境为优先平台（由仓库与团队当前使用环境决定）。
- **Rationale**: WebView 多平台差异较大（打包、二进制依赖、GPU），先保证一个平台跑通可降低风险。
- **Alternatives considered**:
  - 三平台一次性全支持：风险与验证成本显著增加。

- **已确认**: 本期优先跑通 Windows。

## Decision 5: 测试策略

- **Decision**: 本期以手工验收为主，满足以下可重复步骤：
  - 启动进入开始界面
  - 5 个按钮点击弹“开发中”（可关闭且会自动消失）
  - 点击退出弹确认框，确认后退出
  - 模拟 WebView 资源加载失败时出现错误层与退出按钮
- **Rationale**: 宪章强调可编译可启动为质量门禁且不强制单测；本期功能以 UI 集成为主，手工验收性价比最高。
- **Alternatives considered**:
  - 引入 UI 自动化测试：投入与基础设施成本高，适合后续迭代。

- **已确认**: 本期不强制自动化测试；以 `quickstart.md` 手工验收为准。

## Research Tasks (待执行)

为了把以上 NEEDS CLARIFICATION 全部消除，Phase 0 需要做一次仓库扫描（代码检索/读取构建文件），明确：
- Java 版本、构建工具
- WebView 具体依赖与封装
- 客户端窗口/渲染框架与开始界面挂载点
- 目标平台优先级
- 是否已有测试框架

> 说明：本次 `/speckit.plan` 生成环境脚本返回的是 `specs/main` 路径，但本 feature 的真实 spec 位于 `specs/003-webview-ui/`。本研究文件与后续产物均以该目录为准。
