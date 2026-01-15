# Feature Spec: 引入 WebView 渲染客户端 UI

**Feature ID**: 003-webview-ui  
**Date**: 2026-01-15  
**Owner**: TBD  

## Summary
在现有桌面客户端中嵌入 WebView（Chromium JCEF 方案），让游戏 UI（菜单、面板、HUD 等）使用 HTML / CSS / JS 技术栈渲染，并通过 JS ↔ JVM 桥接与现有客户端逻辑交换数据。

## Goals
* UI 迭代效率显著提升（前端生态、热重载）。
* 保持与现有 OpenGL 战场渲染并存，互不阻塞。
* 桥接层提供 Command / Snapshot JSON 通道，与网络骨架协议一致。
* 提供**主菜单界面**，包含：新游戏、加载游戏、多人游戏、舰船编辑器、设置、退出游戏，以及实时显示**服务端连接状态**。
* **首期仅完整实现“设置”与“退出游戏”两项功能；其余四项点击后显示“开发中”占位弹窗**。

## Non-Goals / Out of Scope
* 不迁移战场渲染到 WebGL。本特性仅覆盖 UI 面板层。
* 不支持移动端 WebView（后续迭代）。
* 不实现离屏纹理共享；UI 与战场区域分窗或叠加。

---

## User Scenarios & Testing
1. **启动客户端**
   * 用户双击启动；窗口出现，左侧战场（OpenGL），右侧 UI 面板（WebView）加载完成 < 2 s。
   * 主菜单显示 6 个入口按钮；右上角显示“已连接”绿灯（或“断开”红灯）。
2. **设置**
   * 用户点击“设置”打开设置弹窗，可修改分辨率/音量并保存。
3. **退出**
   * 点击“退出游戏”后出现确认弹窗，确认后客户端关闭。
4. **其他四项占位**
   * 点击“新游戏”“加载游戏”“多人游戏”“舰船编辑器”时弹出“开发中”提示，不触发任何后端指令。

---

## Functional Requirements
FR-W01  客户端必须在窗口中嵌入 Chromium-based WebView（JCEF ≥ 110）。

FR-W02  WebView 页面初始加载时间 ≤ 2 s（本地项目资源）。

FR-W03  客户端需暴露 `javaBridge.sendCommand(String json)` 给 JS，确保与现有 Command 协议字段一致。

FR-W04  客户端需监听 JS 端 `window.onSnapshot(json)`，每当收到服务端 Snapshot / Event 时调用。

FR-W05  JS 与 JVM 之间消息序列化格式统一使用 UTF-8 JSON。

FR-W06  WebView 与 OpenGL 渲染互不阻塞；主循环 FPS 波动 ≤ 5 %。

FR-W07  客户端退出时需优雅释放 WebView 资源，防止进程残留。

FR-W08  Quickstart 文档更新：新增前端启动说明与依赖下载指引。

FR-W09  主菜单必须包含 6 个入口按钮：新游戏、加载游戏、多人游戏、舰船编辑器、设置、退出游戏；按钮风格与整体 UI 统一。

FR-W10  主菜单右上角需实时显示服务端连接状态指示灯：绿色=已连接，红色=断开；状态更新延迟 ≤ 1 s。

FR-W11  实现阶段划分：
* FR-W11a  “设置”按钮：弹出设置面板，可修改分辨率、音量并保存。
* FR-W11b  “退出游戏”按钮：出现确认弹窗，确认后关闭客户端。
* FR-W11c  其他四个按钮点击后仅显示“开发中”提示弹窗，不发送任何网络指令。

---

## Assumptions
- 用户环境已安装 Java 21；Chromium 包将随安装包分发。  
- UI 资源放置于 `client/ui/dist/`，生产环境走 `file://` 协议加载。  
- 安全需求：UI 不执行远程脚本；所有资源本地加载。

---

## Success Criteria
SC-W1  启动到 UI 完成渲染 < 2 s（冷启动，本地）。

SC-W2  点击“设置”时面板在 300 ms 内弹出并可保存；点击“退出游戏”确认后 1 s 内关闭客户端。

SC-W3  主循环平均 FPS ≥ 24（目标 25）且波动 ≤ ±5 %。

SC-W4  关闭客户端后，无残留 `jcef` 进程。

SC-W5  Quickstart 步骤可在一台新机器上 15 分钟内跑通。

SC-W6  其他四个按钮点击后弹窗提示“开发中”且不报错。

---

## Key Entities
| Entity | Fields | Description |
|--------|--------|-------------|
| `WebUIBridge` | `sendCommand(json)`<br>`onSnapshot(json)`<br>`onConnectionStatus(connected:bool)` | 双向调用接口（JS ↔ JVM） |

---

## Dependencies
- JCEF 二进制包（Windows, macOS, Linux）
- Gson 已存在

## Risks & Mitigations
| 风险 | 影响 | 缓解 |
|------|------|-------|
| WebView GPU 黑名单导致降级软件渲染 | UI 卡顿 | 启动时检测 `chrome://gpu` 状态并提示用户 |
| 体积增加 (~70 MB) | 下载包变大 | 后续考虑 Tauri + Slim Chromium |
| UI 与 OpenGL 区域尺寸适配 | 布局错位 | 使用 Flex/百分比布局 + 多分辨率测试 |

---

## Open Questions
无
