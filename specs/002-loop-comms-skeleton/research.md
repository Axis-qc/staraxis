# Phase 0 Research: 创建主循环与通讯骨架（本地双通道）

**Feature**: `002-loop-comms-skeleton`  
**Date**: 2026-01-15  
**Spec**: `specs/002-loop-comms-skeleton/spec.md`

## Decision 1: 本特性不实现 UDP 可靠层

- **Decision**: 数据通道（数据通道）仅用于本地环境下的 `ServerTick`（tick 同步消息）下发，不实现 ACK/重传/乱序窗口/分片等 UDP 可靠层机制。
- **Rationale**:
  - spec 已固化：本特性验收为“本地连接模式”，且不进行丢包测试（`SC-005`）。
  - 本特性目标是“初始框架”，可靠层属于后续迭代内容，提前引入会造成范围膨胀。
- **Alternatives considered**:
  - 实现最小 ACK-only：仍会引入状态机与重传逻辑，超出本特性范围。

## Decision 2: 双通道会话绑定方式

- **Decision**: 控制通道 `ServerHello`（服务端握手响应）分配 `clientId`（客户端连接唯一标识），数据通道通过 `DataHello{clientId}`（数据通道绑定请求）与控制通道会话绑定，绑定失败则断开数据通道。
- **Rationale**:
  - 已在 Clarifications 固化，并落到 `FR-011`。
  - 该方式便于本地环境快速验证，且后续可扩展到更复杂的 sessionToken/nonce。
- **Alternatives considered**:
  - 客户端生成 `clientId`：会增加冲突与重连语义决策点。
  - sessionToken/nonce：更接近公网/NAT 方案，但超出本地骨架范围。

## Decision 3: 控制通道消息边界策略

- **Decision**: 控制通道必须实现明确消息边界（FR-007）。具体选型在 Phase 1 合同（contracts）中以“长度前缀帧”作为推荐方案。
- **Rationale**:
  - 边界机制是 TCP 必需能力，且不属于“多余功能”。
  - 长度前缀方案更通用，不依赖字符集与换行约定。
- **Alternatives considered**:
  - 换行分隔：实现更快，但二进制载荷不友好，且易受换行处理影响。

## Open Items Resolved

- *NEEDS CLARIFICATION (plan.md)*: JDK 版本、网络库选型、代码目录结构。
  - 说明：这些属于实现层面，将在 `/speckit.tasks` 或实际编码时通过代码库扫描确认；不影响本特性规格正确性。
