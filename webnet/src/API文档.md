# StarAxis WebNet API 接口文档

本文档列出了 `webnet` 模块提供的所有 HTTP API 和 WebSocket 接口定义，供前后端联调参考。

## 1. WebSocket 接口 (/ws)

玩家主连接，用于接收实时快照、日结算数据以及发送控制命令。

### 1.1 握手与鉴权
- **路径**: `/ws?token={jwt_token}`
- **鉴权**: 必须带有效的 token 参数。
- **服务端响应**:
  ```json
  { "type": "hello", "ok": true, "server": "webnet", "playerId": "uuid" }
  ```

### 1.2 客户端发送的消息 (Client -> Server)
- **subscribeSnapshot**: 订阅快照推送。
  ```json
  { "type": "subscribeSnapshot" }
  ```
- **updateVisibleSectors**: 更新当前视野内的星区集合（后端按此过滤推送）。
  ```json
  { 
    "type": "updateVisibleSectors", 
    "sectors": [{ "q": 0, "r": 0 }, { "q": 1, "r": -1 }] 
  }
  ```
- **unsubscribeSnapshot**: 取消快照订阅。
  ```json
  { "type": "unsubscribeSnapshot" }
  ```
- **pong**: 响应服务端的心跳 ping。
  ```json
  { "type": "pong" }
  ```

### 1.3 服务端推送的消息 (Server -> Client)
- **snapshot**: 实时世界状态快照。
  - **频率**: 约 40ms/tick。
  - **结构**: 包含 `simulationTick`, `sectorCenters`, `entities`（已按可见星区过滤）。
- **ping**: 应用层心跳检测。
  ```json
  { "type": "ping" }
  ```

---

## 2. HTTP RESTful API (/api)

### 2.1 认证模块 (Auth)
- **POST /api/auth/register**: 注册新账号。
- **POST /api/auth/login**: 登录并获取 Token。
- **GET /api/auth/me**: 获取当前登录用户信息（需 Bearer Token）。
- **POST /api/auth/logout**: 注销会话。
- **POST /api/auth/gameId**: 绑定/更新当前账号的 `gameId`（存档关联）。

### 2.2 游戏管理与设置
- **GET /api/status**: 获取服务端运行状态（含连接数、运行时间）。
- **GET /api/i18n/{lang}**: 获取合并后的语言包数据。
- **GET /api/mods**: 获取已安装的 Mod 列表及其启用状态。
- **POST /api/mods/order**: 保存 Mod 加载顺序与禁用列表。

### 2.3 新游戏流程 (New Game)
- **POST /api/newgame/step1/selectNation**: 步骤1：选择开局国家。
- **POST /api/newgame/step2/worldSettings**: 步骤2：配置世界生成参数（种子、半径等）。
- **POST /api/newgame/step3/confirm**: 步骤3：确认并正式启动游戏实例。

### 2.4 国家与预设 (Nation)
- **GET /api/game/nations**: 获取所有预设国家配置。
- **GET /api/nations/players/list**: 列出当前玩家创建的所有自定义国家。
- **POST /api/nations/players/save**: 保存/更新自定义国家配置。

### 2.5 管理员接口 (需 ADMIN 权限)
- **POST /api/quit**: 安全关闭服务端进程。
- **POST /api/restart**: 重启服务端进程。

---

## 3. AI 专用接口 (/ws/ai)
专供 AI 助手接入的通道，支持工具调用协议。
- **tool: snapshot.getEntity**: 查询特定实体的详细快照。
- **tool: snapshot.getLatestSummary**: 获取当前世界的宏观统计摘要。
