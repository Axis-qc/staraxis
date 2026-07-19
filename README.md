# StarAxis（星轴）

一个基于 [libGDX](https://libgdx.com/) 框架开发的太空战略游戏，使用 [gdx-liftoff](https://github.com/libgdx/gdx-liftoff) 生成项目模板。

本项目是一个类《群星》(Stellaris) 的 4X 太空战略游戏，结合宏观大战略管理与微观舰船手控战斗。

## 项目结构

```
game                 权威游戏逻辑、WorldState、模拟推进、Command 执行、只读快照发布
client + lwjgl3      原生客户端、libGDX/OpenGL 世界画面渲染、输入、相机、本机 Host 模式
webnet               联机网络管理与控制网关：账号、权限、世界/房间、AI/API、原生联机同步
web                  AI 控制台 / 管理控制台：AI 对话、命令结果、摘要信息、世界/账号/房间管理
ui                   原生 Scene2D UI 层（只读展示，不写权威世界状态）
```

模块职责：

- **`game`**：纯 Java 游戏核心层，维护世界状态和游戏规则，负责模拟时间推进、命令校验和执行、世界生成、存档等。不依赖 LibGDX / UI / Web。
- **`client`**：原生客户端应用层（LibGDX `ApplicationListener`），负责主循环、输入、渲染组织、UI 初始化和 game 运行时连接。
- **`lwjgl3`**：桌面平台启动器，创建 LWJGL3 窗口并启动 `client`，负责平台兼容性和发行打包。
- **`ui`**：原生 Scene2D UI 层，提供菜单、设置、HUD、控件、JSON UI、皮肤和字体，通过 action 上抛交互，不直接修改 `WorldState`。
- **`webnet`**：联机网络管理与控制网关，提供 HTTP/WebSocket 服务，负责账号、token、权限、世界/房间管理、AI API、原生联机同步。
- **`web`**：Vue/TypeScript 前端，定位为 AI 控制台 / 管理控制台，不再作为主游戏画面客户端。

## 构建与运行

本项目使用 [Gradle](https://gradle.org/) 管理依赖。项目已包含 Gradle Wrapper，可直接使用 `gradlew.bat`（Windows）或 `./gradlew`（Linux/Mac）运行 Gradle 任务。

### 常用 Gradle 任务

按启动目标分类：

**桌面客户端（LWJGL3 + OpenGL 窗口，含渲染、UI、本地 Host 模式）**
- `lwjgl3:run` — 启动原生桌面客户端（LibGDX 窗口，主玩游戏入口）
- `lwjgl3:jar` — 构建桌面客户端可运行 jar，产物位于 `lwjgl3/build/libs`

**Headless 服务端（无图形界面，纯 TickLoop 模拟 + 控制台交互）**
- `game:run` — 启动 Headless 服务器模式（类似 Minecraft Server，不创建窗口，适合性能测试/联机服务器）

> `run` 在根项目定义为 `lwjgl3:run` 的别名，但在多模块 Gradle 工程中可能因任务解析顺序被 `game` 模块的 `application` 插件拦截，导致实际执行 `:game:run`。**如需运行桌面客户端，建议始终使用显式命令 `:lwjgl3:run`，避免歧义。**

**Webnet 网关（HTTP + WebSocket，联机/账号/AI 入口）**
- `runWeb` / `:webnet:run` — 启动 webnet 调试网关（无原生窗口，纯 HTTP + WebSocket 服务）

**构建与打包**
- `build` — 构建完整工程（含原生客户端 jar + 资源打包到发行目录）
- `dist` — 打包原生客户端发行版到 `StarAxis Game/` 目录
- `clean` — 清理发行目录
- `buildWeb` — 构建 web 前端到发行目录（`webui/`）

**其他**
- `test` — 运行单元测试
- `idea` — 生成 IntelliJ IDEA 项目文件
- `eclipse` — 生成 Eclipse 项目文件
- `cleanIdea` — 删除 IntelliJ IDEA 项目数据
- `cleanEclipse` — 删除 Eclipse 项目数据

#### Windows 示例

- `./gradlew.bat :lwjgl3:run` — 启动原生桌面客户端（主玩游戏入口，**推荐**）
- `./gradlew.bat :game:run` — 启动 Headless 服务端（纯 TickLoop，无窗口）
- `./gradlew.bat runWeb` — 启动 webnet 调试网关
- `./gradlew.bat :webnet:run` — 启动 webnet 调试网关（等价命令）

### 常用 Gradle 参数

- `--continue`: 遇到错误时继续执行任务
- `--daemon`: 使用 Gradle 守护进程运行任务
- `--offline`: 使用缓存的依赖归档文件
- `--refresh-dependencies`: 强制验证所有依赖（对快照版本有用）

> 注意：大多数非特定项目的任务都可以使用 `name:` 前缀运行，其中 `name` 应替换为特定项目的 ID。例如，`game:clean` 仅清理 `game` 项目的 `build` 文件夹。

---

## 开发文档

权威开发文档入口：[`0-docs/总目录.md`](0-docs/总目录.md)

快速上手：[`0-docs/快速上手.md`](0-docs/快速上手.md)

### 核心文档

- [运行时模块职责与联机 AI 架构](0-docs/01-底层架构/运行时模块职责与联机AI架构.md)
- [客户端设计方案](0-docs/01-底层架构/11客户端设计方案.md)
- [模拟时间](0-docs/01-底层架构/模拟时间.md)
- [项目目录结构](0-docs/游戏文件目录.md)
- [核心规范](0-docs/00-通用规范与治理/核心规范.md)

---
