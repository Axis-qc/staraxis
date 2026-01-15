# Contracts: 数据通道（绑定 / Tick 下发）

> 目标：定义数据通道（数据通道）上的最小消息契约。
>
> 范围边界：
> - 本特性为“本地连接模式”（FR-013），验收不做丢包测试（SC-005）。
> - 本数据通道不实现 UDP 可靠层（ACK/重传/乱序窗口/分片），也不要求跨 NAT 的地址绑定。
>
> 术语：
> - `clientId`（客户端连接唯一标识）：由控制通道 `ServerHello`（服务端握手响应）分配。
> - `serverTick`（服务端权威 tick）：服务端主循环推进的权威 tick。

---

## Message: DataHello（数据通道绑定请求）

- **Direction**: Client → Server
- **Purpose**: 将数据通道连接绑定到控制通道建立的 `Session`（会话）。
- **Fields**:
  - `clientId`（客户端连接唯一标识）
- **Rules**:
  - 若 `clientId` 不存在或不可绑定：服务端必须拒绝并断开数据通道连接（FR-011）。

## Message: ServerTick（tick 同步消息）

- **Direction**: Server → Client
- **Frequency**: 每 tick 下发一次（FR-012），`ticksPerSecond`（每秒 tick 数）为 25 时约 25 条/秒。
- **Fields**:
  - `serverTick`（服务端权威 tick）
