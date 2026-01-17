# Data Model: JCEF 主菜单 UI 骨架

> 本数据模型仅覆盖“主菜单 UI↔Host”交互所需的最小数据形状，不包含模拟层、存档、网络等任何领域数据。

## Entity: MainMenuActionEvent（主菜单动作事件）

### Fields

- `action`（动作类型）: 表示用户在主菜单点击了哪个入口。

### Allowed Values（枚举值）

- `NEW_GAME_CLICK`（点击新游戏）
- `LOAD_GAME_CLICK`（点击加载游戏）
- `MULTIPLAYER_CLICK`（点击多人游戏）
- `SHIP_DESIGNER_CLICK`（点击舰船设计器）
- `SETTINGS_CLICK`（点击设置）
- `EXIT_CLICK`（点击退出游戏）

### Validation Rules

- `action`（动作类型）必须为上述枚举值之一。

## State Transitions

- 本功能不包含状态机；每次点击产生一次独立事件。
