# 运行时模块职责与联机 AI 架构

> 状态：当前决策口径  
> 更新时间：2026-06-14  
> 目标：明确 `game`、`client`、`lwjgl3`、`webnet`、`web` 在后续重构中的职责边界，避免继续把浏览器渲染链路作为主客户端方向。

---

## 1. 总体结论

StarAxis 后续运行时架构按以下职责划分：

```text
game
  负责游戏主逻辑、权威模拟、世界状态、命令执行。

client + lwjgl3
  负责原生客户端、OpenGL/libGDX 渲染、输入、相机、本机 Host 模式。

webnet
  后续转为联机网络管理与控制网关：
  - 多人联机会话
  - 远程原生客户端同步
  - 账号/权限/世界管理
  - AI 工具/API 网关
  - 浏览器控制台后端

web
  不再作为主游戏画面客户端。
  后续改为 AI 控制台 / 管理控制台：
  - 玩家与 AI 对话
  - AI 调用受权限保护的 API 控制玩家单位
  - 世界/账号/房间管理
  - 不渲染高频世界画面
```

核心原则：

1. **游戏主逻辑在 `game`**。  
   `WorldState`、`StarAxisGameRuntime`、`CommandBus`、模拟系统与快照发布都属于 `game`。

2. **原生客户端负责画面**。  
   `client`/`lwjgl3` 读取 `RealTimeWorldState` 或网络同步帧后进行 2D 原生渲染。

3. **浏览器不再承担世界渲染**。  
   `web` 从 Three.js 世界客户端转为 AI 控制台与管理界面。

4. **webnet 后续不是浏览器画面推送器，而是联机网络管理层**。  
   它可以继续提供 WebSocket/HTTP，但主要用于联机同步、AI 工具、权限、管理，而不是给浏览器推送高频画面。

---

## 2. 模块职责边界

### 2.1 `game`：主逻辑与权威模拟

`game` 是唯一权威游戏逻辑模块。

职责：

- 创建与维护 `WorldState`。
- 推进 `simulationTick` 与游戏时间。
- 执行玩家/AI/系统命令。
- 运行移动、殖民、时间、可见性、经济、战斗等模拟系统。
- 发布只读状态：
  - `RealTimeWorldState`
  - `DailySettlementState`
- 提供命令入口：
  - `StarAxisGameRuntime.submitCommand(...)`
  - `CommandBus`

强制约束：

- 除模拟层外，其他层不得直接写 `WorldState`。
- 外部控制必须转换为 `Command`。
- 渲染层优先读取 `RealTimeWorldState`，不要直接遍历正在被模拟修改的 `WorldState`。
- AI/API/网络层不能绕过 `CommandBus` 修改游戏结果。

---

### 2.2 `client`：原生客户端逻辑与渲染

`client` 是原生游戏客户端主体。

职责：

- 持有原生渲染器。
- 持有输入系统、相机系统、选择系统、交互系统。
- 本机 Host 模式下直接持有并推进 `StarAxisGameRuntime`。
- 远程 Client 模式下连接服务器并渲染网络同步状态。
- 将玩家输入转换为本地命令或网络命令请求。

本机 Host 模式：

```text
ClientGame
  -> StarAxisGameRuntime.newGame(...)
  -> runtime.start()
  -> 每帧 runtime.update(dt)
  -> renderer.render(runtime.getRealTimeWorldStateReadonly())
  -> input -> runtime.submitCommand(...)
```

远程 Client 模式：

```text
ClientGame
  -> connect GameHost/webnet native endpoint
  -> receive NativeWorldFrame
  -> renderer.render(remoteFrame)
  -> input -> send PlayerCommandRequest
```

强制约束：

- 原生客户端渲染世界画面。
- 本机 Host 可以直接读内存中的 `RealTimeWorldState`。
- 远程 Client 不能直接读服务器内存，必须通过网络同步帧渲染。
- 原生客户端不拥有权威规则计算，除本机 Host 模式外不得推进权威模拟。

---

### 2.3 `lwjgl3`：桌面启动器

`lwjgl3` 是桌面平台启动模块。

职责：

- 创建 LWJGL/libGDX 应用窗口。
- 加载平台图标、启动参数和桌面配置。
- 启动 `client` 中的 `ClientGame`。
- 不承载游戏规则。

---

### 2.4 `webnet`：联机网络管理与控制网关

`webnet` 后续定位为网络与管理层，而不是浏览器画面层。

职责：

- 管理联机世界/房间/会话。
- 持有 Dedicated Server 或 Host 暴露的 `StarAxisGameRuntime`。
- 为远程原生客户端提供状态同步 endpoint。
- 为远程原生客户端接收命令请求。
- 为浏览器 AI 控制台提供 HTTP/WebSocket API。
- 做账号、token、权限、玩家-国家绑定、审计、限流。
- 提供 AI 工具网关，将 AI tool 调用转换为游戏命令或只读查询。

可保留/演进的能力：

- `/api/auth/*`
- `/api/worlds/*`
- `/api/ai/chat`
- `/ws/ai`
- 原生联机 endpoint，例如 `/ws/native` 或后续 TCP/UDP 通道

应弱化或迁移的能力：

- 面向浏览器画面的高频 snapshot 推送。
- 浏览器世界渲染专用订阅逻辑。
- 直接服务 Three.js 世界视口的数据路径。

强制约束：

- `webnet` 不能成为第二套游戏规则实现。
- `webnet` 只能通过 `game` 的只读快照和命令入口与权威模拟交互。
- 所有写操作必须经过玩家权限检查，再提交 `Command`。

---

### 2.5 `web`：AI 控制台与管理界面

`web` 后续不再作为主游戏画面客户端。

职责：

- 登录/账号界面。
- 世界/房间/服务器管理界面。
- AI 聊天控制台。
- AI 工具调用状态展示。
- 玩家用自然语言或表单向 AI/控制 API 下达意图。
- 显示摘要、日志、命令执行结果、单位列表等低频信息。

不再负责：

- Three.js 世界画面渲染。
- 高频实体位置插值。
- 星区/星系/舰船实时画面渲染。
- 作为主要玩家操作视口。

浏览器控制流程：

```text
玩家在浏览器输入：
  “让殖民船去最近的宜居星球殖民”

web
  -> /api/ai/chat

AI 系统
  -> tool: world.getKnownSummary
  -> tool: unit.listOwn
  -> tool: planet.findColonizable
  -> tool: unit.move / planet.colonize

webnet AI tool gateway
  -> 校验 token
  -> 校验 playerId/nationId
  -> 校验单位归属与视野权限
  -> runtime.submitCommand(...)
```

---

## 3. 运行模式

### 3.1 单机 / 本机 Host 模式

```text
lwjgl3 process
  ├─ client
  │   ├─ Native renderer
  │   ├─ Input/camera/selection
  │   └─ StarAxisGameRuntime
  └─ optional embedded control server
      ├─ AI API
      └─ browser control API
```

特点：

- 原生客户端直接读内存中的 `RealTimeWorldState`。
- 输入直接提交到 `runtime.submitCommand(...)`。
- 可选启动本地控制服务器供浏览器 AI 控制台连接。
- 适合单机、调试、房主开局。

---

### 3.2 Dedicated Server / 正规联机模式

```text
server process
  ├─ game
  │   └─ StarAxisGameRuntime
  └─ webnet
      ├─ native multiplayer endpoint
      ├─ AI tool/API gateway
      └─ auth/world/session management

native client A
  └─ network sync + OpenGL render

native client B
  └─ network sync + OpenGL render

browser
  └─ AI console / admin console
```

特点：

- 服务器持有唯一权威 `StarAxisGameRuntime`。
- 所有远程原生客户端只接收状态帧并提交命令请求。
- 浏览器只做 AI 控制台/管理，不渲染世界。
- 适合多人联机与远程服务器。

---

## 4. 原生联机协议方向

早期优先使用 WebSocket JSON 跑通逻辑，后续再视性能迁移到二进制协议。

### 4.1 服务端到客户端：状态同步帧

建议数据模型：

```text
NativeWorldFrame
  worldId
  playerId
  simulationTick
  totalGameSeconds
  visibleSectors
  entities
  changedEntities
  removedEntities
  sectorOwners
```

要求：

- 按玩家视野/情报权限过滤。
- 可先全量推送 MVP，后续改增量 delta。
- 只包含渲染和 UI 必要字段，不暴露完整 `WorldState`。

### 4.2 客户端到服务端：命令请求

建议数据模型：

```text
PlayerCommandRequest
  requestId
  worldId
  playerId
  commandType
  args
```

服务端处理：

```text
token/session 校验
  -> worldId 校验
  -> playerId/nationId 绑定校验
  -> command 权限校验
  -> 转换为 game Command
  -> runtime.submitCommand(...)
```

### 4.3 服务端到客户端：命令确认

建议数据模型：

```text
CommandAck
  requestId
  ok
  error
  acceptedTick
```

---

## 5. AI 工具/API 方向

浏览器 AI 控制台与外部 AI 不直接改状态，只调用受控工具。

推荐工具：

```text
world.getKnownSummary
world.findNearest
unit.listOwn
unit.get
unit.move
unit.stop
unit.patrol
unit.attack
planet.listKnown
planet.get
planet.colonize
time.get
time.setSpeed
```

每个工具必须经过：

```text
Authorization token
  -> AuthStore.Session
  -> playerId
  -> nationId
  -> 权限检查
  -> 查询只读快照或提交 Command
```

AI 工具只允许两类行为：

1. **查询**：读取 `RealTimeWorldState`、`DailySettlementState` 或专用 ViewModel。
2. **控制**：提交 `Command`，由模拟层在 tick 内执行。

禁止：

- AI 直接写 `WorldState`。
- AI 获取超出玩家权限的完整世界状态。
- AI 绕过战争迷雾、视野和单位归属检查。

---

## 6. 迁移路线

### Phase 1：文档与职责冻结

- 明确 `game` 主逻辑。
- 明确 `client` 原生渲染。
- 明确 `webnet` 后续做联机网络管理。
- 明确 `web` 改 AI 控制台，不再作为主画面客户端。

### Phase 2：原生本机 Host MVP

- `ClientGame` 创建并持有 `StarAxisGameRuntime`。
- 原生渲染器读取 `RealTimeWorldState`。
- 输入转换为 `Command` 并直接提交。
- 先不做远程联机。

### Phase 3：浏览器 AI 控制台 MVP

- 精简 `web` 为登录 + AI 控制台 + 世界管理。
- 扩展 `webnet` AI tool。
- AI tool 网关接入 `runtime.submitCommand(...)`。
- 查询结果按玩家权限过滤。

### Phase 4：原生联机 MVP

- 在 `webnet` 增加原生联机 endpoint。
- 远程原生客户端连接服务器。
- 服务端发送 `NativeWorldFrame`。
- 客户端发送 `PlayerCommandRequest`。
- 服务端返回 `CommandAck`。

### Phase 5：性能与协议优化

- 状态帧从全量改增量。
- WebSocket JSON 改 WebSocket binary 或专用协议。
- 增加客户端预测/插值。
- 增加服务器限流、审计、断线重连、重放保护。

---

## 7. 与旧浏览器渲染链路的关系

旧链路：

```text
game -> webnet snapshot -> web Three.js render
```

后续不再作为主方向。

保留价值：

- 参考渲染逻辑与交互设计。
- 迁移到原生渲染时作为功能对照。
- 调试期可保留部分摘要/诊断页面。

目标链路：

```text
本机 Host:
game -> client native renderer

远程联机:
game -> webnet/native sync -> client native renderer

浏览器 AI:
web -> webnet AI/API -> game CommandBus
```

---

## 8. 开发注意事项

- 新功能优先写入 `game`，不要写到 `webnet` 或 `web` 里形成第二套规则。
- 原生渲染优先适配 `RealTimeWorldState`。
- 远程同步帧可以从 `RealTimeWorldState` 派生。
- 浏览器只展示摘要和 AI 交互，不追求实时画面。
- 所有玩家/AI 控制必须统一走 `Command`。
- 后续如果拆出独立 `gamehost` 模块，应只承载运行时编排、权限、同步帧构建，不承载规则计算。