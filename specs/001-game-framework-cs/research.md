# Research: Game Framework Architecture (C/S Separation)

## Decisions & Rationale

### 1. 序列化库选择 (Serialization Library)
- **Decision**: 使用 **Kryo**。
- **Rationale**: Kryo 是 Java 生态中性能极高的二进制序列化库，特别是在 LibGDX 社区中有深厚的集成基础（如 KryoNet）。相比 JSON，它能极大地减少数据包体积和 CPU 开销，这对于 20Hz 的高频同步至关重要。
- **Alternatives considered**: JSON (Jackson/Gson) - 虽易于调试但性能较差；Protobuf - 性能优异但定义繁琐，对 Java 内部对象的直接支持不如 Kryo 灵活。

### 2. 固定步进模拟 (Fixed Timestep Simulation)
- **Decision**: 在 `core` 模块中实现独立的 `SimulationLoop`，频率固定为 **20Hz**。
- **Rationale**: 保证逻辑演算与帧率无关，是防作弊和多人同步的基础。20Hz (50ms) 能在保证 4X 游戏流畅度的同时，平衡 CPU 负载。
- **Alternatives considered**: 变长步进 (Variable Timestep) - 虽实现简单但会导致不同设备计算结果不一致；更高频率 (60Hz) - 对于大战略游戏而言性能开销过大，且无明显体验提升。

### 3. 同步策略与平滑插值 (Sync & Interpolation)
- **Decision**: 采用 **状态同步 (State Sync)** + **客户端插值 (Client Interpolation)**。
- **Rationale**: 状态同步对网络延迟的容忍度更高，支持中途加入。客户端通过保留至少一个 Tick 的数据缓冲区进行插值，可以消除因网络波动造成的视觉抖动。
- **Alternatives considered**: 确定性锁步 (Lockstep) - 带宽最省但对延迟极其敏感，开发调试难度极大。

### 4. 通信基础架构 (Communication Infrastructure)
- **Decision**: 初始版本采用 **内存队列 (In-Memory Queue)** 模拟 C/S 通讯，预留 **KryoNet** 接口。
- **Rationale**: 现阶段重点是 C/S 逻辑解耦和模块化。通过内存队列可以快速验证解耦效果，同时 KryoNet 的引入可以在后期平滑切换到真实网络环境。

## Best Practices Identified
1. **Kryo 注册**: 所有通过网络传输的类必须在 C/S 两端以相同的顺序显式注册，以获得最佳性能并避免类标识符不匹配。
2. **状态分离**: `core` 逻辑应完全通过指令 (Command) 驱动，不直接暴露内部状态的可变引用给客户端。
3. **解耦检查**: `core` 模块的 `build.gradle` 应严格限制依赖，严禁出现 `gdx-backend-lwjgl3` 等图形相关的依赖包。
