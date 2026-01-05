# Data Model: Game Framework Architecture (C/S Separation)

## Entities

### 1. GameState (游戏状态同步包)
代表某一 Tick 下游戏世界的全量或增量状态快照。由服务端序列化发送，客户端反序列化读取。

| Field | Type | Description |
|-------|------|-------------|
| tick | long | 当前逻辑帧编号 |
| timestamp | long | 服务端产生该状态的时间戳 |
| entities | Map<Long, EntityState> | 当前世界中所有实体的状态映射 (ID -> State) |
| worldData | WorldMetadata | 包含宇宙规模、全局资源等非实体信息 |

### 2. EntityState (实体状态)
单个游戏实体（如舰船、行星）在某一时刻的核心属性。

| Field | Type | Description |
|-------|------|-------------|
| id | long | 唯一标识符 |
| type | EntityType | 实体类别 (SHIP, STATION, PLANET, etc.) |
| position | Vector2 | 逻辑坐标 (基于 0° 向右的坐标系) |
| rotation | float | 逻辑朝向 |
| health | float | 生命值/强度 |
| metadata | Map<String, Object> | 实体特定数据 (如名称、所属国家) |

### 3. Command (指令/动作意图)
客户端发送给服务端的行为请求。

| Field | Type | Description |
|-------|------|-------------|
| playerId | long | 发起指令的玩家 ID |
| commandType | CommandType | 指令类型 (MOVE, BUILD, ATTACK, etc.) |
| targetId | long | 作用目标 ID (可选) |
| parameters | Map<String, Object> | 指令具体参数 (如目标坐标) |
| timestamp | long | 指令发起时间 |

### 4. Vector2 (逻辑坐标点)
C/S 共享的数学模型，定义在 `shared` 目录。

| Field | Type | Description |
|-------|------|-------------|
| x | float | X 轴坐标 (向右为正) |
| y | float | Y 轴坐标 (向上为正) |

## State Transitions & Rules

1. **指令验证 (Command Validation)**:
   - 服务端接收到 `Command` 后，必须根据当前 `GameState` 验证其合法性（如资源是否足够、距离是否在射程内）。
   - 验证通过后，在下一个 Tick 将变更应用到 `GameState`。

2. **状态更新 (State Update)**:
   - 每隔 50ms (20Hz)，`GameServer` 遍历所有系统逻辑，更新 `GameState`。
   - 更新完成后，将 `GameState` 广播给所有已连接的 `GameClient`。

3. **客户端补偿 (Client Interpolation)**:
   - 客户端维护一个状态缓冲区（至少包含两个最近的 `GameState`）。
   - 渲染时根据当前渲染时间在两个状态间进行插值计算，实现平滑视觉位移。
