# Research: JCEF 主菜单 UI 骨架

## 目标

本研究用于为“JCEF 主菜单 UI 骨架”选择稳定、可落地的集成方案，并将决策记录下来，以降低后续实现返工风险。

## 决策 1：JCEF 与桌面窗口的集成方式

- **Decision**: 使用“宿主窗口内嵌 WebView”的方式，将 WebView 渲染在游戏窗口内的 UI 层上。
- **Rationale**: 该方式与《客户端设计方案》对“OpenGL 下层 + WebView 上层”的强制口径一致，能在不引入模拟层的前提下先跑通 UI。
- **Alternatives considered**:
  - 独立开一个 WebView 窗口显示 UI（会偏离“叠加渲染”目标，并导致输入/焦点管理更复杂）
  - 先用非 WebView 的 UI 方案临时替代（不符合本次需求：必须使用 JCEF）

## 决策 2：UI↔Host 事件桥接方式

- **Decision**: 采用“UI 发送主菜单动作事件到 Host”的桥接方式，Host 负责执行动作（弹出提示/退出）。
- **Rationale**: 满足 UI 独立性原则，且事件集固定（6 个），可以定义稳定的契约。
- **Alternatives considered**:
  - Host 主动轮询 UI 状态（复杂且耦合）
  - UI 直接调用宿主的业务逻辑对象（违反边界）

## 未决项（Deferred）

- 如何将 JCEF 的渲染结果与 LibGDX 的窗口合成到同一窗口中，属于实现细节，放入任务阶段验证。
- UI 的视觉样式与布局细节属于设计/实现范畴，不在本研究中讨论。
