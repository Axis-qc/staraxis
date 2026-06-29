# StarAxis 双逻辑帧缓冲与渲染帧插值计划喵
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans喵。严格按本文档顺序实施喵，不要把“逻辑帧缓存”和“渲染帧平滑”混成前端模拟喵。

## 0. 目标与结论喵

**当前进度喵**  
- [x] 阶段 A 已完成喵  
- [x] 阶段 B 已完成喵  
- [x] 阶段 C 已完成喵  
- [x] 阶段 D 已完成喵  
- [ ] 阶段 E 未开始喵  

**目标喵**  
将当前前端插值层进一步收敛为“相邻两个权威逻辑帧 + 中间渲染帧插值”的双帧模型喵。  
前端不做真实世界推进喵，不根据本地时间猜测未来权威结果喵，只做这三件事喵：

- 保存上一逻辑帧快照喵
- 保存下一缓冲逻辑帧快照喵
- 用渲染帧在两帧之间做插值填充喵

**核心结论喵**  
“留一逻辑帧缓冲”的目的不是故意让画面更慢喵，而是让渲染帧始终拿到一前一后两个权威状态点喵。  
当前渲染周期内喵，前一逻辑帧必须保持固定喵，后一逻辑帧则允许被新到的高频快照持续刷新喵。  
只要“当前逻辑帧”和“下一逻辑帧缓冲”都在手里喵，中间所有渲染帧都应该立即开始插值喵。  
这里的“逻辑帧”口径固定与后端权威逻辑周期一致喵，当前按 `50ms` 定义喵。  
`高频快照` 的职责是更快送达高实时性权威状态喵，不是改变前端逻辑帧时长喵。  
命令反馈要即时喵，但单位本体仍只服从后端权威逻辑帧喵。

---

## 1. 架构原则喵

- [ ] 后端快照仍是唯一权威状态来源喵
- [ ] 后端权威逻辑帧周期固定为 `50ms` 喵
- [ ] 前端逻辑帧定义必须与后端 `50ms` 权威逻辑帧一致喵
- [ ] 前端渲染时间轴固定落后于最新权威逻辑时间轴 `1` 个逻辑帧，也就是约 `50ms` 喵
- [ ] 渲染层只读取“当前逻辑帧 + 下一逻辑帧缓冲”这组双缓冲，不直接推进世界喵
- [ ] 无后一逻辑帧时才允许短时外推喵
- [ ] 命令 UI 即时反馈与实体权威位置显示必须解耦喵
- [ ] “一逻辑帧缓冲”必须按权威时间定义，不能按网络消息包数量定义喵
- [ ] 高频快照只能提升高实时权威状态的送达实时性，不能改变逻辑帧时间基准喵

---

## 2. 新模型定义喵

### 2.1 双逻辑帧窗口喵

前端渲染窗口只关心这两个核心逻辑帧缓冲喵：

- `currentSnapshot`：当前正在被渲染的权威逻辑帧喵
- `nextSnapshotBuffer`：下一目标逻辑帧缓冲喵，在当前渲染周期内可被新高频快照持续更新喵

这两个快照必须都带喵：

- `simulationTick`（权威模拟 Tick）喵
- `totalGameSecondsExact`（精确权威时间）喵
- 实体位置、速度、朝向、目标点等高频姿态字段喵

**固定口径喵**

- 逻辑帧时间步长固定按后端权威周期 `50ms` 定义喵
- `currentSnapshot` 与 `nextSnapshotBuffer` 表示相邻权威逻辑帧喵，不表示任意相邻网络消息包喵
- 高频快照可以更早送到喵，但它送达的是“下一逻辑帧的更新内容”喵，不是新的可变时长逻辑帧喵
- 当前渲染周期内，新到高频快照只能更新 `nextSnapshotBuffer` 喵，不能回写 `currentSnapshot` 喵
- 只有当当前渲染周期结束时喵，`nextSnapshotBuffer` 才能升格为新的 `currentSnapshot` 喵
- 渲染主窗口只消费双缓冲喵，但底层缓存仍可保留少量额外帧用于乱序包、恢复和滑窗切换喵

### 2.2 渲染帧职责喵

每个 `render frame`（渲染帧）只做喵：

1. 计算当前 `renderTime`（渲染时间）喵  
2. 读取当前 `currentSnapshot` 和当前时刻的 `nextSnapshotBuffer` 喵  
3. 计算插值因子 `alpha` 喵  
4. 输出本帧显示姿态喵

公式口径喵：

`displayPose = interpolate(currentSnapshot, nextSnapshotBuffer, alpha)` 喵

### 2.3 跟手性口径喵

- 命令点击后的目标点、路径线、光标反馈应立即显示喵
- 单位本体位置只能在收到变化后的下一权威逻辑帧后开始插值喵
- 一旦 `nextSnapshotBuffer` 已建立喵，画面应立刻进入 `current -> nextBuffer` 插值喵，不能再额外等待一帧喵
- 当前渲染周期内后续到达的高频快照喵，应持续刷新 `nextSnapshotBuffer` 的实体状态喵
- 高频快照存在的意义是减少“权威变化已发生但前端还没收到”的送达延迟喵，不是让前端逻辑帧变成可变周期喵

---

## 3. 明确不应出现的错误实现喵

- [ ] 不能把双逻辑帧插值重新做成前端本地模拟器喵
- [ ] 不能因为保留一逻辑帧缓冲，就在建立 `nextSnapshotBuffer` 后再多等一逻辑帧才开始动喵
- [ ] 不能把“消息包到达顺序”直接等同于“逻辑帧时间顺序”喵
- [ ] 不能把“高频快照发送更快”误解为“前端逻辑帧变成更高频或可变周期”喵
- [ ] 不能在当前渲染周期内直接修改 `currentSnapshot` 喵
- [ ] 不能把当前周期内多次到达的高频快照误当成多个新的逻辑帧端点喵
- [ ] 不能用低频面板数据反向影响高频实体姿态喵
- [ ] 不能让命令 UI 反馈依赖权威实体位置变化后才出现喵
- [ ] 不能把无后一帧场景默认当成正常插值，而应明确进入短时外推或冻结喵

---

## 4. 目标文件喵

### 4.1 核心实现喵

- [x] [snapshotInterpolationBuffer.ts](/G:/games/staraxis/web/src/game/world/interpolation/snapshotInterpolationBuffer.ts) 喵
- [x] [entityInterpolation.ts](/G:/games/staraxis/web/src/game/world/interpolation/entityInterpolation.ts) 喵
- [x] [localVisibleWorld.ts](/G:/games/staraxis/web/src/game/world/localVisibleWorld.ts) 喵
- [x] [GameTimeManager.ts](/G:/games/staraxis/web/src/game/time/GameTimeManager.ts) 喵

### 4.2 渲染接入喵

- [x] [shipRenderer.ts](/G:/games/staraxis/web/src/rendering/layers/entity/renderers/shipRenderer.ts) 喵
- [x] [entityQuerySystem.ts](/G:/games/staraxis/web/src/rendering/systems/entityQuerySystem.ts) 喵
- [x] [worldRenderManager.ts](/G:/games/staraxis/web/src/rendering/worldRenderManager.ts) 喵

### 4.3 UI / 调试喵

- [x] [InGameView.vue](/G:/games/staraxis/web/src/views/InGameView.vue) 喵
- [x] [useInGameDataHub.ts](/G:/games/staraxis/web/src/features/inGame/composables/useInGameDataHub.ts) 喵
- [x] 相关 Debug 文本与计划文档喵

---

## 5. 按顺序执行的实施阶段喵

## 阶段 A：明确双帧窗口数据模型喵

**必须完成的任务喵**
- [x] 明确缓冲窗口的最小必要字段喵
- [x] 明确 `currentSnapshot/nextSnapshotBuffer` 的替换与升格规则喵
- [x] 明确窗口切换时的升帧规则喵
- [x] 明确掉帧、迟到包、乱序包的处理规则喵
- [x] 明确当前渲染周期内高频快照只更新 `nextSnapshotBuffer` 的规则喵

**完成标准喵**
- [x] 插值层内部不再依赖“最近 N 帧模糊搜索”作为主逻辑喵
- [x] 渲染主链能明确回答“当前帧是谁、下一帧缓冲是谁”喵

## 阶段 B：把渲染时间轴收敛到一逻辑帧缓冲喵

**必须完成的任务喵**
- [x] 将当前固定延迟口径改成“固定一逻辑帧缓冲 = 50ms”喵
- [x] 使用权威时间字段而不是本地帧序号决定插值区间喵
- [x] 确保建立 `nextSnapshotBuffer` 后立即可进入插值喵
- [x] 明确高频快照只提前送达逻辑帧状态，不改变逻辑帧时基喵

**完成标准喵**
- [x] 画面延迟由固定逻辑帧缓冲决定，而不是额外等待策略决定喵

## 阶段 C：重写插值/外推切换规则喵

**必须完成的任务喵**
- [x] 有前后帧时强制走插值喵
- [x] 只有前帧没有后帧时才允许短时外推喵
- [x] 外推超时后冻结在最后权威姿态喵
- [x] teleport / corrected 必须直接 snap 喵
- [x] 明确“当前周期内 nextBuffer 被刷新”不会导致 alpha 重置喵

**完成标准喵**
- [x] 插值与外推的切换条件在代码层面唯一且清晰喵

## 阶段 D：渲染器与查询系统完全按双帧模型读取喵

**必须完成的任务喵**
- [x] 渲染器统一走双帧插值查询喵
- [x] 行星与非舰船实体的时间口径同步检查喵
- [x] 调试面板可显示当前 `currentTick/nextTick/renderAlpha` 喵

**完成标准喵**
- [x] 渲染侧可以解释当前帧为什么画在这个位置喵

## 阶段 E：验证跟手性与视觉稳定性喵

**必须完成的任务喵**
- [ ] 验证命令 UI 即时反馈仍然保留喵
- [ ] 验证单位本体在收到变化后能立刻进入插值喵
- [ ] 验证无后一帧时不会乱跳喵
- [ ] 更新日志、文档与调试说明喵

**完成标准喵**
- [ ] 玩家主观感受到的是“命令立刻有反馈，单位权威运动平滑开始”喵

---

## 6. 关键规则喵

### 6.1 升帧规则喵

初次连接时喵：

- 第一份快照建立 `currentSnapshot` 喵
- 后续到来的下一份快照建立 `nextSnapshotBuffer` 喵
- 一旦 `nextSnapshotBuffer` 存在喵，当前渲染周期就在 `current -> nextBuffer` 之间插值喵

在当前渲染周期内喵：

- 新到的高频快照只允许更新 `nextSnapshotBuffer` 喵
- 不允许回写 `currentSnapshot` 喵
- 不允许把同一周期内多次高频更新解释成多个新的逻辑帧端点喵

当满足以下条件时喵，窗口应从 `N/N+1缓冲` 升到 `N+1/N+2缓冲` 喵：

- 当前渲染时间已走完 `currentSnapshot -> nextSnapshotBuffer` 这一整段逻辑帧区间喵
- 且 `N+2` 已经到达喵

升格时喵：

- `nextSnapshotBuffer` 升格为新的 `currentSnapshot` 喵
- 后续新到快照开始写新的 `nextSnapshotBuffer` 喵

如果当前渲染时间已走完 `current -> nextBuffer` 但新的下一帧缓冲尚未建立喵，当前窗口不能继续假装自己处于正常双帧插值态喵，而必须明确进入“短时外推”或“冻结在 nextBuffer”状态喵。

### 6.2 乱序与迟到包喵

- [x] 小于当前 `currentSnapshot` 的旧帧直接丢弃喵，同 Tick 高频包只用于同帧覆盖规则喵
- [x] 当前渲染周期内，合法新包只能用于更新 `nextSnapshotBuffer` 喵
- [x] 同一目标逻辑帧的多份高频快照只能覆盖 `nextSnapshotBuffer` 的内容，不能扩张成新的逻辑帧端点喵
- [ ] 若窗口断裂，则请求重同步或退回安全冻结喵

### 6.3 命令视觉反馈喵

- [ ] 目标点喵
- [ ] 路径线喵
- [ ] 选择反馈喵
- [ ] 命令提交状态喵

这些都要即时显示喵，但不能直接写实体权威位置喵。

---

## 7. 强制验收标准喵

- [ ] 同一实体在 `prev/next` 两逻辑帧之间能持续平滑运动喵
- [ ] 收到变化后的下一权威逻辑帧缓冲建立后，渲染应立即开始插值喵
- [ ] 不再出现“有后一帧了但还要多等一帧才开始动”的错误喵
- [ ] 无后一帧场景下只发生短时外推或冻结，不发生乱跳喵
- [ ] 命令点击后立即有 UI 反馈喵
- [ ] 单位本体仍只服从权威快照喵
- [ ] 当前渲染周期内，`currentSnapshot` 不会被后续高频快照篡改喵

---

## 8. 交接摘要喵

这份计划的核心不是“继续增强预测”喵，而是把现有插值层严格收敛成“双逻辑帧窗口 + 中间渲染帧填充”模型喵。  
渲染帧只补画面，不推进世界喵。  
当前逻辑帧固定渲染喵，下一逻辑帧缓冲在当前周期内可被高频快照持续更新喵。  
一旦下一逻辑帧缓冲建立喵，就应立刻开始插值喵，不能再额外等待喵。  
逻辑帧时间基准固定按后端 `50ms` 权威周期定义喵。  
高频快照只负责更快送达高实时权威状态喵，不改变前端逻辑帧周期喵。  
命令即时反馈保留喵，但实体权威位置不允许前端先改喵。
