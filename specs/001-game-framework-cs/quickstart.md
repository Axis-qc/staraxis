# Quickstart: Game Framework Architecture (C/S Separation)

## Development Environment
- **IDE**: IntelliJ IDEA / VS Code (Windsurf)
- **JDK**: 17
- **Build Tool**: Gradle

## Module Structure
- `core`: 纯 Java 逻辑模块。
  - `src/main/java/.../core`: 服务端专有逻辑。
  - `src/main/java/.../shared`: C/S 共享代码。
- `lwjgl3`: 桌面客户端模块，依赖 `core` 中的 `shared`。

## Running the Project

### 1. 运行服务端模拟 (Headless Mode)
目前可以通过 `core` 模块的单元测试验证逻辑，或者在命令行运行 Gradle 测试：
```bash
./gradlew :core:test
```
测试包含：
- `GameServerTest`: 验证 20Hz 步进和无界面约束。
- `ConnectionSyncTest`: 验证握手和快照同步。
- `StressTest`: 验证 100 个实体的模拟性能。

### 2. 运行客户端 (LWJGL3)
启动带有图形界面的完整游戏，实时观察同步状态：
```bash
./gradlew :lwjgl3:run
```
界面左上角会显示 Server Tick、Interpolated Tick 和 Sim Time 等调试信息。

## Key Workflows

### 如何添加新实体逻辑
1. 在 `shared` 模块中定义实体的 `EntityState`。
2. 在 `core` 模块中实现对应的服务端处理系统 (System)。
3. 在 `lwjgl3` 模块中实现对应的渲染代理 (Renderer)。
4. 在 `contracts/network-protocol.md` 中注册相关的类（如果需要同步）。

### 如何调试 C/S 同步
- 查看 `GameClient` 中的同步缓冲区日志。
- 使用 `SimulationLoop` 提供的 `debugTick()` 方法手动推进逻辑帧。
