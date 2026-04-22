# StarAxis Web 前端 API 使用文档

本文档面向 `web` 前端模块，列出前端会调用的 HTTP/WS 接口、调用方式与字段口径，方便查询与联调喵。

## 1. WebSocket：快照通道（/ws）

### 1.1 连接方式（connectSnapshotWs）
- **入口文件**：`web/src/net/snapshotWs.ts`（快照 WS 客户端）喵。
- **连接地址**：
  - `ws://{host}/ws?token={token}` 或 `wss://{host}/ws?token={token}` 喵。
- **鉴权口径**：
  - token 存储于 `localStorage['sa.token']`，前端连接时自动附带喵。

### 1.2 前端发送消息（Client -> Server）

#### subscribeSnapshot（订阅快照）
- **用途**：请求服务端开始推送快照通道消息喵，当前主通道为 `snapshot_high_freq`（高频快照）、`snapshot_low_freq`（低频快照）和 `command_result`（命令结果）喵。
- **发送**：WS `onopen` 后自动发送喵。
```json
{ "type": "subscribeSnapshot" }
```

#### updateVisibleSectors（更新视野星区集合）
- **用途**：上报前端当前渲染剔除范围覆盖的星区集合，服务端按此过滤实体与星区中心点推送喵。
- **实现位置**：`web/src/views/InGameView.vue`（监听相机/缩放，节流 + 滞后更新订阅集合）喵。
- **发送**：通过 `SnapshotWsClient.updateVisibleSectors(sectors)` 喵。
```json
{
  "type": "updateVisibleSectors",
  "sectors": [
    { "q": 0, "r": 0 },
    { "q": 1, "r": -1 }
  ]
}
```

#### unsubscribeSnapshot（取消订阅）
- **用途**：停止接收快照推送喵。
```json
{ "type": "unsubscribeSnapshot" }
```

#### pong（心跳回应）
- **用途**：回应服务端的 `ping`，保持连接存活喵。
```json
{ "type": "pong" }
```

### 1.3 服务端推送消息（Server -> Client）

#### ping（心跳）
- **用途**：服务端心跳探测，前端收到后回复 `pong` 喵。
```json
{ "type": "ping" }
```

#### snapshot_high_freq（高频快照）
- **用途**：只服务实时渲染与实体缓存喵。
- **接收位置**：`connectSnapshotWs({ onHighFreqSnapshot })` 回调喵。
- **关键字段**：
  - `simulationTick`：高频权威 Tick 喵。
  - `totalGameSecondsExact`：高频连续时间基线喵。
  - `entities`：当前订阅范围内的高频实体快照喵。
  - `privateEntitiesByIntelLevel`：私有情报实体分层喵。

#### snapshot_low_freq（低频快照）
- **用途**：只服务时间 HUD、星区元数据、地表/面板等低频 UI 喵。
- **接收位置**：`connectSnapshotWs({ onLowFreqSnapshot })` 回调喵。
- **关键字段**：
  - `version`：低频状态版本号喵。
  - `sectorCenters`：当前订阅星区中心点（`q,r,x,y`）喵。
  - `sectorOwnerNationIdByCoord`：星区归属喵。
  - `dailySettlementState`：结算/地表等面板数据喵。

#### command_result（命令结果）
- **用途**：推进命令 UI 状态喵。
- **接收位置**：`connectSnapshotWs({ onCommandResult })` 回调喵。
- **关键字段**：
  - `clientCommandId`：前端命令 ID 喵。
  - `entityId`：命令目标实体喵。
  - `resultType`：`submitted/accepted/rejected/completed/corrected` 喵。
  - `simulationTick`：该结果对应的权威 Tick 喵。

#### snapshot（兼容整包快照）
- **用途**：旧协议兼容兜底喵，不再是前端主更新链路喵。
- **接收位置**：`connectSnapshotWs({ onSnapshot })` 回调喵。

`SnapshotMessage`（兼容结构）喵：
```json
{
  "type": "snapshot",
  "ok": true,
  "tickCostMs": 1,
  "realTimeWorldState": {
    "simulationTick": 123,
    "gameDatetimeDay": 0,
    "accGameHoursInDay": 1.0,
    "worldRadius": 8,
    "sectorCenters": [
      { "q": 0, "r": 0, "x": 0.0, "y": 0.0 }
    ],
    "entities": [
      {
        "entityId": 1,
        "entityType": "STAR",
        "systemId": 100,
        "parentEntityId": 0,
        "sectorCoord": { "q": 0, "r": 0 },
        "posWorldGU": { "x": 0.0, "y": 0.0 },
        "details": { "starTypeId": "G", "radiusGU": 1.0 }
      }
    ]
  },
  "dailySettlementState": {
    "settledDay": 0,
    "sectorCount": 91,
    "planetSurfaces": {}
  }
}
```

### 1.4 视野订阅与渲染剔除对齐（重要口径）
- **剔除口径来源**：`WorldRenderer.getCullingAabbGU()`（由 `frameStateBuilder` 计算的 `cullingAabb`）喵。
- **订阅范围**：在 `cullingAabb` 基础上扩张（`SUBSCRIBE_AABB_SCALE`）后计算星区集合喵。
- **滞后/节流**：
  - `REPORT_THROTTLE_MS`：限制上报频率喵。
  - `UNSUBSCRIBE_GRACE_MS`：退订延迟，避免镜头微动造成 pop-in/pop-out 喵。
- **缓存清理**：每 5 分钟清理一次不在订阅集合且未选中的实体/星区中心，防止缓存增长喵。

---

## 2. HTTP：基础请求封装（services/backend.ts）

### 2.1 authFetch（统一鉴权请求）
- **入口文件**：`web/src/services/backend.ts` 喵。
- **作用**：自动从 `localStorage['sa.token']` 读取 token 并加到 Header：
  - `Authorization: Bearer {token}` 喵。

### 2.2 状态查询

#### GET /api/status（fetchStatus）
- **用途**：首页/控制台显示服务端状态喵。
- **调用**：`fetchStatus()` 喵。

### 2.3 认证接口

#### POST /api/auth/register（authRegister）
- **用途**：注册账号喵。
- **请求体**：`{ username, password }` 喵。

#### POST /api/auth/login（authLogin）
- **用途**：登录并获取 token 喵。
- **请求体**：`{ username, password }` 喵。

#### GET /api/auth/me（authMe）
- **用途**：获取当前登录态信息（含 playerId/gameId/role）喵。
- **鉴权**：Bearer token喵。

#### POST /api/auth/logout（authLogout）
- **用途**：注销喵。
- **鉴权**：Bearer token喵。

#### POST /api/auth/gameId（authSetGameId）
- **用途**：设置当前账号 gameId（存档关联）喵。
- **鉴权**：Bearer token喵。
- **请求体**：`{ gameId }` 喵。

### 2.4 Mods

#### GET /api/mods（fetchMods）
- **用途**：读取 Mods 列表、顺序与禁用集合喵。

#### POST /api/mods/order（saveMods）
- **用途**：保存 Mods 顺序与禁用集合喵。
- **请求体**：`{ order: string[], disabled: string[] }` 喵。

### 2.5 管理接口（ADMIN）

#### POST /api/quit（requestQuit）
- **用途**：关闭进程喵。
- **鉴权**：Bearer token（需 ADMIN 权限）喵。

#### POST /api/restart（requestRestart）
- **用途**：重启进程喵。
- **鉴权**：Bearer token（需 ADMIN 权限）喵。

---

## 3. 其它说明
- 本文档为“前端视角”的使用说明，后端权威接口定义见 `webnet/src/API文档.md` 喵。
