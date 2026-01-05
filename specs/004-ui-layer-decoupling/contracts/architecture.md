# UI Layer & Toolchain Contracts

## 1. UIManager Interface
负责顶层渲染调度和页面切换。

### `void render(SpriteBatch batch, float delta)`
- **Role**: 渲染入口委派。
- **Contract**: 必须先渲染游戏世界（GameViewport），再渲染 UI 覆盖层。

### `void navigateTo(String screenId)`
- **Role**: 页面路由。

## 2. EventBus Contract
逻辑层与 UI 层的唯一通信通道。

### `void post(Object event)`
- **Location**: `core` 模块定义接口，`lwjgl3` 模块实现。

## 3. Gradle Toolchain API
预定义的自动化任务命令。

| Task Name | Command | Description |
|-----------|---------|-------------|
| `checkEnv` | `./gradlew checkEnv` | 检查 JDK、资源目录及依赖一致性 |
| `syncAssets` | `./gradlew syncAssets` | 同步并校验 assets 目录下的所有资源 |
| `runFullTests` | `./gradlew runFullTests` | 顺序运行所有模块的单元测试和集成测试 |
