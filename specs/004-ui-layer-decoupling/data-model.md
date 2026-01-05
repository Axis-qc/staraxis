# Data Model: UI Layer Decoupling

## Entities

### UIModel (Abstract)
所有具体 UI Model 的基类。

| Field | Type | Description |
|-------|------|-------------|
| dirty | Boolean | 标记数据是否已更新，需要重新绘制 |
| listeners | List | 视图层监听器 |

### MainMenuModel
主界面显示模型。

| Field | Type | Description |
|-------|------|-------------|
| menuItems | List<String> | 菜单项列表 |
| backgroundState | Object | 动态背景的物理状态（由事件更新） |

### SettingsModel
设置界面显示模型。

| Field | Type | Description |
|-------|------|-------------|
| resolutions | List<String> | 可选分辨率 |
| currentLanguage | String | 当前语言显示名 |

## Event Contracts
逻辑层向 UI 层发送的事件定义。

- `GameStateChangedEvent`: 包含世界状态快照。
- `PreferenceChangedEvent`: 包含用户配置变更。
- `SystemNotificationEvent`: 用于显示 Toast 或弹窗。

## State Transitions
1. **Event Trigger**: 逻辑层逻辑执行 -> 产生状态变更。
2. **Push**: 事件总线将数据推送到对应的 `UIModel`。
3. **Model Update**: `UIModel` 更新内部状态并标记 `dirty`。
4. **View Refresh**: `UIManager` 在下一帧渲染时检测到 `dirty`，通知 View 刷新组件。
