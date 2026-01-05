# Contract: C/S Message Protocol (Kryo)

## Overview
所有 C/S 之间的通讯必须通过 `com.staraxis.game.shared.network` 包中定义的 Message 对象进行。这些对象由 Kryo 进行序列化和反序列化。

## Messages (Client to Server)

### 1. ConnectionRequest
客户端尝试连接服务端。
- `String playerName`: 玩家名称
- `String version`: 客户端版本号

### 2. PlayerCommandMessage
包装玩家的具体操作指令。
- `Command command`: 具体指令实体 (见 data-model.md)

## Messages (Server to Client)

### 1. ConnectionResponse
服务端回应连接请求。
- `boolean success`: 是否允许连接
- `String message`: 错误信息 (如果失败)
- `long assignedPlayerId`: 服务端分配的唯一 ID

### 2. GameStateUpdate
服务端推送的逻辑帧快照。
- `GameState state`: 完整的或增量的游戏状态 (见 data-model.md)

### 3. ServerNotification
服务端发送的异步通知（如消息、警告）。
- `String type`: 消息类型 (INFO, WARNING, ERROR)
- `String content`: 消息内容

## Kryo Registration Order
**重要**：为了保证序列化的一致性，以下类必须在 C/S 两端以完全相同的顺序进行注册：

1. `java.util.HashMap`
2. `java.util.ArrayList`
3. `com.staraxis.game.shared.model.Vector2`
4. `com.staraxis.game.shared.network.ConnectionRequest`
5. `com.staraxis.game.shared.network.ConnectionResponse`
6. `com.staraxis.game.shared.network.PlayerCommandMessage`
7. `com.staraxis.game.shared.network.GameStateUpdate`
8. `com.staraxis.game.shared.model.GameState`
9. `com.staraxis.game.shared.model.EntityState`
... (更多类按需追加)
