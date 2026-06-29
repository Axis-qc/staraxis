# StarAxis 星球渲染与选中指示器分层迁移设计文档

**日期**：2026-04-15
**主题**：星球渲染与选中指示器分层架构迁移
**状态**：已批准
**作者**：Claude Code

## 1. 项目背景与需求

### 1.1 问题描述
前端分层渲染重构完成后，部分功能尚未完全迁移到新架构中喵。具体问题包括：

1. **星球渲染缺失**：`CelestialLayer`（星体层）仅实现了恒星渲染器，行星渲染器为 `TODO` 状态
2. **选中指示器未集成**：原有的 `SelectionRenderer`（选中环渲染器）未集成到分层架构，导致实体选中状态无视觉反馈喵
3. **架构不统一**：部分渲染逻辑仍停留在子系统架构，与新分层架构风格不一致喵

### 1.2 核心需求
1. **恢复星球渲染功能**：在星体层中完整支持行星渲染，包括表面纹理、拖尾效果等高级功能喵
2. **恢复选中指示器功能**：新建专门的选择层，为所有实体类型（恒星、行星、舰船）提供统一的绿色虚线圈选中指示喵
3. **保持架构整洁**：完全迁移到分层架构，避免新旧架构混合造成的代码混乱和耦合喵

## 2. 设计方案概述

### 2.1 架构决策
采用**完整分层迁移方案**，将星球渲染和选中指示器完全集成到分层架构中喵。

**核心原则**：
- ✅ **星球渲染在星体层**：行星作为天体类型，自然归属 `CelestialLayer` 管理喵
- ✅ **选中指示器独立层**：新建 `SelectionLayer` 统一处理所有实体类型的选中状态喵
- ✅ **代码复用优先**：复用现有业务逻辑，仅重构架构适配层，降低风险喵

### 2.2 整体架构图
```
WorldRenderManager (协调器)
├── BackgroundLayer (背景层, renderOrder: 0)
├── CelestialLayer (星体层, renderOrder: 1)   ← 新增行星渲染
│   ├── LayerStarRenderer (恒星渲染器)
│   └── LayerPlanetRenderer (行星渲染器)     ← 新增
├── EntityLayer (实体层, renderOrder: 2)
│   └── LayerShipRenderer (舰船渲染器)
└── SelectionLayer (选择层, renderOrder: 4)   ← 新增
    └── LayerSelectionRenderer (选中环渲染器)
```

**渲染顺序说明**：
- `RenderOrder.BACKGROUND` (0): 背景层
- `RenderOrder.CELESTIAL` (1): 星体层（恒星、行星）
- `RenderOrder.ENTITY` (2): 实体层（舰船、空间站）
- `RenderOrder.SELECTION` (4): 选择层（选中指示器）*注：预留3给特效层喵

## 3. 详细设计

### 3.1 星球渲染迁移设计

#### 3.1.1 新增 LayerPlanetRenderer
**位置**：`web/src/rendering/layers/celestial/renderers/planetRenderer.ts`
**职责**：适配现有 `PlanetRenderer` 子系统逻辑到分层架构喵

**核心功能**：
- **2D精灵渲染**：复用现有的行星表面纹理加载和精灵渲染逻辑喵
- **拖尾效果**：保留行星移动轨迹（三角形带+Shader实现）喵
- **对象池管理**：复用现有的对象池机制，减少GC压力喵
- **LOD支持**：集成现有的LOD系统，根据缩放级别调整渲染质量喵

**接口设计**：
```typescript
class LayerPlanetRenderer {
  // 构造函数
  constructor(parentGroup: THREE.Group)

  // 生命周期方法
  init(ctx: WorldRenderContext): void
  update(ctx: WorldRenderContext, frame: WorldFrameState): void
  dispose(): void

  // 私有方法（复用现有逻辑）
  private updateTrail(): void      // 更新拖尾效果
  private loadAndApplyTexture(): void // 纹理加载
}
```

#### 3.1.2 CelestialLayer 集成
**修改位置**：`web/src/rendering/layers/celestial/celestialLayer.ts`
**集成方式**：
```typescript
class CelestialLayer extends BaseLayer {
  private _starRenderer: LayerStarRenderer
  private _planetRenderer: LayerPlanetRenderer  // 新增

  init(ctx: WorldRenderContext): void {
    // 初始化恒星渲染器（现有）
    this._starRenderer = new LayerStarRenderer(this.group)
    this._starRenderer.init()

    // 初始化行星渲染器（新增）
    this._planetRenderer = new LayerPlanetRenderer(this.group)
    this._planetRenderer.init(ctx)
  }

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    if (!this.visible) return

    // 更新恒星渲染器（现有）
    if (this._starRenderer) {
      this._starRenderer.update(ctx, frame)
    }

    // 更新行星渲染器（新增）
    if (this._planetRenderer) {
      this._planetRenderer.update(ctx, frame)
    }

    this.updateTimestamp()
  }

  dispose(ctx: WorldRenderContext): void {
    // 清理渲染器
    if (this._starRenderer) {
      this._starRenderer.dispose()
      this._starRenderer = null
    }

    if (this._planetRenderer) {  // 新增
      this._planetRenderer.dispose()
      this._planetRenderer = null
    }

    ctx.worldGroup.remove(this.group)
    super.setVisible(false)
  }
}
```

### 3.2 选中指示器迁移设计

#### 3.2.1 新增 SelectionLayer
**位置**：`web/src/rendering/layers/selection/selectionLayer.ts`
**职责**：统一处理所有实体类型的选中状态指示喵

**层特性**：
- **渲染顺序**：`RenderOrder.SELECTION` (4)
- **实体类型支持**：STAR、PLANET、SHIP
- **统一样式**：所有实体类型使用相同的绿色虚线圈样式喵

#### 3.2.2 新增 LayerSelectionRenderer
**位置**：`web/src/rendering/layers/selection/renderers/selectionRenderer.ts`
**职责**：绿色虚线圈选中指示器实现喵

**技术参数**：
- **颜色**：`0x4caf50`（材质绿，与游戏UI风格协调）
- **虚线样式**：
  - `dashSize: 10` - 虚线长度
  - `gapSize: 5` - 间隔长度
  - `scale: 1` - 缩放因子
- **材质**：`THREE.LineDashedMaterial`
- **几何体**：`THREE.BufferGeometry`构建的圆形线（半径1.0，分段数48）*注：`THREE.RingGeometry`不能与虚线材质配合使用喵

**关键逻辑**：
- **尺寸计算**：基于实体半径 + 40% 边距，最小像素尺寸10像素
- **位置同步**：使用 `ctx.getEntityWorldPosGU(entityId)` 确保与选框系统坐标同步
- **LOD处理**：根据缩放级别调整透明度和可见性
- **对象池**：复用现有的对象池机制

### 3.3 层管理器注册
**修改位置**：`web/src/rendering/worldRenderManager.ts`
**修改内容**：
```typescript
// 在 createWorldRenderManager 函数中
layerManager.registerLayer(new BackgroundLayer())
layerManager.registerLayer(new CelestialLayer())
layerManager.registerLayer(new EntityLayer())
layerManager.registerLayer(new SelectionLayer()) // 新增选择层

// 更新渲染顺序常量（如果尚未定义）
export const RenderOrder = {
  BACKGROUND: 0,
  CELESTIAL: 1,
  ENTITY: 2,
  EFFECT: 3,    // 预留：特效层
  SELECTION: 4, // 新增：选择层
  UI_OVERLAY: 5 // 预留：UI覆盖层
}
```

## 4. 技术实现细节

### 4.1 坐标同步机制
**关键保证**：所有渲染器必须使用统一的坐标获取接口，确保视觉一致性喵

```typescript
// 正确方式：使用上下文提供的接口
const entityPos = ctx.getEntityWorldPosGU(entityId)
// 而不是：直接从实体数据中读取（可能不同步）
```

### 4.2 虚线环实现技术
**Three.js 虚线环限制与解决方案**：
- **限制**：`THREE.RingGeometry` 不能直接与 `THREE.LineDashedMaterial` 配合使用喵
- **解决方案**：使用 `THREE.Line` + `THREE.BufferGeometry` 构建圆形虚线喵

**实现代码示例**：
```typescript
private createDashedRing(): THREE.Line {
  const radius = 1.0
  const segments = 48
  const points = []

  // 生成圆形点集
  for (let i = 0; i <= segments; i++) {
    const theta = (i / segments) * Math.PI * 2
    points.push(new THREE.Vector3(
      Math.cos(theta) * radius,
      Math.sin(theta) * radius,
      0
    ))
  }

  const geometry = new THREE.BufferGeometry().setFromPoints(points)
  const material = new THREE.LineDashedMaterial({
    color: 0x4caf50,
    dashSize: 10,
    gapSize: 5,
    scale: 1,
    linewidth: 2,
  })

  const line = new THREE.Line(geometry, material)
  line.computeLineDistances() // 必须调用以启用虚线
  return line
}
```

### 4.3 实体类型识别与处理
**统一处理逻辑**：
```typescript
private getEntitySize(entity: EntitySnapshot): number {
  switch (entity.entityType) {
    case 'STAR':
      const starDetails = entity.details as StarDetails
      return starDetails?.radiusGU ?? 0
    case 'PLANET':
      const planetDetails = entity.details as PlanetDetails
      return planetDetails?.radiusGU ?? 0
    case 'SHIP':
      // 舰船使用默认尺寸
      return DEFAULT_SHIP_SIZE
    default:
      return DEFAULT_SIZE
  }
}

**默认尺寸常量**：
- `DEFAULT_SHIP_SIZE: number = 20` - 舰船默认半径（世界单位）
- `DEFAULT_SIZE: number = 15` - 其他实体类型默认半径（世界单位）

### 4.4 性能优化策略
1. **对象池复用**：两个渲染器都继承现有的对象池机制，避免频繁创建/销毁Three.js对象喵
2. **增量更新**：仅更新可见且在视口内的实体选中状态喵
3. **LOD控制**：根据缩放级别动态调整选中环的透明度和可见性喵
4. **批量渲染**：相同材质的选中环尽可能批量处理，减少绘制调用喵

## 5. 实施计划

### 5.1 阶段分解

#### 阶段1：星球渲染迁移（预计1天）
1. 创建 `LayerPlanetRenderer` 并集成到 `CelestialLayer` 喵
2. 测试行星渲染功能（纹理、拖尾、LOD）喵
3. 验证渲染顺序和性能喵

#### 阶段2：选中指示器迁移（预计1天）
1. 创建 `SelectionLayer` 和 `LayerSelectionRenderer` 喵
2. 实现绿色虚线环样式喵
3. 测试所有实体类型的选中状态显示喵

#### 阶段3：集成测试与优化（预计0.5天）
1. 完整功能测试（选择、渲染、交互）喵
2. 性能分析与优化喵
3. 代码审查与文档更新喵

### 5.2 文件结构变更
```
web/src/rendering/
├── layers/
│   ├── celestial/           # 星体层（修改）
│   │   ├── celestialLayer.ts          ← 新增行星渲染器集成
│   │   ├── renderers/
│   │   │   ├── starRenderer.ts        # 现有
│   │   │   └── planetRenderer.ts      ← 新增
│   ├── entity/             # 实体层（不变）
│   ├── selection/          # 新建：选择层
│   │   ├── index.ts
│   │   ├── selectionLayer.ts          ← 新增
│   │   └── renderers/
│   │       └── selectionRenderer.ts   ← 新增
│   ├── background/         # 背景层（不变）
│   ├── baseLayer.ts        # 基础层（不变）
│   └── layerManager.ts     # 层管理器（修改注册）
└── worldRenderManager.ts   # 渲染管理器（修改层注册）
```

### 5.3 风险评估与缓解

#### 风险1：虚线环渲染性能问题
- **可能性**：中喵
- **影响**：低喵
- **缓解措施**：使用合理的分段数（48），限制同时显示的选中环数量喵

#### 风险2：坐标同步不一致
- **可能性**：低喵
- **影响**：高喵
- **缓解措施**：强制所有渲染器使用 `ctx.getEntityWorldPosGU` 接口，禁止直接读取实体坐标喵

#### 风险3：现有功能回归
- **可能性**：低喵
- **影响**：中喵
- **缓解措施**：保留原有子系统代码作为参考，逐步验证每个功能点喵

## 6. 验收标准

### 6.1 功能验收标准
1. ✅ **行星渲染恢复**：行星正常显示表面纹理和拖尾效果喵
2. ✅ **选中指示器恢复**：选中实体显示绿色虚线环喵
3. ✅ **实体类型支持**：STAR、PLANET、SHIP 都支持选中指示喵
4. ✅ **渲染顺序正确**：选中环在实体上方正确显示喵
5. ✅ **交互功能正常**：选择、取消选择等交互功能正常工作喵

### 6.2 性能验收标准
1. ✅ **帧率稳定**：新增渲染层后，帧率下降不超过5%喵
2. ✅ **内存可控**：新增对象池机制，内存使用无显著增加喵
3. ✅ **加载时间**：新增资源加载不影响现有加载流程喵

### 6.3 代码质量标准
1. ✅ **架构统一**：完全迁移到分层架构，无新旧架构混合喵
2. ✅ **代码复用**：最大化复用现有业务逻辑，仅重构适配层喵
3. ✅ **类型安全**：TypeScript编译无错误，类型定义完整喵
4. ✅ **注释完整**：新增类和接口有完整注释，遵循项目规范喵

## 7. 附录

### 7.1 依赖关系图
```mermaid
graph TD
    WRM[WorldRenderManager] --> LM[LayerManager]
    LM --> BL[BackgroundLayer]
    LM --> CL[CelestialLayer]
    LM --> EL[EntityLayer]
    LM --> SL[SelectionLayer]  ← 新增

    CL --> StarR[LayerStarRenderer]
    CL --> PlanetR[LayerPlanetRenderer] ← 新增

    SL --> SelectionR[LayerSelectionRenderer] ← 新增
```

### 7.2 关键技术点参考
1. **现有行星渲染器参考**：`web/src/rendering/subsystems/planetRenderer.ts`
2. **现有选中环参考**：`web/src/rendering/subsystems/selectionRenderer.ts`
3. **分层架构参考**：`web/src/rendering/layers/celestial/celestialLayer.ts`
4. **层管理器参考**：`web/src/rendering/layers/layerManager.ts`

---

**文档状态**：设计完成，等待用户审查喵。

**下一步**：用户审查通过后，将创建详细的实现计划喵。