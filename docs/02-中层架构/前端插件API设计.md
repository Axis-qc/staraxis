# Web 控制台扩展 API 设计

> 创建时间：2026-06-13  
> 更新口径：2026-06-14  
> 状态：规划中
>
> 本文档描述 `web` 作为 **AI 控制台 / 管理控制台** 时可暴露的浏览器侧扩展 API。  
> `web` 不再作为主游戏画面客户端，不提供 Three.js 世界画面渲染插件接口，也不暴露高频实体同步接口。

---

## 一、设计目标

在 `web` 控制台页面中暴露统一的浏览器侧 API 对象，供调试脚本、控制台页面模块、未来受控扩展调用。

推荐对象名：

```typescript
window.StarAxisConsoleAPI
```

如果历史代码已经暴露 `window.StarAxisAPI`，可在过渡期保留兼容别名，但新文档、新代码应优先使用 `window.StarAxisConsoleAPI`。

### 只做

- 读取当前玩家可见的低频摘要数据。
- 读取账号、世界、房间、服务器状态等管理信息。
- 读取 AI 对话、tool 调用、命令执行结果和审计日志。
- 发起受控的 `Command` 请求，由 `webnet` 校验权限后提交给 `game` 权威运行时。
- 提供清晰的 TypeScript 类型定义。
- 作为 AI 控制台与管理控制台的页面级扩展点。

### 不做

- 不提供主游戏画面渲染接口。
- 不提供 Three.js 场景、相机、材质、实体插值等接口。
- 不订阅高频实体位置帧。
- 不直接修改 `WorldState`。
- 不绕过 `webnet` 的 token、playerId、nationId、视野与单位归属校验。
- 不在 web 侧实现第二套游戏规则。

---

## 二、边界与调用链

### 2.1 只读查询

```text
web 控制台页面 / 扩展脚本
  -> window.StarAxisConsoleAPI.getWorldSummary()
  -> webnet 只读 API
  -> game 只读快照 / 摘要 DTO
```

要求：

- 只能返回当前账号/玩家有权限查看的数据。
- 返回低频摘要 DTO，不返回权威实体对象。
- 可返回 `DailySettlementState` 摘要、玩家国家摘要、房间状态、服务器状态等。

### 2.2 写操作 / 指令请求

```text
web 控制台页面 / AI tool / 扩展脚本
  -> window.StarAxisConsoleAPI.submitCommand(...)
  -> webnet 鉴权、限流、审计、命令合法性初筛
  -> game CommandBus / runtime.submitCommand(...)
  -> CommandAck / CommandResult
```

要求：

- 所有会改变游戏结果的操作必须转换为 `Command`。
- `web` 只负责发起请求和展示结果。
- `webnet` 负责鉴权、限流、审计、玩家/国家/单位归属校验。
- `game` 负责最终规则校验与执行。
- API 返回的是 `CommandAck` / `CommandResult`，不是直接修改后的权威对象引用。

### 2.3 AI tool 调用

```text
AI 对话面板
  -> tool request
  -> webnet AI tool gateway
  -> 只读查询或 Command 请求
  -> tool result
  -> web 展示结果与审计记录
```

要求：

- AI tool 不能直接访问或修改权威 `WorldState`。
- AI 的写操作与玩家操作一样走 `Command`。
- 每次 tool 调用应保留审计记录：调用者、时间、参数、权限结果、执行结果。

---

## 三、全局 API 对象草案

```typescript
// web/src/api/consoleApi.ts

interface StarAxisConsoleAPI {
  // === 会话 / 权限 ===
  getSession(): Promise<SessionInfo>
  getCurrentPlayer(): Promise<PlayerSummary>
  getCurrentNation(): Promise<NationSummary>

  // === 世界 / 房间 / 服务器摘要 ===
  getWorldSummary(worldId?: string): Promise<WorldSummary>
  getRoomSummary(roomId?: string): Promise<RoomSummary>
  getServerStatus(): Promise<ServerStatus>

  // === 游戏低频摘要 ===
  getDailySettlementSummary(worldId?: string): Promise<DailySettlementSummary>
  getVisibleFleetsSummary(worldId?: string): Promise<FleetSummary[]>
  getVisibleSystemsSummary(worldId?: string): Promise<SystemSummary[]>

  // === AI 控制台 ===
  getAiConversations(): Promise<AiConversationSummary[]>
  getAiMessages(conversationId: string): Promise<AiMessage[]>
  sendAiMessage(request: SendAiMessageRequest): Promise<AiMessageResult>
  getToolCallLog(filter?: ToolCallLogFilter): Promise<ToolCallLogEntry[]>

  // === 受控命令提交 ===
  submitCommand(command: ConsoleCommandRequest): Promise<CommandAck>
  getCommandResult(commandId: string): Promise<CommandResult>

  // === 事件监听（低频 / 管理事件，不是主画面高频帧） ===
  onConsoleEvent(callback: (event: ConsoleEvent) => void): () => void
}

declare global {
  interface Window {
    StarAxisConsoleAPI: StarAxisConsoleAPI

    /**
     * 过渡期兼容别名。
     * 新代码应使用 StarAxisConsoleAPI。
     */
    StarAxisAPI?: StarAxisConsoleAPI
  }
}
```

---

## 四、数据类型草案

```typescript
interface SessionInfo {
  tokenExpiresAt: string
  accountId: string
  playerId: string
  permissions: string[]
}

interface PlayerSummary {
  playerId: string
  displayName: string
  nationId?: string
}

interface NationSummary {
  nationId: string
  name: string
  color?: string
}

interface WorldSummary {
  worldId: string
  name: string
  simulationTick: number
  gameDatetime: string
  totalSystems: number
  totalPlanets: number
  knownSystems: number
  knownFleets: number
}

interface RoomSummary {
  roomId: string
  worldId: string
  name: string
  status: 'creating' | 'running' | 'paused' | 'closed'
  playerCount: number
  maxPlayers: number
}

interface ServerStatus {
  status: 'starting' | 'running' | 'degraded' | 'maintenance' | 'offline'
  version: string
  onlinePlayers: number
  activeWorlds: number
}

interface DailySettlementSummary {
  worldId: string
  day: number
  generatedAtTick: number
  economy?: Record<string, unknown>
  production?: Record<string, unknown>
  population?: Record<string, unknown>
}

interface FleetSummary {
  fleetId: string
  name: string
  ownerNationId: string
  visible: boolean
  coarsePosition?: {
    x: number
    y: number
  }
  status: string
}

interface SystemSummary {
  systemId: string
  name: string
  visible: boolean
  explored: boolean
  coarsePosition?: {
    x: number
    y: number
  }
}

interface SendAiMessageRequest {
  conversationId?: string
  text: string
  worldId?: string
  roomId?: string
}

interface AiMessageResult {
  conversationId: string
  messageId: string
  status: 'accepted' | 'completed' | 'failed'
}

interface AiConversationSummary {
  conversationId: string
  title: string
  updatedAt: string
}

interface AiMessage {
  messageId: string
  role: 'user' | 'assistant' | 'tool' | 'system'
  content: string
  createdAt: string
}

interface ToolCallLogFilter {
  conversationId?: string
  commandId?: string
  from?: string
  to?: string
}

interface ToolCallLogEntry {
  id: string
  conversationId?: string
  toolName: string
  argumentsJson: string
  permissionResult: 'allowed' | 'denied'
  resultSummary: string
  createdAt: string
}

interface ConsoleCommandRequest {
  worldId: string
  commandType: string
  payload: Record<string, unknown>
  idempotencyKey?: string
}

interface CommandAck {
  accepted: boolean
  commandId: string
  message?: string
}

interface CommandResult {
  commandId: string
  status: 'pending' | 'applied' | 'rejected' | 'failed'
  message?: string
  auditId?: string
}

type ConsoleEvent =
  | {
      type: 'command_result'
      commandId: string
      result: CommandResult
    }
  | {
      type: 'ai_message'
      conversationId: string
      message: AiMessage
    }
  | {
      type: 'room_status'
      room: RoomSummary
    }
  | {
      type: 'world_summary_updated'
      worldId: string
      simulationTick: number
    }
```

---

## 五、实现步骤

### 1. 创建控制台 API 包装层

建议路径：

```text
web/src/api/consoleApi.ts
```

职责：

- 封装 `webnet` HTTP/WebSocket 调用。
- 统一处理 token、错误码、重试、超时。
- 暴露 `StarAxisConsoleAPI` 类型与实现。

### 2. 初始化全局对象

在 `web/src/main.ts` 或控制台应用入口中初始化：

```typescript
import { createStarAxisConsoleAPI } from './api/consoleApi'

const consoleApi = createStarAxisConsoleAPI()
window.StarAxisConsoleAPI = consoleApi
window.StarAxisAPI = consoleApi // 仅过渡期兼容
```

### 3. 添加类型定义

建议路径：

```text
web/src/types/console-api.ts
```

### 4. 收敛旧实时画面接口

旧的 `/ws/game`、`snapshotWs.ts`、高频实体同步 store 如果仍存在，应按以下方式处理：

- 可临时保留为诊断/迁移工具。
- 不继续作为主画面客户端接口扩展。
- 新增控制台功能不得依赖它们完成主游戏操作。
- 若需要世界状态展示，只展示低频摘要或审计结果。

---

## 六、使用示例

```javascript
// 在 web 控制台页面 DevTools 中测试
const summary = await window.StarAxisConsoleAPI.getWorldSummary()
console.log('世界摘要:', summary)

const result = await window.StarAxisConsoleAPI.submitCommand({
  worldId: summary.worldId,
  commandType: 'fleet.move.request',
  payload: {
    fleetId: 'fleet-001',
    target: { x: 1000, y: 2000 }
  },
  idempotencyKey: crypto.randomUUID()
})

console.log('命令受理结果:', result)

const unsubscribe = window.StarAxisConsoleAPI.onConsoleEvent((event) => {
  console.log('控制台事件:', event)
})

// 取消监听
unsubscribe()
```

---

## 七、相关文档

- `0-docs/01-底层架构/运行时模块职责与联机AI架构.md` - 最新运行时职责与联机/AI 架构
- `0-docs/02-中层架构/AI插件化架构设计.md` - AI 插件化架构设计
- `0-docs/快速上手.md` - 项目快速上手与硬规则
- `web/src/net/snapshotWs.ts` - 旧实时同步接口，后续仅作迁移/诊断参考
- `web/src/stores/` - 前端状态管理