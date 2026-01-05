# Feature Specification: Game Framework Architecture (C/S Separation)

**Feature Branch**: `001-game-framework-cs`  
**Created**: 2026-01-05  
**Status**: Draft  
**Input**: User description: "设计游戏框架，严格遵循客户端和服务端分离原则"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 独立的服务端逻辑模拟 (Priority: P1)

作为一名开发者，我希望游戏的所有核心逻辑（如坐标计算、资源产出、战斗判定）都在一个独立的服务端模块中运行，以便于未来支持多人模式和防止客户端作弊。

**Why this priority**: 这是项目的基石原则。没有 C/S 分离的底层支持，后续所有功能开发都会陷入混合逻辑的泥潭。

**Independent Test**: 在不启动任何图形界面的情况下，能够单独运行 `core` 模块进行单元测试和模拟演算。

**Acceptance Scenarios**:

1. **Given** 一个没有任何图形依赖的环境, **When** 启动服务端模拟循环, **Then** 能够正常处理实体位置更新和资源累加。
2. **Given** 客户端发起连接请求, **When** 服务端处理请求后, **Then** 客户端能收到同步的状态数据包，且服务端不依赖客户端的渲染反馈。

---

### User Story 2 - 响应式的客户端渲染与交互 (Priority: P2)

作为一名玩家，我希望在客户端操作时能感受到流畅的视觉表现，同时所有显示的数据都真实反映了服务端的当前状态。

**Why this priority**: 确保用户体验与服务端状态同步，验证 C/S 分离后的通信机制是否可行。

**Independent Test**: 模拟服务端发送状态更新，观察客户端是否能正确解析并更新显示，而不参与任何核心数值计算。

**Acceptance Scenarios**:

1. **Given** 服务端发送了位置更新数据, **When** 客户端接收到数据后, **Then** 画面上的舰船平滑移动到目标位置。
2. **Given** 玩家在客户端点击建造按钮, **When** 客户端发送请求到服务端, **Then** 客户端等待服务端确认后再显示建造进度。

---

### User Story 3 - 游戏模拟驱动的同步机制 (Priority: P3)

作为一名开发者，我希望游戏的逻辑步进严格遵循模拟时间，而非依赖各端不同的帧率。

**Why this priority**: 保证不同性能设备上的演算结果完全一致，是多人联机的前提。

**Independent Test**: 在不同帧率限制下运行模拟，对比相同模拟时间后的系统状态是否完全一致。

**Acceptance Scenarios**:

1. **Given** 模拟频率设定为 20Hz, **When** 运行 100 个步进, **Then** 所有实体的状态与预期一致，无论实际渲染帧率是 30fps 还是 144fps。

---

### Edge Cases

- **网络延迟/丢包处理**：当服务端数据包延迟到达时，客户端如何进行平滑补偿（插值/预测）？
- **连接中断**：客户端断开后，服务端是否能维持该实体的状态直到超时或重连？
- **指令冲突**：多个客户端同时修改同一个服务端实体时，服务端如何判定生效顺序？

## Clarifications

### Session 2026-01-05
- Q: C/S 通信协议 (Communication Protocol) → A: Binary (Kryo) (LibGDX 生态高性能序列化)
- Q: 同步模型 (Synchronization Model) → A: 状态同步 + 客户端插值 (State Sync with Interpolation)
- Q: 逻辑更新频率 (Tick Rate) → A: 20Hz (50ms per tick)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 模块化设计 (Modular Design): 系统必须采用模块化架构，`core` 模块严禁引入 LibGDX 的图形类（如 `SpriteBatch`, `Texture` 等）。
- **FR-002**: 命名与注释 (Naming & Docs): 所有方法名后必须跟括号中文说明。例：`calculatePosition(计算位置)`。
- **FR-003**: C/S 分离 (C/S Separation): 服务端逻辑必须通过 Message/Event 机制与客户端通信，禁止直接引用。使用 Kryo 进行高性能二进制序列化，采用状态同步模型。
- **FR-004**: 模拟驱动 (Simulation Driven): 核心逻辑使用固定步进时间（Fixed Timestep）更新，逻辑更新频率 (Tick Rate) 统一为 20Hz。
- **FR-005**: 扩展性 (Extensibility): 设计通用的接口层，支持未来通过插件形式注入新的服务端逻辑模块。

### Key Entities *(include if feature involves data)*

- **GameServer (服务端核心)**: 负责全局状态维护、步进驱动。
- **GameClient (客户端核心)**: 负责渲染代理、用户输入转换。
- **GameState (游戏状态)**: 序列化后的数据包，用于 C/S 同步。
- **Command (指令)**: 客户端发送给服务端的动作意图。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `core` 模块中 0 个图形库依赖。
- **SC-002**: 服务端单元测试覆盖率达到 80% 以上（核心逻辑部分）。
- **SC-003**: 在模拟高延迟环境（100ms+）下，客户端视觉位移偏差通过插值控制在可接受范围内。
- **SC-004**: 成功实现 LibGDX 项目中 `core` 模块与 `lwjgl3` 模块的单向解耦运行。
