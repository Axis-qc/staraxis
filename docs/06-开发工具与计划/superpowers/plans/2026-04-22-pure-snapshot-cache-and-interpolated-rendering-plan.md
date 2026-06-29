# StarAxis 纯快照缓存与插值渲染重构计划喵
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans喵。严格按本文档顺序实施喵，不要跳阶段并行大改喵。

## 0. 目标与结论喵

**当前进度喵**  
- [x] 阶段 A 已完成喵  
- [x] 阶段 B 已完成喵  
- [x] 阶段 C 已完成喵  
- [x] 阶段 D 已完成喵  
- [x] 阶段 E 已完成喵  
- [x] 阶段 F 已完成喵  
- [x] 阶段 G 已完成喵  
- [x] 阶段 H 代码收尾已完成喵  

**目标喵**  
将当前“前端本地模拟 + 后端权威校验”链路重构为“后端权威计算 + 前端快照缓存 + 插值渲染”链路喵。  
前端不再承担世界物理推进喵，不再维护长期本地预测态喵，只负责喵：

- 接收高频快照喵
- 接收低频快照喵
- 接收命令结果消息喵
- 维护缓存世界喵
- 按 FPS 对相邻权威快照做插值渲染喵
- 处理输入和命令 UI 喵

**核心结论喵**  
凡是不再属于新架构职责的前端本地模拟代码，都要直接删除或改造成缓存用途喵。  
不要保留“也许以后还用得上”的预测层兼容代码喵。  
这次重构的最终形态不是“前端模拟简化版”喵，而是“前端缓存与渲染客户端”喵。

---

## 1. 架构原则喵

- [ ] 后端 Java 世界是唯一权威世界喵
- [x] 前端 `LocalVisibleWorld` 改造成缓存世界，不再是本地模拟世界喵
- [x] 前端显示流畅性由“高频快照缓存 + 插值/短时外推”保证喵
- [x] 命令结果由后端权威确认，前端不再上报“本地完成结果”喵
- [x] 高频快照、低频快照、命令结果三条通道分离喵
- [x] 所有排序统一以 `simulationTick` 为主序喵
- [x] 所有增量同步都必须有基线恢复方案喵

---

## 2. 明确不再保留的旧链路喵

以下能力不再属于目标架构喵，必须删除或彻底失效喵：

- [x] `predictedShipsById` 作为主状态来源喵
- [x] `ShipMovementSystemFrontend` 参与真实实体推进喵
- [x] `LocalVisibleWorldSimulation` 推进舰船位置喵
- [x] `movementSeed` 驱动的长期本地预测喵
- [x] `advancePredictedShips` / `advancePredictedShipState` 主链喵
- [x] `syncPredictedShips` 主链喵
- [x] `ensurePredictedStateForCommand` / `reseedPredictedStateIfNeeded` 主链喵
- [x] `sendMoveShipCompletionReport` 及其前端调用链喵
- [x] “本地判断舰船到达后认为 completed” 的逻辑喵
- [x] 视图层主动推进世界时间或推进实体运动喵

如果某个旧文件在新架构下没有新职责，就直接删除喵。  
不要改成“空壳兼容层”喵。

---

## 3. 新架构目标分层喵

### 3.1 前端最终只保留这五层喵

1. `SnapshotTransport` 喵  
职责：接收高频快照、低频快照、命令结果喵。

2. `WorldCache` 喵  
职责：保存最新权威实体状态、最近若干个高频实体帧、低频状态缓存、命令 UI 状态喵。

3. `InterpolationLayer` 喵  
职责：给渲染层提供按当前帧时间计算出的显示姿态喵。

4. `InputAndCommandUi` 喵  
职责：下发命令、显示目标点、显示命令提交状态和命令结果喵。

5. `RendererAndPanels` 喵  
职责：统一从缓存世界和插值层取数喵，不再自己推进实体喵。

### 3.2 前端最终不应承担的职责喵

- [ ] 不再推进舰船真实移动喵
- [ ] 不再根据命令直接改实体权威位置喵
- [ ] 不再根据本地时间推断命令完成喵
- [ ] 不再把快照和预测状态双写到同一实体显示链路喵

---

## 4. 协议设计喵

## 4.1 高频快照协议喵

**用途喵**  
用于移动实体、战斗中实体、镜头内实时实体、选中对象、聚焦对象、需要连续渲染的对象喵。

**频率喵**  
默认按后端 tick 推送喵，即 `20 tick/s`，约每 `50ms` 一次喵。

**消息要求喵**

- [x] 每条高频快照必须带 `simulationTick` 喵
- [x] 每条高频快照必须带连续时间字段，例如 `totalGameSecondsExact` 喵
- [x] 每条高频快照必须带消息类型字段，例如 `type: 'snapshot_high_freq'` 喵
- [x] 每条高频快照必须说明自己是 `full` 还是 `delta` 喵
- [x] 如果是 `delta`，必须带 `baseTick` 喵

**高频实体字段要求喵**

- [x] `entityId` 喵
- [x] `entityType` 喵
- [x] `posWorldGU` 喵
- [x] `velocity` 喵
- [x] `headingDeg` 喵
- [x] `isMoving` 喵
- [x] `movementTarget` 喵
- [x] 必要时带 `movementCommand` 喵
- [ ] 必要时带实体显隐/删除标记喵

**增量恢复规则喵**

- [x] 客户端收到 `delta` 包时，必须检查 `baseTick === lastAppliedHighFreqTick` 喵
- [x] 如果基线不连续，客户端不能硬合并喵，必须触发一次高频全量重同步喵
- [ ] 允许服务端每隔固定 tick 发送高频 keyframe 喵，降低丢包恢复成本喵

## 4.2 低频快照协议喵

**用途喵**  
用于国家、经济、建筑、科研、人口、外交、面板统计、非实时大对象信息喵。

**消息要求喵**

- [x] 消息类型独立，例如 `type: 'snapshot_low_freq'` 喵
- [x] 必须带 `simulationTick` 或低频 `version` 喵
- [x] 不进入插值层喵
- [x] 低频更新只改低频缓存喵

## 4.3 命令结果协议喵

**用途喵**  
表达命令是否被接受、拒绝、完成或纠偏喵。

**消息要求喵**

- [x] 消息类型独立，例如 `type: 'command_result'` 喵
- [x] 必须带 `clientCommandId` 喵
- [x] 必须带 `entityId` 喵
- [x] 必须带 `simulationTick` 喵
- [x] 状态至少包括 `submitted`、`accepted`、`rejected`、`completed`、`corrected` 喵

**状态优先级规则喵**

- [x] HTTP `ok` 只表示提交成功，不表示命令已被接受喵
- [x] `accepted/rejected/completed/corrected` 只能由后端权威结果消息决定喵
- [x] 实体位置变化不能被当成命令状态的唯一来源喵

---

## 5. 字段所有权矩阵喵

必须明确每类字段由哪条通道负责喵，避免不同消息互相覆盖喵。

### 5.1 高频快照唯一负责的字段喵

- [x] 实体位置 `posWorldGU` 喵
- [x] 实体速度 `velocity` 喵
- [x] 实体朝向 `headingDeg` 喵
- [x] 实体移动态 `isMoving` 喵
- [x] 实体移动目标 `movementTarget` 喵
- [x] 实体移动命令快照 `movementCommand` 喵
- [ ] 实体出现、销毁、显隐切换喵

### 5.2 低频快照唯一负责的字段喵

- [x] 经济状态喵
- [x] 建筑状态喵
- [x] 科研状态喵
- [x] 人口与国家面板统计喵
- [x] 低频非实时 UI 数据喵

### 5.3 命令结果唯一负责的字段喵

- [x] 命令状态喵
- [x] 命令拒绝原因喵
- [x] 命令纠偏原因喵
- [x] 命令完成确认喵

### 5.4 严禁跨通道混写的规则喵

- [x] 低频快照不能写高频实体位置喵
- [x] HTTP 回包不能直接推进实体坐标喵
- [x] 插值层不能回写权威缓存喵
- [x] 命令 UI 状态不能反向覆盖权威实体状态喵

---

## 6. 订阅范围与缓存保活规则喵

这是本次计划必须明确的一层喵，否则实现一定会做歪喵。

### 6.1 订阅范围不等于缓存保活范围喵

- [x] 当前镜头订阅范围只决定“向后端请求哪些高频区域数据”喵
- [x] 缓存保活范围决定“哪些实体即使不在当前订阅范围也不能立刻删”喵

### 6.2 必须保活的对象喵

- [x] 己方舰船喵
- [x] 当前选中对象喵
- [x] 当前聚焦对象喵
- [ ] 当前打开面板对应对象喵
- [x] 刚刚发过命令且尚未收到终态结果的对象喵
- [ ] 当前镜头外但仍在战斗或关键事件中的己方对象喵

### 6.3 允许清理的对象喵

- [ ] 非己方、非选中、非聚焦、非面板目标、非事件相关、超出保活时间的实体喵

### 6.4 计划实施要求喵

- [ ] 必须把 `useVisibleSectors` 与缓存清理逻辑一起改喵
- [ ] 不能只改订阅上报，不改本地缓存清理喵
- [ ] 不能沿用当前“镜头外且未选中就清缓存”的旧逻辑喵

---

## 7. 插值层参数必须固定喵

不能把插值层做成一套自由发挥的隐藏预测器喵。  
以下参数必须先在实现中固定喵。

- [x] `interpolationDelayMs` 喵  
推荐初始值：`100ms` 喵。

- [x] `maxExtrapolationMs` 喵  
推荐初始值：`100ms` 喵。

- [x] `teleportThresholdGU` 喵  
超过该阈值时直接 snap，不做平滑插值喵。

- [x] `resumeResetThresholdMs` 喵  
页面后台切回、长时间卡顿后，超过该阈值直接重建插值窗口喵。

- [x] `maxBufferedHighFreqTicks` 喵  
缓存最近若干高频 tick，推荐 `4~8` 喵。

### 7.1 插值规则喵

- [x] 优先使用 `tick N` 与 `tick N+1` 做线性插值喵
- [x] 朝向使用角度插值喵
- [x] 没有后一帧时，只允许短时间外推喵
- [x] 外推时间超过上限后，显示应冻结在最后一帧或等待重同步喵
- [ ] 被标记为 teleport/corrected 的实体直接 snap 喵

---

## 8. 按顺序执行的实施阶段喵

## 阶段 A：冻结协议与恢复规则喵

**当前状态喵**  
- [x] 已完成喵  

**目标文件喵**
- [x] [snapshotWs.ts](/G:/games/staraxis/web/src/net/snapshotWs.ts) 喵
- [ ] [EntitySnapshot.java](/G:/games/staraxis/game/src/main/java/staraxis/game/state/snapshot/EntitySnapshot.java) 喵
- [ ] [RealTimeWorldState.java](/G:/games/staraxis/game/src/main/java/staraxis/game/state/RealTimeWorldState.java) 喵
- [x] [SnapshotMessageFactory.java](/G:/games/staraxis/webnet/src/main/java/staraxis/webnet/websocket/SnapshotMessageFactory.java) 喵
- [x] [SnapshotBroadcaster.java](/G:/games/staraxis/webnet/src/main/java/staraxis/webnet/websocket/SnapshotBroadcaster.java) 喵

**必须完成的任务喵**
- [x] 定义高频快照消息喵
- [x] 定义低频快照消息喵
- [x] 定义命令结果消息喵
- [x] 定义全量与增量格式喵
- [x] 定义 `baseTick` 与丢包恢复规则喵
- [x] 定义重连与客户端主动请求全量同步规则喵

**完成标准喵**
- [x] 另一个阶段开始前，协议已经固定喵
- [x] 不再允许边实现边发明字段喵

## 阶段 B：先改缓存世界，再删预测世界喵

**当前状态喵**  
- [x] 已完成喵  

**目标文件喵**
- [x] [localVisibleWorld.ts](/G:/games/staraxis/web/src/game/world/localVisibleWorld.ts) 喵
- [x] [localVisibleWorldTypes.ts](/G:/games/staraxis/web/src/game/world/localVisibleWorldTypes.ts) 喵
- [x] [localVisibleWorldQueries.ts](/G:/games/staraxis/web/src/game/world/localVisibleWorldQueries.ts) 喵
- [x] [index.ts](/G:/games/staraxis/web/src/game/world/index.ts) 喵

**必须完成的任务喵**
- [x] 把 `LocalVisibleWorld` 改造成缓存世界喵
- [x] 加入高频帧缓存结构喵
- [x] 加入低频状态缓存结构喵
- [x] 保留命令 UI 状态缓存喵
- [x] 加入高频 `lastAppliedTick` 与低频 `lastAppliedVersion` 喵
- [x] 加入缓存保活规则和兴趣集合喵

**这一步先不要删的内容喵**
- [x] 旧预测字段可以先保留一小段过渡期，但必须停止成为主来源喵

**完成标准喵**
- [x] 所有新查询都能从缓存世界拿到最新权威数据喵
- [x] 预测状态不再是主链路喵

## 阶段 C：实现插值缓冲层喵

**当前状态喵**  
- [x] 已完成喵  

**目标文件喵**
- [x] 新增 `web/src/game/world/interpolation/` 喵
- [x] 新增 `snapshotInterpolationBuffer.ts` 喵
- [x] 新增 `entityInterpolation.ts` 喵
- [ ] 视情况新增 `renderClock.ts` 喵

**必须完成的任务喵**
- [x] 建立基于 `simulationTick` 的高频帧缓冲喵
- [x] 实现统一插值查询接口喵
- [x] 实现位置/朝向插值喵
- [x] 实现短时外推喵
- [x] 实现 teleport/snap 规则喵
- [x] 实现后台恢复后的窗口重置喵

**完成标准喵**
- [x] 插值层不回写缓存喵
- [x] 插值层不承担游戏逻辑喵

## 阶段 D：渲染器与 UI 全迁移喵

**当前状态喵**  
- [x] 已完成喵  

**目标文件喵**
- [x] [shipRenderer.ts](/G:/games/staraxis/web/src/rendering/layers/entity/renderers/shipRenderer.ts) 喵
- [x] [entityQuerySystem.ts](/G:/games/staraxis/web/src/rendering/systems/entityQuerySystem.ts) 喵
- [ ] [worldRenderManager.ts](/G:/games/staraxis/web/src/rendering/worldRenderManager.ts) 喵
- [x] [ShipPanel.vue](/G:/games/staraxis/web/src/features/inGame/components/ShipPanel.vue) 喵
- [ ] 所有依赖实体位置的 HUD 和面板喵

**必须完成的任务喵**
- [x] 舰船渲染改用插值查询喵
- [x] 选择、聚焦、面板定位改用插值查询喵
- [x] 路径线、目标点、选择圈改用缓存世界权威数据喵
- [x] 移除渲染器内部的预测时间推进喵

**完成标准喵**
- [x] 渲染层不再引用预测状态主链喵

## 阶段 E：删除本地模拟主链喵

**当前状态喵**  
- [x] 已完成喵  

**目标文件喵**
- [x] [localVisibleWorldSimulation.ts](/G:/games/staraxis/web/src/game/world/localVisibleWorldSimulation.ts) 喵
- [x] [ShipMovementSystemFrontend.ts](/G:/games/staraxis/web/src/game/systems/ShipMovementSystemFrontend.ts) 喵
- [x] [shipPositionEstimator.ts](/G:/games/staraxis/web/src/game/shipPositionEstimator.ts) 喵
- [x] [GameTimeManager.ts](/G:/games/staraxis/web/src/game/time/GameTimeManager.ts) 喵
- [x] [InGameView.vue](/G:/games/staraxis/web/src/views/InGameView.vue) 喵

**必须完成的任务喵**
- [x] 删掉本地实体推进喵
- [x] 删掉命令种子驱动的本地物理更新喵
- [x] 删掉完成上报链路喵
- [x] 删掉前端本地到达判定喵
- [x] 将 `GameTimeManager` 收敛为渲染时间辅助喵

**完成标准喵**
- [x] 前端代码中不再存在真实世界推进链路喵

## 阶段 F：重构命令链路喵

**当前状态喵**  
- [x] 已完成喵  

**目标文件喵**
- [x] [shipCommandsApi.ts](/G:/games/staraxis/web/src/net/shipCommandsApi.ts) 喵
- [x] [localVisibleWorldCommands.ts](/G:/games/staraxis/web/src/game/world/localVisibleWorldCommands.ts) 喵
- [x] [useRtsRightClickCommand.ts](/G:/games/staraxis/web/src/features/inGame/commands/useRtsRightClickCommand.ts) 喵
- [x] [InGameView.vue](/G:/games/staraxis/web/src/views/InGameView.vue) 喵

**必须完成的任务喵**
- [x] 保留 `clientCommandId` 喵
- [x] HTTP 回包只当 transport ack 喵
- [x] 真正命令状态只由命令结果消息决定喵
- [x] 前端只维护命令 UI 态喵
- [x] 前端可立即显示目标点和路径标记喵
- [x] 但不能立即改实体权威位置喵

**完成标准喵**
- [x] 命令 UI 和实体位置解耦喵

## 阶段 G：高低频分流落地喵

**当前状态喵**  
- [x] 已完成喵  

**目标文件喵**
- [x] 后端快照广播相关文件喵
- [x] [snapshotWs.ts](/G:/games/staraxis/web/src/net/snapshotWs.ts) 喵
- [x] [useInGameDataHub.ts](/G:/games/staraxis/web/src/features/inGame/composables/useInGameDataHub.ts) 喵
- [x] 国家/建筑/经济/科技相关 UI 喵

**必须完成的任务喵**
- [x] 高频只服务实时渲染喵
- [x] 低频只服务面板数据喵
- [x] 两条通道分别缓存与排序喵
- [x] 不允许低频覆盖高频实体位置喵

**完成标准喵**
- [x] 高频链路与低频链路互不污染喵

## 阶段 H：删除残留、补文档、跑回归喵

**当前状态喵**  
- [x] 代码与文档收尾已完成喵  
- [ ] 场景回归测试待人工执行喵  

**目标文件喵**
- [x] 删除无用预测文件喵
- [x] 更新日志与模块说明喵

**必须完成的任务喵**
- [x] 删除无引用的预测代码喵
- [x] 删除误导性注释喵
- [x] 更新相关文档喵
- [x] 跑前端构建喵
- [ ] 按场景回归测试喵

**完成标准喵**
- [x] 仓库里不再保留无职责的前端模拟层喵

---

## 9. 文件级写入范围喵

### 9.1 前端喵

- [ ] `web/src/game/world/` 喵
- [ ] `web/src/net/` 喵
- [ ] `web/src/rendering/` 喵
- [ ] `web/src/views/InGameView.vue` 喵
- [ ] `web/src/features/inGame/` 中所有依赖实体位置和命令状态的 UI 喵

### 9.2 后端喵

- [ ] `webnet/src/main/java/staraxis/webnet/websocket/` 喵
- [ ] `webnet/src/main/java/staraxis/webnet/api/ship/` 喵
- [ ] `game/src/main/java/staraxis/game/state/` 喵
- [ ] `game/src/main/java/staraxis/game/state/snapshot/` 喵

### 9.3 直接删除候选喵

- [ ] [ShipMovementSystemFrontend.ts](/G:/games/staraxis/web/src/game/systems/ShipMovementSystemFrontend.ts) 喵
- [ ] [shipPositionEstimator.ts](/G:/games/staraxis/web/src/game/shipPositionEstimator.ts) 喵
- [ ] [localVisibleWorldSimulation.ts](/G:/games/staraxis/web/src/game/world/localVisibleWorldSimulation.ts) 喵

---

## 10. 提交拆分建议喵

### Commit 1：协议冻结喵
- [x] 定义高频、低频、命令结果三类协议喵
- [x] 明确定义 `baseTick` 与重同步规则喵

### Commit 2：缓存世界重构喵
- [x] `LocalVisibleWorld` 改造成缓存世界喵
- [x] 接入兴趣集合与保活策略喵

### Commit 3：插值层落地喵
- [x] 新增插值缓冲和统一查询接口喵

### Commit 4：渲染器和 UI 迁移喵
- [x] 所有实体显示统一迁移到插值查询喵

### Commit 5：删除本地模拟喵
- [x] 删掉前端本地推进、完成上报、命令种子预测喵

### Commit 6：高低频分流喵
- [x] 高频/低频通道和缓存分流落地喵

### Commit 7：收尾喵
- [x] 删除残留预测代码，补日志与回归准备喵

---

## 11. 强制验收标准喵

- [ ] 不发送命令时，镜头内移动实体仍能持续流畅显示喵
- [ ] 发送命令时，不再出现“前端先跳一下再被后端拉回”的抽动喵
- [x] 关闭前端预测推进相关模块后，游戏依然能正常运行喵
- [ ] 高频快照丢一包后，客户端能检测基线断裂并恢复喵
- [ ] 订阅范围切换后，己方单位和关注对象不会被错误清理喵
- [x] 低频面板更新不会覆盖高频实体状态喵
- [x] 仓库内不再保留无职责的前端模拟主链喵

---

## 12. 交接摘要喵

另一个 AI 必须严格按阶段 A 到 H 顺序实施喵。  
不要先删代码再补协议喵。  
不要先删预测层再补缓存保活喵。  
不要边做边重新发明消息格式喵。  
先定协议和恢复规则喵，再改缓存世界喵，再加插值层喵，再迁移渲染与 UI 喵，最后删除本地模拟层喵。  
本文档已经补齐了容易遗漏的五类坑喵：增量基线恢复、订阅与保活分离、命令状态权威来源、插值参数固定、高低频字段所有权矩阵喵。  
照这个顺序做即可喵。
