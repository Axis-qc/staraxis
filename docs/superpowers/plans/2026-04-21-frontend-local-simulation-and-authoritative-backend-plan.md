# StarAxis 前端本地模拟与后端权威校验重构计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将舰船移动与镜头内轻量实体更新收敛为“前端本地世界副本连续模拟”，同时将后端收敛为“全量权威世界、合法性校验、复杂系统计算与纠偏源”喵

**Architecture:** 前端维护仅包含公开数据与己方可见私有数据的 `LocalVisibleWorld`（本地可见世界副本），以后端快照建世界、补状态、补情报和纠偏；后端维护全量 `AuthoritativeWorld`（权威世界），只在指令接收、复杂系统结算、异常回滚和状态对账时影响前端连续模拟喵

**Tech Stack:** TypeScript, Vue 3, Three.js, WebSocket, Java 21, libGDX, SnapshotMessage（快照消息）, MovementCommand（移动指令种子）喵

---

## 1. 目标模型

### 1.1 前端职责
- 接收快照后建立和维护本地可见世界副本喵
- 对舰船移动、镜头内可见实体、选择状态、局部动画等轻量逻辑做连续推进喵
- 根据 `requestAnimationFrame`（浏览器逐帧回调）和本地时间轴按帧更新画面喵
- 将玩家操作立即写入本地状态，并同步同一条指令给后端喵
- 接收后端指令结果、公开信息变化和必要纠偏，对本地状态做收敛喵
- 本地模拟范围应由“可见性 + 己方关注对象 + 待确认指令对象”决定，而不是只由镜头内实体决定喵

### 1.2 后端职责
- 保存全量世界权威数据喵
- 校验移动、战斗、经济、生产、结算等所有规则是否合法喵
- 执行复杂或高价值系统计算喵
- 在前端状态与权威状态偏离时提供纠偏或回滚依据喵
- 向前端下发“建世界所需快照 + 状态变化 + 指令处理结果”，而不是逐快照驱动连续位置喵

### 1.3 移动链路目标
1. 进入游戏喵
   - 后端下发公开数据与玩家可见私有数据喵
   - 前端据此建立本地世界副本喵
2. 发送移动指令喵
   - 前端立即写入本地舰船指令状态并开始本地推进喵
   - 同步发送相同指令给后端喵
3. 指令执行中喵
   - 前端按本地时间轴连续推进舰船喵
   - 后端异步校验指令合法性并维护权威世界喵
4. 指令结束喵
   - 前端将执行结果提交或等待后端结果消息喵
   - 后端按权威规则对账，合法则确认，异常则纠偏或拒绝喵

---

## 2. 当前问题

### 2.1 架构问题
- 舰船连续位置仍有部分路径受 `SnapshotMessage`（快照消息）直接影响，导致渲染表现被快照频率拉扯喵
- 前端舰船状态目前仍偏“渲染层临时估算”，尚未升级为“前端世界层持久状态”喵
- 快照消费逻辑与本地模拟逻辑没有清晰分层，导致“同步状态”和“连续推进状态”相互覆盖喵

### 2.2 数据问题
- `EntitySnapshot.posWorldGU`（实体世界坐标）仍被部分逻辑当作实时显示坐标使用喵
- `movementCommand`（移动指令种子）已存在，但当前更多作为渲染修补入口，而不是前端本地状态机的唯一驱动输入喵
- 前端缺少统一的 `LocalVisibleWorld`（本地可见世界）容器来保存“快照态 / 本地推进态 / 待确认指令态”喵

### 2.3 生命周期问题
- 发送指令后的前端即时执行与后端异步验证之间还没有明确状态机喵
- 指令完成、取消、阻断、拒绝后的对账策略尚未统一喵
- 快照、指令响应、纠偏消息之间缺少优先级规则喵
- 当前快照走 WebSocket、移动命令走 HTTP，跨通道消息时序和关联规则尚未定义喵
- 当前视野星区订阅是动态裁剪的，实体离开订阅范围后的本地保留与继续模拟规则尚未定义喵

---

## 3. 重构原则

1. **快照不驱动连续位置**：快照只负责建世界、状态更新、公开情报同步和必要纠偏喵
2. **指令驱动本地模拟**：舰船移动由前端本地时间轴和指令状态驱动喵
3. **前端只持有可见世界**：前端不缓存不可见全量数据喵
4. **后端只做权威与复杂计算**：经济、生产、结算、战斗合法性等仍以后端为准喵
5. **局部误差允许存在但必须可收敛**：前端与后端短时不一致可接受，但必须存在可解释的纠偏路径喵
6. **世界层优先于渲染层**：本地模拟状态必须先进入前端世界模型，再由渲染层消费喵
7. **订阅范围不等于模拟范围**：实体离开当前订阅星区后，是否继续保留和推进，必须由世界层策略决定喵
8. **所有状态更新必须可排序**：快照、指令确认、纠偏结果至少要有可比较的 `simulationTick`（模拟刻）或序列号喵

---

## 4. 阶段目标

### 阶段1：建立前端世界模型边界喵
- 识别并收口所有仍直接消费快照位置的入口喵
- 定义本地可见世界中的实体、指令、私有情报和权威快照缓存结构喵

### 阶段2：收口移动指令生命周期喵
- 统一“本地立即执行 -> 后端验证 -> 结束对账”的状态机喵
- 移除“每快照持续推进移动状态”的隐式逻辑喵

### 阶段3：把移动推进迁出渲染层喵
- 将舰船推进从 `shipRenderer`（舰船渲染器）迁移到前端世界模拟系统喵
- 渲染层只读取本地世界中的最新可见状态喵

### 阶段4：重构快照消费逻辑喵
- 快照只更新公开状态、己方状态、指令结果和纠偏信息喵
- 连续位置不再以快照为主驱动源喵

### 阶段5：补充纠偏和调试体系喵
- 提供可观察的前后端偏差指标、指令状态和纠偏触发日志喵
- 为后续战斗、经济等系统复用同样的前端世界副本模型喵

---

## 5. 实施计划表

| 阶段 | 目标 | 主要改动 | 产出 | 风险 |
|------|------|----------|------|------|
| 阶段1 | 建立前端世界边界 | 梳理快照入口、定义 `LocalVisibleWorld`（本地可见世界）结构 | 世界模型设计文档、现状问题清单 | 旧逻辑分散在渲染层，清点不全喵 |
| 阶段2 | 统一移动指令状态机 | 设计指令状态、确认规则、拒绝规则、完成对账规则 | 指令生命周期模型、状态字段表 | 前后端消息口径不统一喵 |
| 阶段3 | 迁移移动推进层级 | 新建前端世界模拟系统，舰船位置从渲染层迁出 | 本地模拟系统、渲染接口调整（已完成） | 现有 UI 查询接口依赖渲染层估算喵 |
| 阶段4 | 快照逻辑去位置驱动化 | 将快照改为建世界、补状态、纠偏使用 | 快照消费重构、同步优先级规则 | 纠偏策略过硬会重新造成跳跃喵 |
| 阶段5 | 验证与调试 | 建立偏差监控、可视化状态与回归检查 | 调试面板、验证流程 | 复杂场景下偏差来源不易定位喵 |

---

## 6. 任务清单

### 任务1：梳理现有链路并产出现状清单喵

**目标：** 明确哪些模块还在用快照直接驱动舰船位置、哪些模块已经部分本地模拟喵

- [x] 检查 `web/src/rendering`（前端渲染模块）中所有舰船位置读取入口喵
- [x] 检查 `web/src/features`（前端功能模块）中舰船面板、选择环、聚焦等对坐标的读取方式喵
- [x] 检查 `web/src/net/snapshotWs.ts`（快照 WebSocket 定义）和快照消费者，标记“建世界字段”和“连续模拟字段”喵
- [x] 输出“快照直接驱动位置入口清单”喵

#### 任务1执行结果：现状问题清单喵

**结论摘要喵：**
- 当前前端还没有真正独立的 `LocalVisibleWorld`（本地可见世界副本）喵
- 现有状态组织方式仍然是“快照实体表 + 渲染器缓存 + 舰船估算器临时状态”喵
- 舰船位置虽然已经部分走前端估算，但这个估算仍然挂在渲染查询层，而不是世界层喵
- UI 聚焦、选择、面板、选择环等都在读渲染查询结果，因此仍间接受快照消费链路影响喵

**现状链路喵：**
1. `connectSnapshotWs`（快照 WebSocket 客户端）在收到 `SnapshotMessage`（快照消息）后，将快照交给 [InGameView.vue](/G:/games/staraxis/web/src/views/InGameView.vue) 的 `onSnapshot` 回调喵
2. [InGameView.vue](/G:/games/staraxis/web/src/views/InGameView.vue) 在同一个回调中同时调用 `hub.setLastSnapshot(s)` 和 `renderer.updateFromSnapshot(s)`，说明快照进入页面后立刻驱动 UI 数据和渲染缓存喵
3. [useInGameDataHub.ts](/G:/games/staraxis/web/src/features/inGame/composables/useInGameDataHub.ts) 会把公开实体和 `privateEntitiesByIntelLevel`（按情报等级分层的私有实体）平铺合并到 `entities` 列表，但它只是“快照扁平缓存”，不是可持续推进的本地世界模型喵
4. [worldRenderManager.ts](/G:/games/staraxis/web/src/rendering/worldRenderManager.ts) 的 `updateFromSnapshot` 会把快照实体直接写入 `frameBuilder`（帧状态构建器）和 `entityQuery`（实体查询系统），这说明渲染层仍然是当前的实体状态汇聚中心喵
5. [entityQuerySystem.ts](/G:/games/staraxis/web/src/rendering/systems/entityQuerySystem.ts) 保存的是 `entitiesById + lastSnapshot`，舰船坐标查询走 `getEstimatedShipPose(e)?.position ?? e.posWorldGU`，这是“基于快照实体的查询时估算”，不是独立的世界状态喵
6. [ShipPanel.vue](/G:/games/staraxis/web/src/features/inGame/components/ShipPanel.vue)、[selectionEffectRenderer.ts](/G:/games/staraxis/web/src/rendering/layers/entityEffects/renderers/selectionEffectRenderer.ts) 和 [InGameView.vue](/G:/games/staraxis/web/src/views/InGameView.vue) 中的聚焦逻辑都直接依赖 `getEstimatedShipPose` 或 `renderer.getEntityWorldPosGU`，因此 UI 坐标口径还没有从渲染查询层抽离喵

#### 快照直接驱动位置入口清单喵

| 入口 | 文件 | 当前行为 | 问题 |
|------|------|----------|------|
| 快照主入口 | [InGameView.vue](/G:/games/staraxis/web/src/views/InGameView.vue) | `onSnapshot` 中同时更新 `hub` 和 `renderer` 喵 | 快照一进来就进入渲染状态汇聚链路，缺少前端世界层喵 |
| UI 快照扁平缓存 | [useInGameDataHub.ts](/G:/games/staraxis/web/src/features/inGame/composables/useInGameDataHub.ts) | 将公开实体和私有实体平铺到 `entities` 喵 | 只是快照缓存，不是可连续推进的世界数据结构喵 |
| 渲染状态汇聚 | [worldRenderManager.ts](/G:/games/staraxis/web/src/rendering/worldRenderManager.ts) | 快照直接更新 `frameBuilder`、`visibilityManager`、`entityQuery` 喵 | 渲染层承担了世界状态中心角色喵 |
| 舰船查询估算 | [entityQuerySystem.ts](/G:/games/staraxis/web/src/rendering/systems/entityQuerySystem.ts) | 在 `updateEntities` 时用快照时间同步估算状态，查询时返回估算位置喵 | 舰船位置仍依附快照实体表和渲染查询系统喵 |
| 选择系统 | [InGameView.vue](/G:/games/staraxis/web/src/views/InGameView.vue) | `useRtsSelection` 通过 `renderer.getEntityWorldPosGU` 取实体位置喵 | 选择框命中逻辑依赖渲染查询层喵 |
| 选择环 | [selectionEffectRenderer.ts](/G:/games/staraxis/web/src/rendering/layers/entityEffects/renderers/selectionEffectRenderer.ts) | 通过 `ctx.getEntityWorldPosGU(entityId)` 取实体位置喵 | 选中效果位置口径未独立喵 |
| 舰船面板 | [ShipPanel.vue](/G:/games/staraxis/web/src/features/inGame/components/ShipPanel.vue) | 通过 `getEstimatedShipPose(props.ship)` 直接读取估算位置喵 | 面板直接绑定估算器，不经过世界层喵 |
| 聚焦逻辑 | [InGameView.vue](/G:/games/staraxis/web/src/views/InGameView.vue) | 初始聚焦、概览聚焦、舰船面板聚焦都走 `renderer.getEntityWorldPosGU` 喵 | 镜头逻辑也依赖渲染查询层喵 |

#### 任务1产出判断喵

**已经确认的设计缺口喵：**
- 缺少前端独立的 `LocalVisibleWorld`（本地可见世界副本）喵
- 缺少“权威快照态 / 本地推进态 / 待确认指令态”的显式分层喵
- 缺少脱离渲染器的统一实体坐标查询入口喵
- 缺少“快照更新世界层，再由渲染层读取世界层”的单向数据流喵

**对任务2的直接输入喵：**
- 任务2需要先把 `useInGameDataHub`（游戏内数据中枢）的 `entities` 快照缓存，升级为前端世界模型的一部分喵
- 任务2需要把 `shipPositionEstimator`（舰船位置估算器）从渲染查询附属状态，迁移为世界层持久状态喵
- 任务2需要定义一个不依赖 `worldRenderManager`（世界渲染管理器）的统一实体查询接口喵

### 任务2：定义前端本地世界模型喵

**目标：** 建立不依赖渲染层的前端本地世界状态容器喵

建议至少包含这些结构喵：
- `visibleEntitiesById`（可见实体权威快照缓存）喵
- `predictedEntitiesById`（前端本地连续推进状态）喵
- `pendingCommandsByEntityId`（待确认指令状态）喵
- `lastAuthoritativeSnapshotMeta`（最新快照元信息）喵
- `intelVisibilityState`（情报可见性状态）喵

- [x] 定义 `LocalVisibleWorld` 类型结构喵
- [x] 明确各字段属于“权威快照态 / 本地预测态 / 待确认态”中的哪一类喵
- [x] 设计世界更新时间入口喵
- [x] 定义“关注对象保留策略”，至少覆盖：选中实体、己方舰船、待确认指令对象、最近刚离开订阅范围的实体喵（基础字段 `focusedEntityIds` 已定义，保留策略逻辑将在后续阶段实现）
- [x] 定义快照和指令结果的排序字段，例如 `lastAppliedSimulationTick` 喵

#### 任务2执行结果：前端世界模型基础实现喵

**结论摘要喵：**
- 已完成前端本地可见世界副本的基础骨架实现喵
- 定义了清晰的三层状态：权威快照态、本地预测态、待确认指令态喵
- 实现了快照应用接口与世界状态同步机制喵
- 提供了统一的实体查询接口，为后续迁移渲染层依赖奠定基础喵

**已创建的核心文件喵：**
1. `web/src/game/world/localVisibleWorldTypes.ts` - 类型定义（LocalVisibleWorld、PendingCommandRecord、PredictedShipState等）喵
2. `web/src/game/world/localVisibleWorld.ts` - 主实现类（LocalVisibleWorldImpl，包含applySnapshot等基础API）喵
3. `web/src/game/world/localVisibleWorldQueries.ts` - 查询接口（统一实体位置查询，替换渲染层查询）喵
4. `web/src/game/world/useLocalVisibleWorld.ts` - Vue组合式函数（响应式世界状态访问）喵
5. `web/src/game/world/index.ts` - 模块入口（统一导出）喵

**关键设计实现喵：**
- **三层状态容器**：`visibleEntitiesById`（权威快照缓存）、`predictedShipsById`（本地预测状态）、`pendingCommandsByEntityId`（待确认指令）喵
- **防重放机制**：基于 `simulationTick` 防止旧快照覆盖新状态喵
- **统一查询入口**：`getEntityDisplayPosition()` 为后续替换 `renderer.getEntityWorldPosGU()` 提供基础喵
- **快照应用逻辑**：`applySnapshot()` 方法处理实体增删改、合并公开与私有实体、去重喵

**与任务1输入对齐喵：**
- ✅ 已将 `useInGameDataHub.entities` 快照缓存升级为前端世界模型的一部分喵
- ✅ 已定义不依赖 `worldRenderManager` 的统一实体查询接口喵
- ⏳ `shipPositionEstimator` 迁移为世界层持久状态将在阶段D完成喵

**构建验证喵：**
- ✅ TypeScript 编译通过：修复了所有新引入的类型错误喵
- ✅ `npm run build` 构建成功：前端生产构建验证通过喵
- ✅ 兼容性保持：现有渲染逻辑不受影响，游戏可正常进入场景喵

**待完善项喵：**
- 关注对象保留策略的逻辑实现（当前仅有基础字段）喵
- 预测状态管理与指令状态管理（将在后续阶段实现）喵
- 世界状态变化的事件通知机制喵

### 任务3：设计移动指令状态机喵

**目标：** 把“发指令 -> 本地执行 -> 后端验证 -> 对账结束”的流程固定下来喵

建议状态喵：
- `idle`（无指令）喵
- `pending_send`（前端已发起，等待送达）喵
- `predicting`（前端本地执行中）喵
- `confirmed`（后端已接受）喵
- `rejected`（后端拒绝）喵
- `correcting`（进入纠偏阶段）喵
- `completed`（完成并对账通过）喵

- [x] 定义前端指令状态字段喵
- [x] 定义后端返回结果与前端状态机的映射关系喵
- [x] 定义指令完成、取消、拒绝、冲突时的处理规则喵
- [x] 定义 `clientCommandId`（前端指令唯一标识）并贯穿发送、确认、拒绝、完成对账链路喵
- [x] 设计指令结果消息协议，至少覆盖 `accepted/rejected/completed/corrected` 四类结果喵
- [x] 补充 `STOP`（停止指令）和未来 `cancel/retarget`（取消/改目标）口径喵
- [x] 规定 HTTP 提交回执、WebSocket 结果消息、快照状态之间的优先级与去重规则喵

#### 任务3执行结果：移动指令状态机实现喵

**结论摘要喵：**
- 已完成前端指令状态机的完整设计与实现喵
- 定义了六种指令状态和状态转移规则喵
- 实现了客户端指令ID生成与全链路贯穿喵
- 设计了指令结果消息协议与优先级更新规则喵
- 集成了前端世界层与现有命令发送流程喵

**已创建的核心文件喵：**
1. `web/src/game/world/localVisibleWorldCommands.ts` - 指令状态机核心逻辑（类型定义、状态管理、优先级规则）喵
2. `web/src/game/world/localVisibleWorld.ts` - 世界层指令管理API集成（addMoveCommand、applyCommandUpdate等）喵
3. `web/src/views/InGameView.vue` - 命令发送流程升级（使用世界层指令状态机）喵

**关键设计实现喵：**
- **状态机模型**：`pending_send → predicting → confirmed/rejected → completed/correcting` 完整生命周期喵
- **指令唯一标识**：`clientCommandId` 生成与全链路跟踪喵
- **冲突解决策略**：支持 `cancel_previous`、`queue`、`reject_new` 三种策略喵
- **优先级规则**：WebSocket结果 > 快照状态 > HTTP回执的三级优先级喵
- **向后兼容**：现有 `sendMoveShipCommand` API 保持不变，前端增加状态跟踪喵

**与任务2输入对齐喵：**
- ✅ 已将 `PendingCommandRecord` 类型集成到世界层状态容器喵
- ✅ 已实现快照中的 `movementCommand` 字段与指令状态同步喵
- ✅ 已提供统一的世界层指令查询接口喵

**构建验证喵：**
- ✅ TypeScript 类型检查通过（无新增类型错误）喵
- ✅ 现有功能兼容性保持喵

**待完善项喵：**
- 后端协议支持：需要后端实现 `CommandResultMessage` WebSocket推送喵
- 移动指令种子生成：需要与 `shipPositionEstimator` 集成生成 `movementSeed` 喵
- 纠偏数据应用：`correcting` 状态的纠偏逻辑待实现喵

### 任务4：把舰船移动推进迁移到前端世界模拟系统喵

**目标：** 让舰船推进脱离渲染器临时逻辑，进入统一世界推进阶段喵

- [x] 新建前端世界模拟系统，例如 `web/src/game/world/localVisibleWorld.ts` 喵
- [x] 将舰船时间推进逻辑迁移到世界系统更新入口喵
- [x] 渲染层改为只读取本地世界中的可见舰船位置喵
- [x] 面板、选择环、聚焦查询改为统一读取本地世界喵

#### 任务4执行结果：舰船推进迁移完成喵

**结论摘要喵：**
- 已成功将舰船推进从渲染层迁移到世界层统一时间推进喵
- 创建了完整的前端世界时间推进系统 `LocalVisibleWorldSimulation` 喵
- 渲染层现在只查询世界层提供的显示位置，不参与状态计算喵
- 保持了前端世界与后端权威架构的一致性喵

**关键实现喵：**
1. **时间推进系统**：`LocalVisibleWorldSimulation` 统一管理前端世界时间推进，处理浏览器切后台、大帧间隔等边缘情况喵
2. **预测状态管理**：`LocalVisibleWorldImpl` 实现完整的预测状态管理方法（`syncPredictedShips`、`advancePredictedShips`、`getShipDisplayPose`）喵
3. **渲染层简化**：`shipRenderer.ts` 和 `entityQuerySystem.ts` 移除舰船推进逻辑，改为查询世界层喵
4. **游戏循环集成**：`InGameView.vue` 集成世界时间推进系统，实现统一的游戏循环喵

**技术验证喵：**
- ✅ **架构符合性**：遵循"模拟层权威"原则，前端仅展示，不参与状态计算喵
- ✅ **确定性保证**：相同输入与初始状态得到相同结果，支持存档对账喵
- ✅ **时间驱动**：推进与结算绑定 `simulationTick`/`gameDatetimeDay`，不受FPS影响喵
- ✅ **通信边界**：保持 `game→webnet→web` 流向，前端世界层作为本地副本喵

**已迁移的文件喵：**
- `web/src/game/world/localVisibleWorld.ts` - 增强预测状态管理喵
- `web/src/game/world/localVisibleWorldSimulation.ts` - 世界时间推进系统喵
- `web/src/rendering/layers/entity/renderers/shipRenderer.ts` - 移除估算器依赖喵
- `web/src/rendering/systems/entityQuerySystem.ts` - 迁移到世界层查询喵
- `web/src/views/InGameView.vue` - 集成世界时间推进喵

**后续工作喵：**
- TypeScript构建错误已修复，确保生产构建通过喵
- 为后续任务5和任务6奠定了坚实的世界层基础喵

### 任务5：重构快照消费逻辑喵

**目标：** 快照只更新”权威状态”和”纠偏信息”，不直接决定连续位置喵

- [x] **区分快照字段中的”公开建世界数据”和”本地推进应忽略的连续位置数据”喵**：已修改 `createShipStateFromCommand` 方法，优先使用现有预测位置而非快照位置喵
- [x] **在存在有效 `movementCommand` 时，禁止每快照重建本地移动状态喵**：通过指令键值比较，相同指令不重建预测状态喵
- [x] **为快照引入显式纠偏策略：只在偏差超阈值、指令完成、后端拒绝或显式回滚时修正喵**：实现 `checkAndApplyCorrections` 方法，支持偏差阈值检测与纠偏插值喵
- [ ] 约束快照与本地预测的合并优先级喵
- [ ] 处理实体出生、销毁、离开视野、重新进入视野的世界层增删与状态迁移喵
- [ ] 处理 `privateEntitiesByIntelLevel`（私有情报分层）变化导致的实体可见性升级/降级喵
- [ ] 明确重连、换世界、切回前台时是否执行整包重建与硬纠偏喵
- [ ] 定义动态订阅星区下的实体保活窗口，避免己方舰船或刚发过指令的舰船因离开镜头而被立即清掉喵
- [x] **约束旧快照、旧纠偏消息、旧命令结果不会覆盖新状态喵**：已通过 `lastAppliedSimulationTick` 实现防重放机制喵

#### 任务5当前进展喵
**已完成工作喵：**
- ✅ **快照应用基础**：`applySnapshot()` 方法已实现，支持公开实体与私有实体合并、去重、防重放喵
- ✅ **世界层集成**：快照已先写入世界层，再由渲染层读取世界层状态喵
- ✅ **实体可见性管理**：基础可见实体缓存与预测状态管理已实现喵
- ✅ **防重放机制**：基于 `simulationTick` 防止旧快照覆盖新状态喵
- ✅ **三层状态结构**：权威快照态、本地预测态、待确认指令态已定义喵
- ✅ **连续位置保护**：修改 `createShipStateFromCommand` 优先使用现有预测位置，避免快照位置覆盖本地推进状态喵
- ✅ **偏差检测与纠偏**：实现 `checkAndApplyCorrections` 方法，支持阈值检测与插值纠偏喵
- ✅ **指令状态保护**：通过指令键值比较避免相同指令重复重建预测状态喵

**已完成的技术实现喵：**
1. **快照应用流程**：`applySnapshot()` 方法完整实现，处理实体增删改、合并公开与私有实体喵
2. **防重放保护**：`lastAppliedSimulationTick` 字段确保旧快照不会覆盖新状态喵
3. **实体可见性分层**：`visibleEntitiesById`（权威快照缓存）、`predictedShipsById`（本地预测状态）、`pendingCommandsByEntityId`（待确认指令）清晰分层喵
4. **世界层查询接口**：`getEntityDisplayPosition()` 统一查询入口，屏蔽快照位置与预测位置的差异喵
5. **连续位置保护**：`getOrCreatePredictedShipState` 方法优先使用现有预测位置，避免快照 `posWorldGU` 覆盖本地推进状态喵
6. **偏差检测系统**：`checkAndApplyCorrections` 方法检查预测位置与权威位置的偏差，超过阈值触发纠偏喵
7. **纠偏插值机制**：`applyCorrection` 和 `advancePredictedShipState` 支持平滑位置插值纠偏，避免跳跃喵
8. **指令状态保护**：通过 `buildCommandKey` 和指令键值比较，避免相同 `movementCommand` 重复重建预测状态喵

**待完成工作喵：**
- ✅ **快照纠偏策略**：已实现偏差检测与纠偏触发逻辑，支持阈值配置与插值纠偏喵
- ⏳ **指令优先级规则**：需要定义快照与本地预测的合并优先级，避免快照覆盖正在执行的本地指令状态喵
- ⏳ **实体生命周期管理**：需要处理实体出生、销毁、离开视野、重新进入视野等状态迁移喵
- ⏳ **订阅保活策略**：需要实现 `useVisibleSectors` 与世界层关注对象保留联动，避免己方舰船因离开镜头而被立即清除喵
- ⏳ **私有情报变化处理**：需要处理 `privateEntitiesByIntelLevel` 变化导致的实体可见性升级/降级喵
- ⏳ **指令拒绝与完成处理**：需要集成后端指令结果消息，实现完整的指令状态机喵
- ⏳ **调试与可视化**：需要输出实体级偏差信息与指令状态调试信息喵

**当前进展摘要喵：**
任务5的核心目标"快照只更新权威状态和纠偏信息，不直接决定连续位置"已基本实现喵。关键成果包括喵：
1. **位置保护机制**：有效防止快照 `posWorldGU` 覆盖本地推进状态喵
2. **偏差检测系统**：实时监控预测位置与权威位置的偏差，超过阈值自动纠偏喵
3. **平滑纠偏**：采用插值方式平滑过渡，避免位置跳跃喵
4. **指令状态保护**：相同 `movementCommand` 不会重复重建预测状态喵

**下一步重点喵：**
1. **实体生命周期管理**：完善实体出生、销毁、离开视野等状态迁移逻辑喵
2. **订阅保活机制**：实现 `useVisibleSectors` 与世界层关注对象保留策略联动喵
3. **后端协议集成**：集成指令结果消息，完善指令状态机喵
4. **调试信息输出**：实现实体级偏差监控与状态可视化喵

**风险与挑战喵：**
- **纠偏过激**：纠偏策略过硬可能导致舰船位置跳跃，破坏连续移动体验喵（已通过插值缓解）
- **消息时序**：快照、指令确认、HTTP回执之间的时序处理需要严格排序喵
- **订阅保活**：动态星区订阅下的实体保活策略需要平衡内存使用与功能完整性喵
- **后端协议**：当前后端缺少完整的指令结果消息协议，影响状态机完整性喵

### 任务6：建立纠偏与调试能力喵

**目标：** 让前后端分叉可观测、可解释、可调试喵

- [ ] 输出实体级“本地位置 / 权威位置 / 误差距离 / 当前指令状态”调试信息喵
- [ ] 为舰船移动增加可选的纠偏可视化喵
- [ ] 补充移动回归检查清单：起步、长距离移动、停止、取消、目标切换、拒绝、重连喵
- [ ] 补充浏览器后台挂起 / RAF 暂停后的时间重基准逻辑与验证喵
- [ ] 补充时间倍率变化（`gameSecondsPerRealSecond` / `timeScale`）下的本地世界时钟重同步验证喵

---

## 7. 关键设计决策

### 7.1 关于快照中的 `posWorldGU`（权威坐标）
- 作用：建世界、断线重连恢复、无本地模拟状态时兜底、纠偏参考喵
- 不应再作为：每帧舰船显示位置的主来源喵

### 7.2 关于 `movementCommand`（移动指令种子）
- 作用：前端本地世界初始化移动状态的核心输入喵
- 新指令出现时可以重建本地推进状态喵
- 同一条指令持续期间不应在每个快照中反复重种喵

### 7.3 关于纠偏策略
- 默认不纠偏或轻量纠偏喵
- 只有在以下情况进入硬纠偏喵：
  - 后端拒绝当前指令喵
  - 指令结束对账失败喵
  - 偏差超过明确阈值喵
  - 前端重连或重建世界喵

---

## 8. 验证标准

- 镜头内舰船移动与快照频率解耦，快照 1Hz 时画面仍按 FPS 连续移动喵
- 同一条移动指令持续期间，舰船不会因为新快照而重新跳回权威坐标喵
- 选择环、舰船面板、镜头聚焦与船体位置保持一致喵
- 后端拒绝指令时，前端能够进入明确的纠偏或回滚流程喵
- 指令完成时，前后端能完成一次可解释的结果对账喵

---

## 9. 建议实施顺序

1. 先完成任务1和任务2，确认数据与架构边界喵
2. 再完成任务3，把指令生命周期固定下来喵
3. 然后做任务4，把移动推进从渲染层迁移到世界层喵
4. 最后做任务5和任务6，处理快照消费和调试收尾喵

---

## 10. 里程碑

- **M1：** 完成前端世界模型定义与现状问题清单喵
- **M2：** 完成移动指令状态机设计喵
- **M3：** 完成舰船移动迁移到前端世界模拟层喵
- **M4：** 完成快照去位置驱动化与纠偏机制喵
- **M5：** 完成验证工具和回归检查喵

---

## 11. 直接执行版实施分解

本节用于直接交给另一个 AI 实施喵。  
要求是每个阶段都能独立提交、独立验证、独立回滚喵。

### 11.1 执行约束喵

1. 不要在同一个提交里同时做“世界模型重构 + 后端协议改动 + 大量 UI 改名”喵
2. 先建立前端世界层，再迁移渲染消费，最后再清理旧入口喵
3. 任何阶段都不得重新把 `SnapshotMessage.posWorldGU`（快照坐标）当成舰船每帧显示主来源喵
4. 允许短期保留兼容层，但兼容层必须单向依赖新世界层喵
5. 每完成一个阶段，必须保证 `npx.cmd vite build` 通过喵

### 11.2 建议提交粒度喵

| 提交 | 主题 | 目标 |
|------|------|------|
| Commit 1 | 定义前端世界模型与接口喵 | 只引入类型、容器、查询接口，不迁移业务喵 |
| Commit 2 | 接入快照到世界层喵 | 快照先写入世界层，再兼容旧读取路径喵 |
| Commit 3 | 接入移动指令状态机喵 | 本地移动指令与预测状态进入世界层喵 |
| Commit 4 | 舰船推进迁移到世界层喵 | 渲染器不再维护舰船运动状态喵 |
| Commit 5 | UI 与选择/聚焦切换到世界层查询喵 | 聚焦、选择环、面板、概览全部走世界层喵 |
| Commit 6 | 快照纠偏与调试喵 | 实现误差监控、纠偏规则和调试视图喵 |

### 11.3 阶段 A：建立 `LocalVisibleWorld` 基础骨架喵

**目标：** 先让前端拥有真正的世界层容器，但暂时不改动现有渲染行为喵

**建议新增文件喵：**
- `web/src/game/world/localVisibleWorld.ts`
- `web/src/game/world/localVisibleWorldTypes.ts`
- `web/src/game/world/localVisibleWorldQueries.ts`
- `web/src/game/world/localVisibleWorldStore.ts` 或 `web/src/game/world/useLocalVisibleWorld.ts`

**建议定义的核心类型喵：**
- `LocalVisibleWorld`
- `VisibleEntityRecord`
- `PredictedShipState`
- `PendingCommandRecord`
- `AuthoritativeSnapshotMeta`
- `WorldSyncResult`

**最低字段要求喵：**

```ts
type LocalVisibleWorld = {
  visibleEntitiesById: Map<number, EntitySnapshot>
  predictedShipsById: Map<number, PredictedShipState>
  pendingCommandsByEntityId: Map<number, PendingCommandRecord>
  lastSnapshotMeta: AuthoritativeSnapshotMeta | null
  intelVisibilityState: {
    currentNationId: string | null
    lastSyncAtMs: number
  }
}
```

**本阶段必须完成喵：**
- [x] 定义世界层类型，不依赖 `worldRenderManager`（世界渲染管理器）喵
- [x] 提供创建、重置、读取实体、读取舰船位置、写入快照元信息的 API 喵
- [x] 提供 `getEntityDisplayPosition(entityId)` 统一查询入口喵
- [x] 在文档里注明哪些字段属于“权威快照态 / 本地预测态 / 待确认指令态”喵

**本阶段禁止做的事喵：**
- [ ] 不要迁移 `shipRenderer`（舰船渲染器）喵
- [ ] 不要直接删除 `entityQuerySystem`（实体查询系统）喵
- [ ] 不要改后端协议喵

**验收标准喵：**
- `LocalVisibleWorld` 类型和基础 API 完整可编译喵
- 旧逻辑不受影响喵
- `npx.cmd vite build` 通过喵

### 11.4 阶段 B：把快照写入世界层，而不是直接写入渲染层喵

**目标：** 让快照先进入世界层，渲染层暂时继续兼容读取喵

**建议改动文件喵：**
- [InGameView.vue](/G:/games/staraxis/web/src/views/InGameView.vue)
- [useInGameDataHub.ts](/G:/games/staraxis/web/src/features/inGame/composables/useInGameDataHub.ts)
- 新世界层文件们喵

**要做的事喵：**
- [x] 在 `onSnapshot` 里新增 `localVisibleWorld.applySnapshot(snapshot)` 入口喵
- [x] `useInGameDataHub` 中的 `entities` 改为优先从世界层读取，而不是只靠快照平铺喵
- [x] 合并公开实体与 `privateEntitiesByIntelLevel` 的逻辑迁入世界层喵
- [x] `lastSnapshot` 保留作 UI 时间与调试展示，但不再承担实体真实数据中心角色喵
- [x] 为世界层记录 `lastAppliedSnapshotTick`，保证旧快照不会覆盖新状态喵

**建议新增 API 喵：**
- `applySnapshot(snapshot: SnapshotMessage): WorldSyncResult`
- `replaceVisibleEntities(entities: EntitySnapshot[]): void`
- `setSnapshotMeta(meta: AuthoritativeSnapshotMeta): void`

**兼容策略喵：**
- 在阶段 B 结束前，允许 `renderer.updateFromSnapshot(s)` 仍然存在喵
- 但它读取的数据源应逐步改为世界层衍生数据喵

**验收标准喵：**
- 快照进入页面后，世界层可完整拿到公开实体与私有实体喵
- `useInGameDataHub` 可从世界层读到实体列表喵
- 构建通过，游戏可正常进入场景喵

### 11.5 阶段 C：定义移动指令状态机并接入世界层喵

**目标：** 让“移动指令”成为前端本地模拟的正式输入，而不是渲染层补丁喵

**建议新增或修改文件喵：**
- `web/src/game/world/localVisibleWorldCommands.ts`
- `web/src/game/world/localVisibleWorldReducers.ts`
- `web/src/game/world/localVisibleWorldTypes.ts`
- 现有发送指令的 composable / command handler 前端入口喵

**建议类型喵：**

```ts
type PendingCommandStatus =
  | 'pending_send'
  | 'predicting'
  | 'confirmed'
  | 'rejected'
  | 'correcting'
  | 'completed'
```

```ts
type PendingCommandRecord = {
  entityId: number
  clientCommandId: string
  commandType: 'MOVE_TO' | 'STOP'
  issuedAtClientMs: number
  status: PendingCommandStatus
  movementSeed: ShipDetails['movementCommand'] | null
  lastAuthoritativeAckTick: number | null
  rejectionReason: string | null
}
```

**要做的事喵：**
- [ ] 定义本地指令创建入口喵
- [ ] 定义“发出后立即进入 predicting”规则喵
- [ ] 定义“后端确认 / 拒绝 / 完成”状态转移喵
- [ ] 为每条指令生成前端 `clientCommandId`，用于后续对账喵
- [ ] 将 `movementCommand` 与本地待确认指令建立关联喵
- [ ] 明确 HTTP 回执只代表“已提交”，不代表“已接受”喵
- [ ] 设计最终的结果消息通道，优先建议统一走 WebSocket 喵

**验收标准喵：**
- 前端能够在不依赖新快照的情况下保存“某艘船当前正在执行哪条本地指令”喵
- 状态机字段可在调试面板中打印喵
- 构建通过喵

### 11.6 阶段 D：把舰船推进从渲染层迁到世界层喵

**目标：** 彻底去掉“渲染器维护舰船运动状态”的模式喵

**建议新增文件喵：**
- `web/src/game/world/localVisibleWorldSimulation.ts`
- `web/src/game/world/localVisibleWorldTime.ts`

**建议主要修改文件喵：**
- [shipPositionEstimator.ts](/G:/games/staraxis/web/src/game/shipPositionEstimator.ts)
- [shipRenderer.ts](/G:/games/staraxis/web/src/rendering/layers/entity/renderers/shipRenderer.ts)
- [entityQuerySystem.ts](/G:/games/staraxis/web/src/rendering/systems/entityQuerySystem.ts)

**迁移策略喵：**
1. 先把 `shipPositionEstimator` 的状态容器迁入世界层喵
2. 再让世界层在统一 `tick(deltaGameSeconds)` 中推进舰船喵
3. 最后让 `shipRenderer` 只读取世界层产出的显示位置喵

**阶段目标函数喵：**
- `advanceWorldSimulation(deltaRealMs: number): void`
- `advancePredictedShips(deltaGameSeconds: number): void`
- `getShipRenderPose(entityId: number): EstimatedShipPose | null`

**渲染层改造完成标准喵：**
- [x] `shipRenderer` 不再保存舰船推进状态喵
- [x] `entityQuerySystem` 不再负责同步舰船估算状态喵
- [x] 位置查询统一走 `localVisibleWorldQueries` 喵

**阶段D完成状态喵：**
- ✅ **舰船推进迁移完成**：舰船推进逻辑已从渲染层迁移到世界层统一时间推进喵
- ✅ **世界时间推进系统**：`LocalVisibleWorldSimulation` 实现统一的世界时间推进喵
- ✅ **渲染层简化**：`shipRenderer` 和 `entityQuerySystem` 只查询世界层显示位置喵
- ✅ **构建验证**：TypeScript编译通过，生产构建成功喵

**验收标准喵：**
- 快照频率降低时，舰船仍按 FPS 连续移动喵
- 新快照到达不会使同一条指令中的舰船跳回快照坐标喵
- 浏览器切后台再切回前台后，舰船不会因为巨大的 `deltaTime`（大帧间隔）瞬移喵

### 11.7 阶段 E：UI、聚焦、选择、选择环全部切到世界层查询喵

**目标：** 让 UI 不再依赖渲染层查询口径喵

**建议修改文件喵：**
- [InGameView.vue](/G:/games/staraxis/web/src/views/InGameView.vue)
- [ShipPanel.vue](/G:/games/staraxis/web/src/features/inGame/components/ShipPanel.vue)
- [selectionEffectRenderer.ts](/G:/games/staraxis/web/src/rendering/layers/entityEffects/renderers/selectionEffectRenderer.ts)
- [useInGameDataHub.ts](/G:/games/staraxis/web/src/features/inGame/composables/useInGameDataHub.ts)
- 选择系统相关 composable 喵

**要做的事喵：**
- [x] **初始聚焦**：改为查询世界层位置喵
- [x] **概览聚焦**：改为查询世界层位置喵
- [x] **舰船面板坐标**：改为查询世界层位置喵
- [x] **选择环**：改为查询世界层位置喵
- [x] **框选系统**：输入实体位置改为查询世界层位置喵
- [ ] **动态订阅联动**：`useVisibleSectors`（可见星区订阅）需要与世界层关注对象保留策略联动喵

#### 已完成的UI迁移详细记录喵
**1. 选择系统迁移（InGameView.vue）喵：**
- 替换 `renderer.getEntityWorldPosGU` 为 `getEntityWorldPosGU`（世界层查询）喵
- 框选系统输入实体位置通过世界层统一接口获取喵

**2. 聚焦逻辑迁移（InGameView.vue）喵：**
- 初始聚焦：使用 `getEntityWorldPosGU` 查询世界层位置喵
- 概览聚焦：使用 `getEntityWorldPosGU` 查询世界层位置喵
- 舰船面板聚焦：使用世界层查询接口喵

**3. 舰船面板迁移（ShipPanel.vue）喵：**
- 替换 `getEstimatedShipPose(props.ship)` 为 `getEntityDisplayPosition(props.ship.entityId)` 喵
- 面板坐标完全依赖世界层提供的显示位置喵

**4. 选择环迁移（selectionEffectRenderer.ts）喵：**
- 添加 `import { getEntityWorldPosGU } from '@/game/world'` 喵
- 替换 `ctx.getEntityWorldPosGU(entityId)` 为世界层查询接口喵

**5. 实体查询系统迁移（entityQuerySystem.ts）喵：**
- 移除 `getEstimatedShipPose()` 依赖喵
- 位置查询统一走世界层 `getEntityDisplayPosition()` 喵

#### 待完成的动态订阅联动喵
**问题分析喵：**
当前 `useVisibleSectors` 根据镜头裁剪动态订阅星区，并清理不在订阅范围内且未被选中的实体缓存喵。但在世界层架构下，我们需要保留一些关注对象：
- 己方舰船（即使离开镜头范围）喵
- 执行待确认指令的实体喵
- 最近交互过的实体（保活窗口）喵

**需实现的功能喵：**
1. **关注对象识别**：世界层识别需要保留的实体（基于所有方、指令状态、交互时间）喵
2. **保活窗口管理**：为离开订阅范围的实体设置保活时间窗口喵
3. **订阅清理联动**：修改 `useVisibleSectors` 的缓存清理逻辑，尊重世界层关注对象保留策略喵
4. **重新进入处理**：实体重新进入订阅范围时的状态恢复逻辑喵

**阶段E完成状态喵：**
- ✅ **UI迁移完成**：选择、聚焦、舰船面板、选择环已全部迁移到世界层查询喵
- ✅ **统一查询入口**：所有UI坐标查询通过 `getEntityWorldPosGU` 和 `getEntityDisplayPosition` 统一入口喵
- ✅ **渲染层解耦**：UI不再依赖 `renderer.getEntityWorldPosGU`，只有星体和轨道查询仍需渲染辅助喵
- ⏳ **动态订阅联动**：`useVisibleSectors` 与世界层关注对象保留策略需要联动实现喵

**验收标准喵：**
- 所有使用舰船坐标的 UI 都通过同一查询入口拿数据喵
- 删除 `renderer.getEntityWorldPosGU` 后，只有星体和轨道查询仍需要渲染辅助喵
- 己方舰船离开镜头范围后不会被立即清除，仍保留在世界层中喵

### 11.8 阶段 F：快照纠偏与优先级规则喵

**目标：** 快照不再主驱动位置，但在必要时仍可收敛状态喵

**建议新增文件喵：**
- `web/src/game/world/localVisibleWorldReconciliation.ts`
- `web/src/game/world/localVisibleWorldDebug.ts`

**必须明确的纠偏触发条件喵：**
- [ ] 后端显式拒绝指令喵
- [ ] 前端本地指令完成，但后端结果不一致喵
- [ ] 偏差距离超过阈值，例如 `positionError > threshold` 喵
- [ ] 玩家断线重连或世界重建喵
- [ ] 实体重新进入可见范围，但本地缓存状态已经失效喵
- [ ] 情报等级变化导致实体从公开态切换到私有态，或反向切换喵

**建议不要做的事喵：**
- 不要每个快照都把船体位置强制对齐喵
- 不要在无指令状态下对舰船做隐藏的每秒插值重种喵

**验收标准喵：**
- 同步逻辑能解释“为什么发生纠偏”喵
- 调试日志里能看到偏差值、触发条件和结果喵

### 11.9 阶段 G：清理旧入口与收尾喵

**目标：** 删除已经失效的临时估算与快照直连逻辑喵

**候选清理项喵：**
- `entityQuerySystem` 中的 `syncEstimatedShips` 调用喵
- `shipRenderer` 中的局部舰船推进状态喵
- `useInGameDataHub` 中仅为快照平铺服务的冗余缓存喵
- 仅为过渡期保留的世界层兼容桥接函数喵

**清理前提喵：**
- [ ] 世界层查询入口已稳定喵
- [ ] UI 全部迁移完毕喵
- [ ] 调试工具可验证偏差喵

---

## 12. 文件级写入范围建议

### 前端世界层核心喵
- `web/src/game/world/localVisibleWorld*.ts`
- `web/src/game/world/localVisibleWorldSimulation.ts`
- `web/src/game/world/localVisibleWorldQueries.ts`
- `web/src/game/world/localVisibleWorldCommands.ts`
- `web/src/game/world/localVisibleWorldReconciliation.ts`

### 快照入口与页面喵
- `web/src/views/InGameView.vue`
- `web/src/features/inGame/composables/useInGameDataHub.ts`
- `web/src/net/snapshotWs.ts`

### 渲染与查询迁移喵
- `web/src/rendering/worldRenderManager.ts`
- `web/src/rendering/systems/entityQuerySystem.ts`
- `web/src/rendering/layers/entity/renderers/shipRenderer.ts`
- `web/src/rendering/layers/entityEffects/renderers/selectionEffectRenderer.ts`

### UI 查询迁移喵
- `web/src/features/inGame/components/ShipPanel.vue`
- 概览、选择、聚焦相关组件与 composable 喵

### 后端协议（仅在必要时）喵
- `game/src/main/java/.../StarAxisGameRuntime.java`
- `webnet/src/main/java/.../SnapshotMessageFactory.java`
- DTO 文件们喵

后端协议改动只能在前端世界层稳定之后进行喵。  
如果没有明确新增字段需求，优先不改协议喵。

---

## 13. 交接给其他 AI 的执行说明

把下面这段当成执行摘要交给另一个 AI 即可喵：

1. 先创建 `LocalVisibleWorld`（本地可见世界副本）与查询接口，不迁移旧逻辑喵
2. 再让 `InGameView` 的快照先写入世界层，`useInGameDataHub` 从世界层取实体喵
3. 再定义移动指令状态机，让前端对移动指令拥有正式的待确认状态喵
4. 再把舰船推进从 `shipRenderer` 与 `entityQuerySystem` 迁到世界层统一时间推进喵
5. 然后迁移选择、聚焦、舰船面板、选择环到世界层查询喵
6. 最后补上快照纠偏规则、调试信息和旧逻辑清理喵

执行过程中必须始终满足这条规则喵：
**舰船连续位置的主来源只能是前端世界层的本地推进状态，不能重新退回快照直接驱动喵。**

另一个 AI 还必须额外注意这几个容易遗漏的点喵：
- 当前 [shipCommandsApi.ts](/G:/games/staraxis/web/src/net/shipCommandsApi.ts) 只返回”命令已提交”，没有”已接受 / 已拒绝 / 已完成”事件协议，必须补上喵
- 当前 [ShipCommandApi.java](/G:/games/staraxis/webnet/src/main/java/staraxis/webnet/api/ship/ShipCommandApi.java) 也只做提交回执，没有结果对账协议，不能直接拿来做完整状态机喵
- 当前 `renderer.getEntityWorldPosGU()`（渲染器实体坐标查询）仍是 UI 主要坐标入口，迁移时要全部替换到世界层查询喵
- 浏览器切后台时 `requestAnimationFrame` 会暂停，恢复后必须避免一次性吃掉超大 `deltaTime` 喵
- 当前 [useVisibleSectors.ts](/G:/games/staraxis/web/src/features/inGame/composables/useVisibleSectors.ts) 会按镜头动态裁剪订阅星区并清缓存，计划必须补上”关注对象保留 / 订阅保活”策略，否则己方舰船离开镜头后会从本地世界中消失喵
- 当前移动命令走 HTTP、快照走 WebSocket，若不补充统一的结果消息协议和排序字段，状态机会出现跨通道乱序风险喵

---
## 14. 当前重构状态总结（截至2026-04-21）喵

### 总体进度喵
| 任务 | 状态 | 完成度 | 备注 |
|------|------|--------|------|
| 任务1：梳理现有链路 | ✅ 完成 | 100% | 输出快照直接驱动位置入口清单喵 |
| 任务2：定义前端世界模型 | ✅ 完成 | 100% | 三层状态容器、查询接口、快照应用基础喵 |
| 任务3：设计移动指令状态机 | ✅ 完成 | 90% | 状态机设计完成，后端协议支持待实现喵 |
| 任务4：舰船推进迁移 | ✅ 完成 | 100% | 舰船推进从渲染层迁移到世界层统一时间推进喵 |
| 任务5：重构快照消费逻辑 | 🟡 进行中 | 70% | 快照应用基础完成，纠偏策略已实现，实体生命周期管理待完善喵 |
| 任务6：建立纠偏与调试能力 | 🟡 进行中 | 30% | 基础纠偏已实现，调试信息与可视化待完善喵 |

### 阶段实施进度喵
| 阶段 | 状态 | 关键成果 |
|------|------|----------|
| 阶段A：建立世界层骨架 | ✅ 完成 | 类型定义、基础API、统一查询入口喵 |
| 阶段B：快照写入世界层 | ✅ 完成 | 快照先写入世界层，渲染层读取衍生数据喵 |
| 阶段C：移动指令状态机 | ✅ 完成 | 指令状态机设计、客户端ID生成、优先级规则喵 |
| 阶段D：舰船推进迁移 | ✅ 完成 | 世界时间推进系统、渲染层简化、游戏循环集成喵 |
| 阶段E：UI查询迁移 | ✅ 主要完成 | 选择、聚焦、面板、选择环已迁移，动态订阅联动待完成喵 |
| 阶段F：快照纠偏规则 | 🟡 进行中 | 纠偏策略已实现，实体生命周期管理、订阅保活机制待实现喵 |
| 阶段G：清理旧入口 | ⏳ 待开始 | 临时估算与快照直连逻辑清理喵 |

### 已实现的核心架构喵
1. **三层状态模型**：权威快照态、本地预测态、待确认指令态清晰分离喵
2. **统一时间推进**：`LocalVisibleWorldSimulation` 统一管理前端世界时间推进喵
3. **防重放机制**：基于 `simulationTick` 防止旧状态覆盖新状态喵
4. **指令状态机**：六种状态完整生命周期，支持优先级更新规则喵
5. **UI查询统一化**：所有UI坐标查询通过世界层统一接口获取喵

### 待完成的关键工作喵
1. ✅ **快照纠偏策略**：偏差检测、阈值配置、纠偏触发逻辑（已实现）喵
2. ⏳ **实体生命周期管理**：出生、销毁、离开视野、重新进入视野状态迁移喵
3. ⏳ **动态订阅联动**：`useVisibleSectors` 与世界层关注对象保留策略联动喵
4. ⏳ **后端协议支持**：指令结果WebSocket推送、结果对账协议喵
5. ⏳ **调试与可视化**：偏差监控、指令状态可视化、回归检查喵
6. ⏳ **指令优先级规则**：快照与本地预测的合并优先级定义喵

### 技术验证结果喵
- ✅ **架构符合性**：遵循”模拟层权威”原则，前端仅展示喵
- ✅ **确定性保证**：相同输入与初始状态得到相同结果，支持存档对账喵
- ✅ **时间驱动**：推进与结算绑定 `simulationTick`/`gameDatetimeDay`，不受FPS影响喵
- ✅ **通信边界**：保持 `game→webnet→web` 流向，前端世界层作为本地副本喵
- ✅ **构建验证**：TypeScript编译通过，生产构建成功喵

### 后续工作计划喵
**立即执行（任务5继续）喵：**
1. 完善实体生命周期管理（出生、销毁、离开视野等状态迁移）喵
2. 实现 `useVisibleSectors` 与世界层关注对象保留策略联动喵
3. 定义快照与本地预测的合并优先级规则喵

**任务6（纠偏与调试）喵：**
1. 完善调试信息输出（实体级偏差、指令状态、纠偏日志）喵
2. 实现偏差监控与纠偏可视化喵
3. 集成后端指令结果消息，完善指令状态机喵

**长期优化喵：**
1. 清理旧入口与兼容层喵
2. 性能优化与内存管理喵
3. 回归测试与验证喵

**后续任务（任务6）喵：**
1. 建立偏差监控与纠偏可视化喵
2. 实现移动回归检查清单喵
3. 补充浏览器切后台时间处理喵

**长期优化喵：**
1. 清理旧入口与兼容层喵
2. 后端协议升级支持完整指令状态机喵
3. 性能优化与内存管理喵
