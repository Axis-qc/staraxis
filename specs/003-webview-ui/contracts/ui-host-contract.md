# UI ↔ Host Contract（003：开始界面）

**Spec**: [spec.md](../spec.md)  
**Plan**: [plan.md](../plan.md)  
**Date**: 2026-01-15

本文件定义 Web UI（运行在 WebView 内）与宿主客户端（Host，负责窗口/生命周期管理）之间的最小交互契约。

本期不涉及服务端通讯，所有消息均为 UI 与 Host 进程内/本地桥接调用。

## 资源入口约定

- UI 资源根目录：`assets/ui/`
- 允许多入口页面，但约定开始界面的主入口为：`assets/ui/start-menu/index.html`

> 说明：该约定用于验收与集成；不限制 UI 内部进一步拆分子目录。

---

## Messages

### UI → Host

#### 1) `ui.requestPlaceholder`

- **When**: 用户点击“新游戏/加载游戏/多人游戏/舰船编辑器/设置”任一按钮。
- **Purpose**: 请求 Host 展示“开发中”提示（占位）。
- **Payload (JSON)**

```json
{
  "type": "ui.requestPlaceholder",
  "source": "newGame"
}
```

- **Rules**
  - `source` 取值应为：`newGame | loadGame | multiplayer | shipEditor | settings`

#### 2) `ui.requestQuit`

- **When**: 用户点击“退出游戏”。
- **Purpose**: 请求 Host 发起退出流程。
- **Payload (JSON)**

```json
{
  "type": "ui.requestQuit"
}
```

---

### Host → UI

#### 3) `host.showPlaceholder`

- **When**: Host 收到 `ui.requestPlaceholder` 并决定展示占位提示。
- **Purpose**: 通知 UI 展示“开发中”提示。
- **Payload (JSON)**

```json
{
  "type": "host.showPlaceholder",
  "message": "开发中",
  "autoDismiss": true,
  "dismissible": true
}
```

- **Rules**
  - 必须满足 spec：可手动关闭 + 会自动消失。

#### 4) `host.showQuitConfirm`

- **When**: Host 收到 `ui.requestQuit`。
- **Purpose**: 通知 UI 展示退出确认框。
- **Payload (JSON)**

```json
{
  "type": "host.showQuitConfirm",
  "message": "确认退出？"
}
```

#### 5) `ui.quitConfirmResult`（UI → Host，确认框结果）

- **When**: 用户在退出确认框中点击“确认/取消”。
- **Purpose**: 将确认结果回传给 Host。
- **Payload (JSON)**

```json
{
  "type": "ui.quitConfirmResult",
  "confirmed": true
}
```

- **Rules**
  - `confirmed=true` → Host 必须退出客户端。
  - `confirmed=false` → Host 不退出，回到开始界面。

#### 6) `host.webViewLoadFailed`

- **When**: Host 发现 WebView 主入口加载失败或资源不可用。
- **Purpose**: 通知 UI 进入错误页/错误层状态，显示错误文案与“退出游戏”按钮。
- **Payload (JSON)**

```json
{
  "type": "host.webViewLoadFailed",
  "message": "UI 资源加载失败"
}
```

---

## Error Handling

- **Unknown message type**: 接收方应忽略并记录（不影响 UI 可用性）。
- **Invalid payload**: 接收方应忽略并记录；必要时回退到可退出状态（满足 FR-W05）。

## Notes

- 该契约只描述消息语义与 JSON 形状，不规定具体桥接 API 名称/实现方式。
