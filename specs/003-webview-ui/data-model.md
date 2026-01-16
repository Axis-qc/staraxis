# Data Model: WebView 嵌入与开始界面

**Feature**: [spec.md](./spec.md)  
**Date**: 2026-01-15

本期不引入持久化数据模型，主要是 UI 状态模型与事件/状态转换，用于保证需求可测与实现可拆分。

## Entities

### StartMenu

- **Fields**
  - `buttons`: 固定 6 个入口按钮
    - `newGame`
    - `loadGame`
    - `multiplayer`
    - `shipEditor`
    - `settings`
    - `quitGame`
  - `overlay`: 当前覆盖层（见下方 `OverlayState`）

- **Validation Rules**
  - 6 个按钮必须同时可见且可点击（对应 FR-W01/FR-W04）。

### OverlayState

表示开始界面上的覆盖层状态。

- **Variants**
  - `none`: 无覆盖层
  - `placeholderDialog`: “开发中”提示
  - `quitConfirmDialog`: 退出确认对话框
  - `webViewLoadError`: WebView 加载失败错误层

### PlaceholderDialog

- **Fields**
  - `title`: 固定或可选（例如“开发中”）
  - `message`: 固定或可选（例如“开发中，敬请期待”）
  - `autoDismissEnabled`: 必须为 true（对应 FR-W02）
  - `closeButtonEnabled`: 必须为 true（对应 FR-W02）

- **Lifecycle**
  - `open` → `dismissed`（自动消失或手动关闭）

### QuitConfirmDialog

- **Fields**
  - `title`: 例如“确认退出”
  - `message`: 例如“确定要退出游戏吗？”
  - `actions`: `confirm` / `cancel`

- **Lifecycle**
  - `open` → `confirm` → `clientExitRequested`
  - `open` → `cancel` → `dismissed`

### WebViewLoadError

- **Fields**
  - `message`: 错误文案
  - `exitActionVisible`: 必须为 true（对应 FR-W05）

- **Lifecycle**
  - `shown` → `exitClicked` → `clientExitRequested`

## State Transitions

```text
none
  ├─ click any placeholder button → placeholderDialog → (auto dismiss | close) → none
  ├─ click quitGame → quitConfirmDialog
  │     ├─ cancel → none
  │     └─ confirm → clientExitRequested
  └─ webView load failed → webViewLoadError
        └─ click exit → clientExitRequested
```

## Notes

- 本模型仅用于描述 UI 侧的可见状态与转换，不规定实现技术栈。
- 本期不包含键盘操作（对应 FR-W06）。
