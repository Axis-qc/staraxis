# Data Model: 创建主循环与通讯骨架（本地双通道）

**Feature**: `002-loop-comms-skeleton`  
**Date**: 2026-01-15  
**Spec**: `specs/002-loop-comms-skeleton/spec.md`

> 目标：抽取本特性（仅主循环 + 通讯骨架）所需的最小数据实体与字段。
> 
> 约束：遵循 `serverTick`（服务端权威 tick）为唯一权威时间轴；本特性为“本地连接模式”，不做丢包测试。

---

## Entity: Session（会话）

表示一个客户端与服务端的会话绑定关系。由于本特性为双通道模型，`Session`（会话）需要同时关联控制通道与数据通道。

### Fields

- `clientId`（客户端连接唯一标识）
  - 唯一性：在服务端进程生命周期内唯一
  - 来源：由 `ServerHello`（服务端握手响应）分配
- `controlChannelState`（控制通道状态）
  - 取值：`Connected`（已连接）/`Disconnected`（已断开）
- `dataChannelState`（数据通道状态）
  - 取值：`Unbound`（未绑定）/`Bound`（已绑定）/`Disconnected`（已断开）
- `lastHeartbeatAtMs`（最后一次收到客户端心跳的本地时间毫秒）
  - 用途：链路观测与超时判断（本特性不强制实现踢出/重连策略）
- `lastServerTickSent`（该会话最后一次下发的服务端权威 tick）
  - 用途：便于日志观测（不用于可靠层）

### Lifecycle / State transitions

- 控制通道建立后：`controlChannelState = Connected`
- 客户端发送 `DataHello{clientId}`（数据通道绑定请求）后：
  - 成功：`dataChannelState = Bound`
  - 失败：服务端断开数据通道连接（FR-011）
- 任一通道断开：对应 state 进入 `Disconnected`

---

## Entity: TickState（Tick 状态）

表示服务端主循环推进所需的最小权威 tick 状态。

### Fields

- `serverTick`（服务端权威 tick，用于联机对齐与确定性排序）
  - 规则：每 tick 自增 +1
- `ticksPerSecond`（每秒 tick 数）
  - 常量：25
- `timeScale`（全局时间倍率）
  - 本特性口径：可固定为 1.0
- `dtGameHours`（本 tick 推进的游戏小时数）
  - 公式：`dtGameHours = (1/25) * timeScale`

---

## Entity: Messages（消息载荷最小集）

> 这里列的是“数据实体视角”的最小字段，具体协议见 `contracts/`。

- `ClientHello`（客户端握手请求）：`clientVersion`（客户端版本字符串，可用于日志）
- `ServerHello`（服务端握手响应）：`clientId`（客户端连接唯一标识），`serverTick`（服务端权威 tick）
- `ClientHeartbeat`（客户端心跳）：`clientSeq`（客户端递增序号），`clientTimeMs`（客户端本地时间毫秒，仅用于观测）
- `ServerHeartbeatAck`（心跳回执，可选）：`clientSeq`（回显客户端序号），`serverTick`（服务端权威 tick）
- `DataHello`（数据通道绑定请求）：`clientId`（客户端连接唯一标识）
- `ServerTick`（tick 同步消息）：`serverTick`（服务端权威 tick）
