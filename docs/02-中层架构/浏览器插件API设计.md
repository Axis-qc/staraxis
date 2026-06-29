# 浏览器插件 API 设计

> 创建时间：2026-06-13
> 状态：规划中

## 一、设计原则

### 核心理念
**复用前端已有的API，不给后端增加额外负担**

- 浏览器插件通过注入脚本到游戏页面，直接调用前端已有的数据获取和操作逻辑
- 不需要后端单独开发REST API
- 复用已有的WebSocket连接和玩家认证机制

### 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        浏览器                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────┐    postMessage    ┌─────────────────┐ │
│  │  游戏页面        │ ◄──────────────► │  AI 插件面板    │ │
│  │  (web/src)      │                  │  (浏览器扩展)   │ │
│  └────────┬────────┘                  └────────┬────────┘ │
│           │                                    │          │
│           │ 暴露 window.StarAxisAPI            │          │
│           ▼                                    │          │
│  ┌─────────────────┐                          │          │
│  │  WebSocket 连接  │ ◄────────────────────────┘          │
│  │  (webnet)       │    复用连接和认证                     │
│  └─────────────────┘                                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、API 接口设计

### 2.1 全局 API 对象

在前端代码中暴露 `window.StarAxisAPI` 对象供插件调用：

```typescript
// web/src/api/pluginApi.ts

interface StarAxisAPI {
  // === 只读数据 ===
  getWorldSummary(): WorldSummary
  getEntities(type: EntityType): Entity[]
  getEntity(id: number): Entity | null
  getPlayerFleets(): Fleet[]
  getPlayerNation(): Nation
  
  // === 实时数据 ===
  getCurrentTick(): number
  getGameDatetime(): string
  
  // === 操作命令 ===
  moveFleet(fleetId: number, target: { x: number; y: number }): Promise<CommandResult>
  stopFleet(fleetId: number): Promise<CommandResult>
  
  // === 事件监听 ===
  onStateUpdate(callback: (data: StateUpdate) => void): () => void
  onCommandResult(callback: (result: CommandResult) => void): () => void
  
  // === 工具函数 ===
  calculateDistance(from: Position, to: Position): number
  formatNumber(num: number): string
}

declare global {
  interface Window {
    StarAxisAPI: StarAxisAPI
  }
}
```

### 2.2 数据类型定义

```typescript
// 世界概览
interface WorldSummary {
  tick: number
  gameDatetime: string
  totalSystems: number
  totalPlanets: number
  totalFleets: number
  playerNation: NationSummary
}

// 实体类型
type EntityType = 'STAR' | 'PLANET' | 'SYSTEM_BARYCENTER' | 'SHIP' | 'STATION'

// 实体基础信息
interface Entity {
  id: number
  type: EntityType
  name: string
  position: { x: number; y: number }
  sectorCoord: { q: number; r: number }
  details: StarDetails | PlanetDetails | ShipDetails
}

// 舰队信息
interface Fleet {
  id: number
  name: string
  ships: Ship[]
  position: { x: number; y: number }
  isMoving: boolean
  movementTarget?: { x: number; y: number }
}

// 命令结果
interface CommandResult {
  success: boolean
  commandId: string
  message?: string
}

// 状态更新事件
interface StateUpdate {
  type: 'high_freq' | 'low_freq'
  tick: number
  entities: Map<number, Entity>
}
```

---

## 三、插件接入方式

### 3.1 Content Script 注入

```javascript
// 插件的 manifest.json
{
  "content_scripts": [
    {
      "matches": ["http://localhost:*/*", "http://127.0.0.1:*/*"],
      "js": ["content.js"],
      "run_at": "document_end"
    }
  ]
}

// content.js
const script = document.createElement('script');
script.src = chrome.runtime.getURL('injected.js');
document.head.appendChild(script);

// 监听插件面板的消息
window.addEventListener('message', (event) => {
  if (event.data.source === 'staraxis-ai-plugin') {
    handlePluginRequest(event.data);
  }
});
```

### 3.2 插件面板调用示例

```javascript
// 插件面板（popup.js 或 sidepanel.js）

// 获取世界概览
const summary = await callGameAPI('getWorldSummary');
console.log('世界概览:', summary);

// 获取玩家舰队
const fleets = await callGameAPI('getPlayerFleets');
console.log('玩家舰队:', fleets);

// 移动舰队
const result = await callGameAPI('moveFleet', [fleetId, { x: 100, y: 200 }]);
console.log('移动结果:', result);

// 监听状态更新
await callGameAPI('onStateUpdate', [(data) => {
  console.log('状态更新:', data);
}]);

// 通用调用函数
function callGameAPI(method, args = []) {
  return new Promise((resolve, reject) => {
    const requestId = Math.random().toString(36).substr(2, 9);
    
    window.postMessage({
      source: 'staraxis-ai-plugin',
      requestId,
      method,
      args
    }, '*');
    
    const handler = (event) => {
      if (event.data.source === 'staraxis-game' && event.data.requestId === requestId) {
        window.removeEventListener('message', handler);
        if (event.data.error) {
          reject(new Error(event.data.error));
        } else {
          resolve(event.data.result);
        }
      }
    };
    
    window.addEventListener('message', handler);
    
    // 超时处理
    setTimeout(() => {
      window.removeEventListener('message', handler);
      reject(new Error('API call timeout'));
    }, 5000);
  });
}
```

---

## 四、安全考虑

### 4.1 权限控制
- 只暴露只读数据API，敏感操作需要二次确认
- 操作API（如移动舰队）需要玩家在游戏界面确认
- 限制API调用频率，防止滥用

### 4.2 数据隔离
- 插件只能访问当前玩家的数据
- 不能获取其他玩家的私有信息
- 不能绕过游戏规则（如战争迷雾）

### 4.3 消息验证
- 验证消息来源，只处理来自可信源的消息
- 使用 requestId 匹配请求和响应
- 设置超时机制，防止内存泄漏

---

## 五、实现步骤

### Phase 1：暴露基础API（短期）
1. 在 `web/src/api/` 目录下创建 `pluginApi.ts`
2. 实现只读数据API（getWorldSummary, getEntities 等）
3. 在 `main.ts` 中初始化并暴露到 `window.StarAxisAPI`
4. 添加 TypeScript 类型定义

### Phase 2：添加操作API（中期）
1. 实现操作命令API（moveFleet, stopFleet 等）
2. 添加确认机制，敏感操作需要玩家确认
3. 实现事件监听系统

### Phase 3：发布插件SDK（长期）
1. 创建示例浏览器扩展
2. 编写插件开发文档
3. 发布 npm 包 `@staraxis/plugin-sdk`

---

## 六、示例插件项目结构

```
staraxis-ai-plugin/
├── manifest.json           # 插件配置
├── popup/
│   ├── popup.html         # 插件弹窗界面
│   ├── popup.js           # 弹窗逻辑
│   └── popup.css          # 弹窗样式
├── sidepanel/
│   ├── sidepanel.html     # 侧边栏界面（可选）
│   ├── sidepanel.js       # 侧边栏逻辑
│   └── sidepanel.css      # 侧边栏样式
├── content.js             # 注入到游戏页面的脚本
├── injected.js            # 在页面上下文中运行的脚本
├── background.js          # 后台服务脚本
├── lib/
│   └── staraxis-api.js    # API 封装库
└── README.md              # 使用说明
```

---

## 七、相关文档

- `0-docs/02-中层架构/AI插件化架构设计.md` - 整体架构设计
- `0-docs/02-中层架构/浏览器插件API设计.md` - 本文档
- `web/src/net/snapshotWs.ts` - WebSocket 通信协议
- `web/src/stores/` - 前端状态管理
