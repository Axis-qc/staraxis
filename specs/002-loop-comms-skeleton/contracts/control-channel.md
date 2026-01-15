# Contracts: 控制通道（握手 / 心跳）

> 目标：定义控制通道（控制通道）上的最小消息契约。
>
> 范围边界：
> - 本特性为“本地连接模式”，不做丢包测试，不覆盖公网/NAT。
> - 本通道需要明确消息边界（FR-007）。

---

## 消息边界（Framing）

本通道采用 Netty 的 `LengthFieldPrepender` 与 `LengthFieldBasedFrameDecoder` 解决粘包/半包问题。

- **帧格式**: `[Length (4 bytes)][Payload (N bytes)]`
- **长度字段**:
  - **字节数**: 4 bytes
  - **字节序**: Big Endian
  - **值含义**: 仅表示后续 `Payload` 的长度，不包含 `Length` 字段本身的 4 字节。
- **最大帧长**: 65535 bytes (可配置)
>
> 术语：
> - `clientId`（客户端连接唯一标识）：由服务端在 `ServerHello`（服务端握手响应）分配。
> - `clientSeq`（客户端递增序号）：客户端单调递增，用于日志对账（本特性不实现乱序/重传语义）。
> - `serverTick`（服务端权威 tick）：服务端主循环推进的权威 tick。

---

## Message: ClientHello（客户端握手请求）

- **Direction**: Client → Server
- **Fields**:
  - `clientVersion`（客户端版本字符串，用于日志/兼容性提示）

## Message: ServerHello（服务端握手响应）

- **Direction**: Server → Client
- **Fields**:
  - `clientId`（客户端连接唯一标识，由服务端分配）
  - `serverTick`（当前服务端权威 tick，用于客户端初始化显示/对齐）

## Message: ClientHeartbeat（客户端心跳）

- **Direction**: Client → Server
- **Interval**: 10 秒一次（SC-005：本地连接，不丢包测试）
- **Fields**:
  - `clientSeq`（客户端递增序号）
  - `clientTimeMs`（客户端本地时间毫秒，仅用于观测，不得参与权威逻辑）

## Message: ServerHeartbeatAck（心跳回执，可选）

- **Direction**: Server → Client
- **Fields**:
  - `clientSeq`（回显客户端序号）
  - `serverTick`（服务端权威 tick，用于日志观测）
