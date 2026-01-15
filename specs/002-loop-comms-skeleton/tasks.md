# Tasks: 创建主循环与通讯骨架（本地双通道）

**Input**: Design documents from `/specs/002-loop-comms-skeleton/`  
**Prerequisites**: plan.md（required）, spec.md（required）, research.md, data-model.md, contracts/, quickstart.md  

**Organization**: 任务按用户故事（US1/US2/US3）组织，确保每个故事都可独立验收。

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 在现有 `server/` 与 `client/` 目录结构内，补齐本特性所需的最小工程骨架与配置。

- [x] T001 创建服务端源码目录（当前 `server/src` 不存在）：`server/src/main/java/staraxis/server/`
- [x] T002 确认并记录服务端模块入口与运行方式（现有 `server/`，更新 `specs/002-loop-comms-skeleton/quickstart.md` 如需补充）
- [x] T003 确认并记录客户端模块入口与运行方式（现有 `client/src/main/java/staraxis/`，更新 `specs/002-loop-comms-skeleton/quickstart.md` 如需补充）
- [x] T004 创建服务端消息结构落位目录：`server/src/main/java/staraxis/net/proto/`
- [x] T005 创建客户端消息结构落位目录：`client/src/main/java/staraxis/net/proto/`
- [x] T006 [P] 在 `server/src/main/java/staraxis/server/` 创建最小日志输出工具类（或复用现有日志设施）
- [x] T007 [P] 在 `client/src/main/java/staraxis/client/` 创建最小日志输出工具类（或复用现有日志设施）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有用户故事共享的核心结构：Tick 状态、会话状态、消息契约落地（不含业务逻辑）。

- [x] T008 定义 `TickState`（tick 状态）数据结构（参考 `specs/002-loop-comms-skeleton/data-model.md`）在 `server/src/main/java/staraxis/server/TickState.java`
- [x] T009 定义 `Session`（会话）数据结构（含 `clientId`（客户端连接唯一标识）、通道状态）在 `server/src/main/java/staraxis/server/Session.java`
- [x] T010 定义控制通道消息结构（`ClientHello`/`ServerHello`/`ClientHeartbeat`/`ServerHeartbeatAck`）在 `server/src/main/java/staraxis/net/proto/` 与 `client/src/main/java/staraxis/net/proto/` 下（两处文件内容需完全一致）
- [x] T011 定义数据通道消息结构（`DataHello`/`ServerTick`）在 `server/src/main/java/staraxis/net/proto/` 与 `client/src/main/java/staraxis/net/proto/` 下（两处文件内容需完全一致）
- [x] T012 实现控制通道消息边界（FR-007）：采用 Netty 的 `LengthFieldPrepender` 与 `LengthFieldBasedFrameDecoder` 处理粘包/半包，并在 `specs/002-loop-comms-skeleton/contracts/control-channel.md` 记录帧格式

**Checkpoint**: Foundation ready（完成后可开始 US1/US2/US3 的实现）

---

## Phase 3: User Story 1 - 启动服务端主循环并稳定运行 (Priority: P1) 〓 MVP

**Goal**: 启动服务端主循环，按 `ticksPerSecond`（每秒 tick 数）= 25 推进 `serverTick`（服务端权威 tick），不依赖客户端存在。

**Independent Test**: 单独启动服务端 60 秒不崩溃，日志输出 `serverTick` 单调递增且节拍接近 25 tick/s（对应 SC-001、SC-004）。

- [x] T013 [US1] 实现服务端主循环骨架（PrepareTick/Update/Commit/PostUpdate）在 `server/src/main/java/staraxis/server/ServerMainLoop.java`
- [x] T014 [US1] 在 `PrepareTick` 中实现 `serverTick += 1` 与 `dtGameHours`（本 tick 推进的游戏小时数）计算（FR-003）在 `server/src/main/java/staraxis/server/ServerMainLoop.java`
- [x] T015 [US1] 实现 25 tick/s 调度（基于单调时间）并记录 `tickCostMs`（每 tick wall-clock 耗时）日志在 `server/src/main/java/staraxis/server/ServerMainLoop.java`
- [x] T016 [US1] 创建服务端启动入口（main）并启动主循环线程在 `server/src/main/java/staraxis/server/ServerMain.java`
- [ ] T017 [US1] 为 FR-009（网络 I/O 不阻塞主循环）增加可观测性：在主循环日志中打印当前线程名，并在网络收发回调中打印线程名（用于人工确认线程分离）在 `server/src/main/java/staraxis/server/ServerMainLoop.java` 与 `server/src/main/java/staraxis/server/net/*`

---

## Phase 4: User Story 2 - 客户端连接并接收服务端 Tick 同步 (Priority: P1)

**Goal**: 在本地连接模式下，客户端通过控制通道获取 `clientId`（客户端连接唯一标识），通过数据通道绑定后接收 `ServerTick`（tick 同步消息）。

**Independent Test**: 启动服务端 + 启动客户端，客户端 2 秒内收到第一条 `ServerTick`，且 `lastServerTick` 单调递增（对应 SC-002、SC-003）。

- [x] T018 [US2] 实现服务端控制通道接入：处理 `ClientHello` 并返回 `ServerHello{clientId, serverTick}` 在 `server/src/main/java/staraxis/server/net/ControlChannelServer.java`
- [x] T019 [US2] 实现服务端会话管理：生成/保存 `clientId`，维护控制通道连接状态在 `server/src/main/java/staraxis/server/SessionManager.java`
- [x] T020 [US2] 实现客户端控制通道：发送 `ClientHello`，接收 `ServerHello` 并保存 `clientId` 在 `client/src/main/java/staraxis/client/net/ControlChannelClient.java`
- [x] T021 [US2] 实现服务端数据通道接入：接收 `DataHello{clientId}` 并绑定到 `Session`（会话）在 `server/src/main/java/staraxis/server/net/DataChannelServer.java`
- [x] T022 [US2] 实现数据通道绑定失败处理：`clientId` 不存在则断开数据通道连接（FR-011）在 `server/src/main/java/staraxis/server/net/DataChannelServer.java`
- [x] T023 [US2] 实现客户端数据通道：连接后发送 `DataHello{clientId}` 在 `client/src/main/java/staraxis/client/net/DataChannelClient.java`
- [x] T024 [US2] 在服务端主循环 `PostUpdate` 阶段：对所有已绑定数据通道的会话下发 `ServerTick{serverTick}`（FR-012）在 `server/src/main/java/staraxis/server/ServerMainLoop.java`
- [x] T025 [US2] 客户端接收 `ServerTick` 并更新 `lastServerTick`（客户端最后收到的权威 tick）并输出日志在 `client/src/main/java/staraxis/client/net/DataChannelClient.java`
- [x] T026 [US2] 创建客户端启动入口（main）：启动控制通道→拿 `clientId`→启动数据通道→打印 tick 在 `client/src/main/java/staraxis/client/ClientMain.java`
- [ ] T027 [US2] 本地连接模式（FR-013）：服务端控制通道与数据通道仅监听本地地址（例如 `127.0.0.1`），并在日志中打印实际监听地址

---

## Phase 5: User Story 3 - 基础心跳用于链路存活验证 (Priority: P2)

**Goal**: 客户端每 10 秒发送 `ClientHeartbeat`（客户端心跳），服务端记录收到心跳日志；可选返回 `ServerHeartbeatAck`（心跳回执）。

**Independent Test**: 在本地连接模式下运行 60 秒，服务端至少每 10 秒输出一次心跳日志（不做丢包测试）（对应 US3 验收场景、SC-005）。

- [ ] T028 [US3] 客户端实现心跳定时器：每 10 秒发送一次 `ClientHeartbeat{clientSeq, clientTimeMs}` 在 `client/src/main/java/staraxis/client/net/ControlChannelClient.java`
- [ ] T029 [US3] 服务端控制通道接收 `ClientHeartbeat` 并更新 `lastHeartbeatAtMs`（最后一次收到心跳时间毫秒）在 `server/src/main/java/staraxis/server/net/ControlChannelServer.java`
- [ ] T030 [US3] [P] （可选）服务端返回 `ServerHeartbeatAck{clientSeq, serverTick}` 在 `server/src/main/java/staraxis/server/net/ControlChannelServer.java`
- [ ] T031 [US3] （可选）客户端接收并打印 `ServerHeartbeatAck` 在 `client/src/main/java/staraxis/client/net/ControlChannelClient.java`

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 收口验收、补齐文档与最低限度的可观测性。

- [ ] T032 将实现与实际端口/运行方式补齐到 `specs/002-loop-comms-skeleton/quickstart.md`（确保与现状一致）
- [ ] T033 [P] 在 `specs/002-loop-comms-skeleton/contracts/control-channel.md` 与 `specs/002-loop-comms-skeleton/contracts/data-channel.md` 补充“本地连接模式，不丢包测试”的一行备注（与 spec 对齐）
- [ ] T034 执行 quickstart 的手工验收流程并记录结果（在 `specs/002-loop-comms-skeleton/quickstart.md` 的“验收记录（模板）”下填入实际结果）

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1（Setup）→ Phase 2（Foundational）→ Phase 3/4/5（US1/US2/US3）→ Phase 6（Polish）

### User Story Dependencies

- **US1**（服务端主循环）是 **US2/US3** 的前置（需要服务端运行起来才有连接与 tick 下发）。
- **US2**（连接+tick）是 **US3**（心跳）的前置（心跳走控制通道，需先有会话）。

### Parallel Opportunities

- `T006` 与 `T007` 可并行（不同目录）。
- `T030` 可与 `T028`/`T029` 并行（可选项）。

---

## Implementation Strategy

### MVP First

1. 完成 Phase 1 + Phase 2
2. 完成 US1（Phase 3）：服务端主循环 25 tick/s 可跑
3. 完成 US2（Phase 4）：本地双通道连接 + tick 下发
4. 完成 US3（Phase 5）：每 10 秒心跳
5. 完成 Phase 6：更新 quickstart 与契约备注，手工验收

---

## Notes

- 本 tasks.md 不包含自动化测试任务：spec 明确“不强制单元测试”，以手工运行验收为主。
- 本特性采用 Netty（TCP + UDP）框架化实现双通道；不实现 UDP 可靠层与丢包对抗测试（SC-005）。
