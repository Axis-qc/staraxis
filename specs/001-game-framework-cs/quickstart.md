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
目前暂未提供独立的 Server Main 类，但可以通过 `core` 模块的单元测试验证逻辑：
```bash
./gradlew :core:test
```

### 2. 运行客户端 (LWJGL3)
启动带有图形界面的完整游戏：
```bash
./gradlew :lwjgl3:run
```

## Key Workflows

### 如何添加新实体逻辑
1. 在 `shared` 模块中定义实体的 `EntityState`。
2. 在 `core` 模块中实现对应的服务端处理系统 (System)。
3. 在 `lwjgl3` 模块中实现对应的渲染代理 (Renderer)。
4. 在 `contracts/network-protocol.md` 中注册相关的类（如果需要同步）。

### 如何调试 C/S 同步
- 查看 `GameClient` 中的同步缓冲区日志。
- 使用 `SimulationLoop` 提供的 `debugTick()` 方法手动推进逻辑帧。
