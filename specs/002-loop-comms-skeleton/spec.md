# Feature Specification: 创建主循环与通讯骨架

**Feature Branch**: `002-loop-comms-skeleton`  
**Created**: 2026-01-15  
**Status**: Draft  
**Input**: User description: "根据 docx/00-通用规范与治理/核心规范.md docx/01-底层架构/1-最终敲定方案/模拟时间.md docx/01-底层架构/1-最终敲定方案/00服务端主循环-实现.md 创建服务端主循环和客户端主循环，以及客户端和服务端的通讯框架，不要多余功能，只搭建初始框架。没有远程分支，直接在本地创建002"

## Clarifications

### Session 2026-01-15

- Q: 通讯传输的连接模型选择哪个？ → A: B（双通道：一条“控制通道”负责握手/心跳，另一条“数据通道”只发 `ServerTick`（tick 同步消息））
- Q: 双通道如何关联同一会话（`clientId` 的来源）？ → A: A（控制通道 `ServerHello` 分配 `clientId`（客户端连接唯一标识），数据通道连接后发送 `DataHello{clientId}`（数据通道绑定请求）进行绑定）
- Q: 数据通道绑定失败如何处理？ → A: A（绑定失败时服务端拒绝并断开数据通道；客户端需先建立控制通道再重试）
- Q: `ServerTick`（tick 同步消息）下发频率？ → A: A（每 tick 下发一次：约 25 条/秒）
- Q: `ClientHeartbeat`（客户端心跳）发送间隔？ → A: 10 秒一次（本地连接阶段，不进行丢包测试）

## User Scenarios & Testing *(建议填写；不强制编写单元测试)*

### User Story 1 - 启动服务端主循环并稳定运行 (Priority: P1)

作为开发者，我希望可以启动一个最小可运行的服务端进程，使其按固定节拍推进 `serverTick`（服务端权威 tick，用于联机对齐与确定性排序），从而为后续接入指令、快照与事件提供稳定时间轴。

**Why this priority**: 主循环是服务端权威模拟的基础，没有它后续任何联机与系统推进都无法进行。

**Independent Test**: 启动服务端后，观察控制台日志或监控输出：`serverTick` 递增且节拍接近 `25 tick/s`。

**Acceptance Scenarios**:

1. **Given** 服务端进程启动且无客户端连接，**When** 运行 10 秒，**Then** `serverTick` 累计递增约 250 次（允许小幅误差）。
2. **Given** 服务端进程启动且存在客户端连接，**When** 运行 10 秒，**Then** `serverTick` 仍稳定递增且不因网络收发阻塞主循环。

---

### User Story 2 - 客户端连接并接收服务端 Tick 同步 (Priority: P1)

作为开发者，我希望可以启动一个最小客户端进程连接服务端，并持续接收服务端下发的 `serverTick`（服务端权威 tick），从而验证最小通讯链路打通。

**Why this priority**: 在没有业务功能前，必须先证明“客户端能连上 + 能接收 tick”，这是后续联机语义的基础。

**Independent Test**: 启动客户端连接服务端，客户端控制台持续打印最新 `serverTick`。

**Acceptance Scenarios**:

1. **Given** 服务端已启动并监听端口，**When** 客户端发起连接并完成握手，**Then** 客户端在 1 秒内开始收到 `ServerTick` 消息。
2. **Given** 客户端持续运行，**When** 服务端持续运行 10 秒，**Then** 客户户端打印的 `lastServerTick`（客户端最后收到的权威 tick）单调递增且无倒退。

---

### User Story 3 - 基础心跳用于链路存活验证 (Priority: P2)

作为开发者，我希望客户端能够按固定间隔发送 `ClientHeartbeat`（客户端心跳，用于链路存活与观测），服务端能够接收并可选回 `ServerHeartbeatAck`（心跳回执），从而便于定位断线与收发异常。

**Why this priority**: 心跳是最小可观测性入口，有助于在“只有 tick 同步”的阶段排查网络问题。

**Independent Test**: 客户端每 N 秒输出一次“已发送心跳”，服务端输出“已收到心跳”；可选输出 ack 对应 `clientSeq`（客户端递增序号）。

**Acceptance Scenarios**:

1. **Given** 客户端与服务端已建立连接（本地连接），**When** 客户端每 10 秒发送一次 `ClientHeartbeat`，**Then** 服务端至少每 10 秒记录一条收到心跳的日志（本阶段不做丢包测试）。

### Edge Cases

- 当客户端未按期发送 `ClientHeartbeat`（客户端心跳）时，服务端如何判定连接不可用并记录日志？
- 当服务端短时间卡顿导致 `ServerTick`（tick 同步消息）发送间隔不均匀时，客户端是否仍能正确更新 `lastServerTick`（客户端最后收到的权威 tick）？

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供服务端主循环（Server Main Loop），并以 `ticksPerSecond`（每秒 tick 数）固定为 `25` 的节拍推进 `serverTick`（服务端权威 tick）。
- **FR-002**: 系统 MUST 在服务端主循环中划分 `PrepareTick`（准备 Tick）、`Update`（更新）、`Commit`（落账）、`PostUpdate`（更新后处理）四阶段，且在本特性范围内只实现“必要的 tick 推进与网络下发”，不引入任何业务系统推进。
- **FR-003**: 系统 MUST 计算 `timeScale`（全局时间倍率）与 `dtGameHours`（本 tick 推进的游戏小时数），并遵循 `dtGameHours = baseDtGameHours * timeScale` 口径，其中 `baseDtGameHours`（基准游戏小时步长）为 `1/25`。
- **FR-004**: 系统 MUST 提供客户端主循环（Client Main Loop），客户端能够连接服务端并接收 `ServerTick`（tick 同步消息），更新 `lastServerTick`（客户端最后收到的权威 tick）并对外可观测（例如控制台输出）。
- **FR-005**: 系统 MUST 提供最小通讯框架，至少包含 `ClientHello`（客户端握手请求）与 `ServerHello`（服务端握手响应），用于建立会话与基础版本信息交换。
- **FR-006**: 系统 MUST 支持客户端定时发送 `ClientHeartbeat`（客户端心跳，用于链路存活与观测），服务端 MUST 能接收；服务端 MAY 返回 `ServerHeartbeatAck`（心跳回执）。
- **FR-007**: 系统 MUST 明确并实现消息边界（例如定长头/长度前缀/换行分隔），避免粘包/半包导致解析错误。
- **FR-008**: 系统 MUST 遵循“服务端权威”（服务端是世界状态唯一权威）与“确定性”（相同初始状态 + 相同输入序列 => 相同输出）的约束：客户端不得生成或修改任何权威状态，仅能接收 `serverTick`（服务端权威 tick）并发送心跳/握手。
- **FR-009**: 系统 MUST 保证网络收发不阻塞服务端主循环：网络 I/O 不得在主循环线程内执行（至少应做到主循环线程无阻塞网络读写）。
- **FR-010**: 系统 MUST 采用双通道通讯模型：控制通道用于握手与心跳，数据通道用于 `ServerTick`（tick 同步消息）下发；两通道的连接关系必须能通过同一 `clientId`（客户端连接唯一标识）关联到同一 `Session`（会话）。
- **FR-011**: 系统 MUST 定义 `DataHello{clientId}`（数据通道绑定请求）用于数据通道与控制通道会话绑定；若绑定失败（例如 `clientId`（客户端连接唯一标识）不存在），服务端 MUST 拒绝并断开数据通道连接。
- **FR-012**: 系统 MUST 按每 tick 下发一次 `ServerTick`（tick 同步消息），在 `ticksPerSecond`（每秒 tick 数）为 `25` 时，数据通道下发频率约为 25 条/秒。
- **FR-013**: 系统 MUST 将本特性限定为“本地连接模式”：客户端与服务端在同一台机器上通过本地地址建立控制通道与数据通道连接；本特性不要求覆盖 NAT、外网地址绑定与跨网络环境的连通性。

### Key Entities *(include if feature involves data)*

- **Session**（会话）: 表示一次客户端与服务端的连接关系，包含 `clientId`（客户端连接唯一标识）、连接状态与最后活跃时间等。
- **TickState**（tick 状态）: 表示服务端推进相关的最小状态，包含 `serverTick`（服务端权威 tick）、`ticksPerSecond`（每秒 tick 数）、`timeScale`（全局时间倍率）、`dtGameHours`（本 tick 推进的游戏小时数）。
- **TickSyncMessage**（tick 同步消息）: 表示服务端向客户端下发的最小同步载荷，包含 `serverTick`（服务端权威 tick）。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 在无客户端连接时，服务端可连续运行 60 秒且不崩溃，`serverTick`（服务端权威 tick）持续递增，平均节拍接近 `25 tick/s`。
- **SC-002**: 在有 1 个客户端连接且保持心跳的情况下，服务端可连续运行 60 秒且不崩溃，客户端能持续接收 `ServerTick` 并观察到 `lastServerTick` 单调递增。
- **SC-003**: 客户端从启动到“收到第一条 `ServerTick`（tick 同步消息）”的时间不超过 2 秒（本机开发环境）。
- **SC-004**: 在 60 秒运行窗口内，服务端主循环无明显阻塞现象（例如 `serverTick` 长时间停止递增超过 1 秒的情况）。
- **SC-005**: 本特性验收在“本地连接模式”下完成：客户端与服务端在同一台机器上运行，且验收过程中不进行丢包/乱序/重传等网络对抗测试。