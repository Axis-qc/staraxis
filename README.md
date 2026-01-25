# StarAxis（星轴）

一个基于 [libGDX](https://libgdx.com/) 框架开发的太空战略游戏，使用 [gdx-liftoff](https://github.com/libgdx/gdx-liftoff) 生成项目模板。

本项目是一个类《群星》(Stellaris) 的 4X 太空战略游戏，结合宏观大战略管理与微观舰船手控战斗。

## 项目结构

- `core`: 核心模块，包含所有平台共享的应用逻辑
- `lwjgl3`: 桌面平台主模块，使用 LWJGL3（旧文档中称为 'desktop'）
- `shared`: 客户端和服务端共享的数据模型和接口
- `server`: 服务端模块，负责游戏模拟演算

## 构建与运行

本项目使用 [Gradle](https://gradle.org/) 管理依赖。项目已包含 Gradle Wrapper，可直接使用 `gradlew.bat`（Windows）或 `./gradlew`（Linux/Mac）运行 Gradle 任务。

### 常用 Gradle 任务

- `build`: 构建所有项目的源代码和归档文件
- `clean`: 清理 `build` 文件夹（包含编译后的类和构建的归档文件）
- `lwjgl3:run`: 启动原生桌面版（LibGDX/LWJGL3）
- `lwjgl3:jar`: 构建原生桌面版可运行的 jar 文件，位于 `lwjgl3/build/libs`
- `webnet:run`: 启动 Web 版宿主（本地 HTTP + WebSocket，不启动 LibGDX）
- `runWeb`: 启动 Web 版（`webnet:run` 的别名）
- `test`: 运行单元测试
- `idea`: 生成 IntelliJ IDEA 项目文件
- `eclipse`: 生成 Eclipse 项目文件
- `cleanIdea`: 删除 IntelliJ IDEA 项目数据
- `cleanEclipse`: 删除 Eclipse 项目数据

#### Windows 示例

- `./gradlew.bat runWeb`: 启动 Web 版（推荐）
- `./gradlew.bat :webnet:run`: 启动 Web 版（等价命令）
- `./gradlew.bat :lwjgl3:run`: 启动原生桌面版

### 常用 Gradle 参数

- `--continue`: 遇到错误时继续执行任务
- `--daemon`: 使用 Gradle 守护进程运行任务
- `--offline`: 使用缓存的依赖归档文件
- `--refresh-dependencies`: 强制验证所有依赖（对快照版本有用）

> 注意：大多数非特定项目的任务都可以使用 `name:` 前缀运行，其中 `name` 应替换为特定项目的 ID。例如，`core:clean` 仅清理 `core` 项目的 `build` 文件夹。

---
