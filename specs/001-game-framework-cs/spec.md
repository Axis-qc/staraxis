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

### User Story 4 - 连接管理与初始同步 (Priority: P2)

作为一名玩家，我希望在启动游戏时能快速同步世界状态，并在网络波动导致断线时能获得明确的反馈。

**Why this priority**: 完整的连接生命周期管理是 C/S 架构可用性的基础。

**Independent Test**: 模拟客户端连接、同步数据、断开连接及重连流程，验证状态一致性。

**Acceptance Scenarios**:

1. **Given** 客户端发起连接, **When** 握手成功, **Then** 客户端接收到完整的世界快照 (Snapshot) 并进入逻辑步进同步状态。
2. **Given** 网络连接中断, **When** 超过心跳阈值（暂定 5s）, **Then** 客户端切换至“离线/重连中”状态，服务端保留玩家实体状态 30s。

---

### Edge Cases

- **网络延迟/丢包处理**：当服务端数据包延迟到达时，客户端采用**线性插值 (Linear Interpolation)** 补偿。对于丢失的逻辑帧包，客户端直接抛弃过时状态，等待最新包。
- **连接中断与恢复**：客户端断开后，服务端维持该实体状态直至超时（30s）。重连成功后，服务端重新推送当前 Tick 的完整快照。
- **指令冲突**：若多个指令在同一个 Tick 作用于同一目标，服务端按指令到达序列号 (Sequence Number) 顺序执行，若后续指令因前序指令执行导致失效，则服务端返回指令执行失败通知。
- **预测校解 (Reconciliation)**：若客户端本地预测位置与服务端权威状态偏差超过 0.1 逻辑单位，客户端必须执行强制位置回退同步。

## Clarifications

### Session 2026-01-05
- Q: C/S 通信协议 (Communication Protocol) → A: Binary (Kryo) (LibGDX 生态高性能序列化)
- Q: 同步模型 (Synchronization Model) → A: 状态同步 + 客户端插值 (State Sync with Interpolation)
- Q: 逻辑更新频率 (Tick Rate) → A: 20Hz (50ms per tick)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 模块化设计 (Modular Design): 系统必须采用模块化架构，`core` 模块严禁引入 LibGDX 的图形类（如 `SpriteBatch`, `Texture` 等）。通过 Gradle `checkStyle` 任务或自定义编译检查强制执行“逻辑层零图形依赖”。
- **FR-002**: 命名与注释 (Naming & Docs): 所有方法名后必须跟括号中文说明。格式示例：`void fireWeapon(开火)() { ... }` 或 `int currentPop(当前人口);`。
- **FR-003**: C/S 分离 (C/S Separation): 服务端逻辑必须通过**观察者模式 (Observer Pattern)** 驱动的消息系统与客户端通信。使用 Kryo 进行二进制序列化，采用状态同步模型。
- **FR-004**: 模拟驱动 (Simulation Driven): 核心逻辑使用固定步进时间（Fixed Timestep）更新，逻辑更新频率 (Tick Rate) 统一为 20Hz。单次 Tick 漂移量允许误差范围为 ±2ms。
- **FR-005**: 扩展性 (Extensibility): 设计通用的接口层，支持未来通过插件形式注入新的服务端逻辑模块。
- **FR-006**: 性能预算 (Performance Budget): 服务端单次 Tick 处理（不含 IO）必须在 10ms 内完成，以确保稳定的 20Hz 输出。
- **FR-007**: 指令校验 (Command Security): 服务端必须校验所有客户端指令的合法性（如移动速度、操作权限、资源消耗），严禁信任客户端发送的数值计算结果。

### Key Entities *(include if feature involves data)*

- **GameServer (服务端核心)**: 负责全局状态维护、步进驱动。
- **GameClient (客户端核心)**: 负责渲染代理、用户输入转换。
- **GameState (游戏状态)**: 序列化后的数据包，用于 C/S 同步。
- **Command (指令)**: 客户端发送给服务端的动作意图。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `core` 模块中 0 个图形库依赖。通过 `grep` 扫描 `core/src` 确保不含 `com.badlogic.gdx.graphics` 包引用。
- **SC-002**: 服务端单元测试覆盖率达到 80% 以上（核心逻辑包 `com.staraxis.game.core.*`）。
- **SC-003**: 在模拟 150ms 延迟环境（RTT）下，客户端通过线性插值使视觉抖动肉眼不可察觉（位移偏差控制在 0.05 逻辑单位内）。
- **SC-004**: 成功实现 LibGDX 项目中 `core` 模块与 `lwjgl3` 模块的单向解耦运行，且 `core` 可在 Headless 模式下独立启动。
