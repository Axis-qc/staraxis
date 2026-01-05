# Quickstart: UI Layer & Toolchain

## 1. Adding a New UI Element
1. 在 `com.staraxis.game.client.ui.model` 中定义数据模型。
2. 在逻辑层相关位置通过 `EventBus.post()` 推送数据。
3. 在 UI View 中订阅模型变更。

## 2. Using the Toolchain
不再使用 `ls`, `mkdir`, `cp` 等终端命令处理资源。

- **初始化环境**: `./gradlew checkEnv`
- **同步资源**: `./gradlew syncAssets`
- **启动游戏**: `./gradlew run` (已集成环境检查)

## 3. Logic-UI Decoupling Verification
若想检查是否违规，运行：
`./gradlew checkDecoupling`
该任务将静态扫描 `core` 模块，若发现 `com.badlogic.gdx.scenes.scene2d` 相关引用则构建失败。
