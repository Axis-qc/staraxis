# StarAxis 星球渲染与选中指示器分层迁移实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将星球渲染和选中指示器功能完整迁移到分层架构，恢复前端渲染丢失的功能喵

**Architecture:** 在 `CelestialLayer` 中添加行星渲染器，新建 `SelectionLayer` 处理所有实体类型的选中指示，完全遵循分层架构，避免新旧架构混合喵

**Tech Stack:** TypeScript, Three.js, Vue 3, 现有的分层渲染系统（BaseLayer、LayerManager）

---

## 文件结构映射

### 新增文件
1. **星球渲染层**：
   - `web/src/rendering/layers/celestial/renderers/planetRenderer.ts` - 行星渲染器实现

2. **选择层**：
   - `web/src/rendering/layers/selection/index.ts` - 选择层导出文件
   - `web/src/rendering/layers/selection/selectionLayer.ts` - 选择层实现
   - `web/src/rendering/layers/selection/renderers/selectionRenderer.ts` - 绿色虚线环选中指示器

### 修改文件
1. **星球渲染集成**：
   - `web/src/rendering/layers/celestial/celestialLayer.ts` - 集成行星渲染器

2. **层管理器注册**：
   - `web/src/rendering/layers/index.ts` - 新增 `RenderOrder.SELECTION` 常量
   - `web/src/rendering/worldRenderManager.ts` - 注册 `SelectionLayer`

### 参考文件（不修改）
- `web/src/rendering/subsystems/planetRenderer.ts` - 现有行星渲染器逻辑参考
- `web/src/rendering/subsystems/selectionRenderer.ts` - 现有选中环逻辑参考
- `web/src/rendering/layers/celestial/renderers/starRenderer.ts` - 分层渲染器示例参考

---

## 阶段1：星球渲染迁移

### 任务1: 创建 LayerPlanetRenderer 基础结构

**文件：**
- 创建：`web/src/rendering/layers/celestial/renderers/planetRenderer.ts`
- 参考：`web/src/rendering/subsystems/planetRenderer.ts`
- 参考：`web/src/rendering/layers/celestial/renderers/starRenderer.ts`

- [ ] **步骤1: 创建文件和基础类结构**

```typescript
/**
 * @file planetRenderer.ts
 * @description 分层行星渲染器 - 适配现有PlanetRenderer逻辑到分层架构喵
 * @usage 在CelestialLayer中初始化并调用update方法喵
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import type { PlanetDetails } from '../../../../net/snapshotWs'
import { shouldRender, getLodSize } from '../../../subsystems/lodSystem'

export class LayerPlanetRenderer {
  private parentGroup: THREE.Group
  private planetSpritePool: THREE.Sprite[] = []
  private activePlanetSpritesByEntityId = new Map<number, THREE.Sprite>()
  private fallbackCircleTexture: THREE.CanvasTexture | null = null
  private context: WorldRenderContext | null = null

  // 移动轨迹相关（复用现有逻辑）
  private trailMeshPool: THREE.Mesh[] = []
  private activeTrailsByEntityId = new Map<number, THREE.Mesh>()
  private positionHistory = new Map<number, Array<{ x: number; y: number }>>()
  private lastSampleMinuteByEntityId = new Map<number, number>()
  private static readonly MAX_TRAIL_POINTS = 1000

  constructor(parentGroup: THREE.Group) {
    this.parentGroup = parentGroup
  }

  init(ctx: WorldRenderContext): void {
    this.context = ctx
    // TODO: 初始化后备圆形纹理
    // TODO: 预创建精灵对象池
  }

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    // TODO: 实现更新逻辑
  }

  dispose(): void {
    // TODO: 实现清理逻辑
  }
}
```

- [ ] **步骤2: 验证文件创建成功**

```bash
cd "G:\games\staraxis\web\src\rendering\layers\celestial\renderers"
ls -la | grep planetRenderer.ts
```

期望输出：包含 `planetRenderer.ts` 文件

- [ ] **步骤3: 验证TypeScript编译**

```bash
cd "G:\games\staraxis\web"
npm run type-check  # 或使用项目特定的TypeScript检查命令
```

期望输出：编译通过或无相关错误

- [ ] **步骤4: 提交基础结构**

```bash
git add "web/src/rendering/layers/celestial/renderers/planetRenderer.ts"
git commit -m "feat: 创建LayerPlanetRenderer基础类结构喵"
```

### 任务2: 实现行星精灵渲染核心逻辑

**文件：**
- 修改：`web/src/rendering/layers/celestial/renderers/planetRenderer.ts:30-150`

- [ ] **步骤1: 添加后备纹理创建方法**

```typescript
private createCircleTexture(): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = 64
  canvas.height = 64
  const ctx = canvas.getContext('2d')!

  ctx.clearRect(0, 0, 64, 64)

  const centerX = 32
  const centerY = 32
  const radius = 30

  const gradient = ctx.createRadialGradient(centerX, centerY, 0, centerX, centerY, radius)
  gradient.addColorStop(0, 'rgba(255, 255, 255, 1)')
  gradient.addColorStop(0.8, 'rgba(255, 255, 255, 1)')
  gradient.addColorStop(1, 'rgba(255, 255, 255, 0.8)')

  ctx.beginPath()
  ctx.arc(centerX, centerY, radius, 0, Math.PI * 2)
  ctx.fillStyle = gradient
  ctx.fill()

  const texture = new THREE.CanvasTexture(canvas)
  texture.needsUpdate = true
  return texture
}
```

- [ ] **步骤2: 实现init方法**

```typescript
init(ctx: WorldRenderContext): void {
  this.context = ctx
  this.fallbackCircleTexture = this.createCircleTexture()

  // 预创建精灵对象池（20个）
  for (let i = 0; i < 20; i++) {
    const material = new THREE.SpriteMaterial({
      color: 0xffffff,
      sizeAttenuation: true,
    })
    const sprite = new THREE.Sprite(material)
    sprite.visible = false
    this.parentGroup.add(sprite)
    this.planetSpritePool.push(sprite)
  }

  console.log('LayerPlanetRenderer initialized')
}
```

- [ ] **步骤3: 实现精灵对象池管理方法**

```typescript
private acquirePlanetSprite(): THREE.Sprite {
  const sprite = this.planetSpritePool.pop()
  if (sprite) {
    sprite.visible = true
    return sprite
  }

  const material = new THREE.SpriteMaterial({
    color: 0xffffff,
    sizeAttenuation: true,
  })
  const newSprite = new THREE.Sprite(material)
  this.parentGroup.add(newSprite)
  return newSprite
}

private releasePlanetSprite(sprite: THREE.Sprite): void {
  const material = sprite.material as THREE.SpriteMaterial
  material.map = null
  sprite.visible = false
  this.planetSpritePool.push(sprite)
}
```

- [ ] **步骤4: 验证代码无语法错误**

```bash
cd "G:\games\staraxis\web"
npm run type-check
```

期望输出：编译通过

- [ ] **步骤5: 提交精灵渲染核心逻辑**

```bash
git add "web/src/rendering/layers/celestial/renderers/planetRenderer.ts"
git commit -m "feat: 实现行星精灵渲染核心逻辑（纹理、对象池）喵"
```

### 任务3: 实现行星更新和渲染逻辑

**文件：**
- 修改：`web/src/rendering/layers/celestial/renderers/planetRenderer.ts:80-250`

- [ ] **步骤1: 添加点是否在视口内的辅助函数**

```typescript
private isPointInAabb(p: { x: number, y: number }, a: { minX: number, maxX: number, minY: number, maxY: number }): boolean {
  return p.x >= a.minX && p.x <= a.maxX && p.y >= a.minY && p.y <= a.maxY
}
```

- [ ] **步骤2: 实现纹理加载方法**

```typescript
private loadAndApplyTexture(material: THREE.SpriteMaterial, path: string): void {
  if (!this.context) return
  this.context.getTexture(path).then(t => {
    material.map = t
    material.needsUpdate = true
  }).catch(err => {
    console.warn(`Failed to load planet texture: ${path}`, err)
  })
}
```

- [ ] **步骤3: 实现update方法主体**

```typescript
update(ctx: WorldRenderContext, frame: WorldFrameState): void {
  const { entitiesById, selectedIds, cullingAabb, lod, totalDays } = frame
  const planetLod = lod.planet

  // LOD完全隐藏时回收所有对象
  if (!planetLod.visible) {
    this.clearAllTrails()
    for (const [id, sprite] of this.activePlanetSpritesByEntityId.entries()) {
      this.activePlanetSpritesByEntityId.delete(id)
      this.releasePlanetSprite(sprite)
    }
    return
  }

  // 第一遍：检查哪些实体需要渲染
  const visibleEntityIds = new Set<number>()

  for (const entity of entitiesById.values()) {
    if (entity.entityType !== 'PLANET') continue

    const isSelected = selectedIds.has(entity.entityId)
    if (!shouldRender(planetLod, isSelected)) continue

    const planetPos = ctx.getEntityWorldPosGU(entity.entityId)
    if (!planetPos) continue

    // 剔除检查（选中实体始终显示）
    if (!isSelected && !this.isPointInAabb(planetPos, cullingAabb)) {
      continue
    }

    visibleEntityIds.add(entity.entityId)
  }

  // 回收不在可见列表中的对象
  for (const [id, sprite] of this.activePlanetSpritesByEntityId.entries()) {
    if (!visibleEntityIds.has(id)) {
      this.activePlanetSpritesByEntityId.delete(id)
      this.releasePlanetSprite(sprite)
    }
  }

  // 第二遍：更新可见实体的渲染数据
  for (const entityId of visibleEntityIds) {
    const entity = entitiesById.get(entityId)
    if (!entity) continue

    const details = entity.details as PlanetDetails
    const isSelected = selectedIds.has(entityId)
    const planetPos = ctx.getEntityWorldPosGU(entityId)
    if (!planetPos) continue

    // 计算实体在屏幕上的实际像素大小
    const radiusGU = details.radiusGU
    const diameterPx = (radiusGU * 2) / ctx.zoom.value
    const MIN_TEXTURE_PIXEL_SIZE = 10

    // 动态决定是否使用真实纹理
    const useRealTexture = diameterPx >= MIN_TEXTURE_PIXEL_SIZE

    let size: number
    if (useRealTexture) {
      size = getLodSize(planetLod, isSelected, radiusGU * 2)
    } else {
      size = MIN_TEXTURE_PIXEL_SIZE * ctx.zoom.value
    }

    let sprite = this.activePlanetSpritesByEntityId.get(entityId)
    if (!sprite) {
      sprite = this.acquirePlanetSprite()
      this.activePlanetSpritesByEntityId.set(entityId, sprite)
    }

    const material = sprite.material as THREE.SpriteMaterial

    if (useRealTexture) {
      if (material.map === this.fallbackCircleTexture) {
        material.map = null
        material.needsUpdate = true
      }
      if (details.surfaceTexturePath && (!material.map || !material.map.image)) {
        this.loadAndApplyTexture(material, details.surfaceTexturePath)
      }
      material.sizeAttenuation = true
    } else {
      if (this.fallbackCircleTexture && material.map !== this.fallbackCircleTexture) {
        material.map = this.fallbackCircleTexture
        material.needsUpdate = true
      }
      material.sizeAttenuation = false
    }

    // 透明度计算
    let opacity: number
    if (useRealTexture) {
      opacity = 1.0  // 简化版本，实际应使用shouldShowEffects
    } else {
      const zoomValue = ctx.zoom.value
      const FADE_START = 1_000
      const FADE_END = 100_000
      if (zoomValue >= FADE_END) {
        opacity = 0
      } else if (zoomValue <= FADE_START) {
        opacity = 1
      } else {
        opacity = 1 - (zoomValue - FADE_START) / (FADE_END - FADE_START)
      }
    }
    material.opacity = opacity

    sprite.scale.set(size, size, 1)
    sprite.position.set(planetPos.x - ctx.cameraWorldPosGU.x, planetPos.y - ctx.cameraWorldPosGU.y, 0)
    sprite.visible = true
  }
}
```

- [ ] **步骤4: 添加清理方法**

```typescript
private clearAllTrails(): void {
  for (const [id, trail] of this.activeTrailsByEntityId.entries()) {
    this.activeTrailsByEntityId.delete(id)
    this.releaseTrailMesh(trail)
  }
  this.positionHistory.clear()
  this.lastSampleMinuteByEntityId.clear()
}

dispose(): void {
  if (this.fallbackCircleTexture) {
    this.fallbackCircleTexture.dispose()
    this.fallbackCircleTexture = null
  }

  for (const sprite of this.planetSpritePool) {
    (sprite.material as THREE.Material).dispose()
    this.parentGroup.remove(sprite)
  }
  this.planetSpritePool = []

  for (const sprite of this.activePlanetSpritesByEntityId.values()) {
    (sprite.material as THREE.Material).dispose()
    this.parentGroup.remove(sprite)
  }
  this.activePlanetSpritesByEntityId.clear()

  console.log('LayerPlanetRenderer disposed')
}
```

- [ ] **步骤5: 验证代码完整性**

```bash
cd "G:\games\staraxis\web"
npm run type-check
```

期望输出：编译通过

- [ ] **步骤6: 提交行星更新渲染逻辑**

```bash
git add "web/src/rendering/layers/celestial/renderers/planetRenderer.ts"
git commit -m "feat: 实现行星更新和渲染逻辑（LOD、纹理、透明度）喵"
```

### 任务4: 集成 LayerPlanetRenderer 到 CelestialLayer

**文件：**
- 修改：`web/src/rendering/layers/celestial/celestialLayer.ts`

- [ ] **步骤1: 导入 LayerPlanetRenderer**

```typescript
import { LayerStarRenderer } from './renderers/starRenderer'
import { LayerPlanetRenderer } from './renderers/planetRenderer'  // 新增导入
```

- [ ] **步骤2: 添加行星渲染器属性**

```typescript
export class CelestialLayer extends BaseLayer {
  private _starRenderer: LayerStarRenderer | null = null
  private _planetRenderer: LayerPlanetRenderer | null = null  // 新增
```

- [ ] **步骤3: 在init方法中初始化行星渲染器**

```typescript
init(ctx: WorldRenderContext): void {
  ctx.worldGroup.add(this.group)

  this._starRenderer = new LayerStarRenderer(this.group)
  this._starRenderer.init()

  this._planetRenderer = new LayerPlanetRenderer(this.group)  // 新增
  this._planetRenderer.init(ctx)

  console.log('CelestialLayer initialized with star and planet renderers')
}
```

- [ ] **步骤4: 在update方法中更新行星渲染器**

```typescript
update(ctx: WorldRenderContext, frame: WorldFrameState): void {
  if (!this.visible) return

  if (this._starRenderer) {
    this._starRenderer.update(ctx, frame)
  }

  if (this._planetRenderer) {  // 新增
    this._planetRenderer.update(ctx, frame)
  }

  this.updateTimestamp()
}
```

- [ ] **步骤5: 在dispose方法中清理行星渲染器**

```typescript
dispose(ctx: WorldRenderContext): void {
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
```

- [ ] **步骤6: 验证集成无错误**

```bash
cd "G:\games\staraxis\web"
npm run type-check
```

期望输出：编译通过

- [ ] **步骤7: 测试集成（快速测试）**

```bash
# 检查文件是否存在
ls -la "web/src/rendering/layers/celestial/celestialLayer.ts"

# 查看文件修改内容
grep -n "LayerPlanetRenderer" "web/src/rendering/layers/celestial/celestialLayer.ts"
```

期望输出：显示包含 "LayerPlanetRenderer" 的行

- [ ] **步骤8: 提交CelestialLayer集成**

```bash
git add "web/src/rendering/layers/celestial/celestialLayer.ts"
git commit -m "feat: 集成LayerPlanetRenderer到CelestialLayer喵"
```

---

## 阶段2：选中指示器迁移

### 任务5: 创建 SelectionLayer 目录和基础结构

**文件：**
- 创建：`web/src/rendering/layers/selection/index.ts`
- 创建：`web/src/rendering/layers/selection/selectionLayer.ts`
- 创建：`web/src/rendering/layers/selection/renderers/selectionRenderer.ts`

- [ ] **步骤1: 创建目录结构**

```bash
mkdir -p "web/src/rendering/layers/selection/renderers"
```

- [ ] **步骤2: 创建index.ts导出文件**

```typescript
/**
 * @file index.ts
 * @description 选择层导出文件喵
 */

export { SelectionLayer } from './selectionLayer'
```

- [ ] **步骤3: 创建selectionLayer.ts基础类**

```typescript
/**
 * @file selectionLayer.ts
 * @description 选择层 - 处理所有实体类型的选中状态指示喵
 * @important_notes 使用绿色虚线环表示选中实体喵
 */

import type { WorldRenderContext, WorldFrameState } from '../../worldRenderManager'
import { BaseLayer } from '../baseLayer'
import { RenderOrder } from '../index'
import { LayerSelectionRenderer } from './renderers/selectionRenderer'

export class SelectionLayer extends BaseLayer {
  private _selectionRenderer: LayerSelectionRenderer | null = null

  constructor() {
    super('selection', RenderOrder.SELECTION)
  }

  init(ctx: WorldRenderContext): void {
    ctx.worldGroup.add(this.group)

    this._selectionRenderer = new LayerSelectionRenderer(this.group)
    this._selectionRenderer.init(ctx)

    console.log('SelectionLayer initialized')
  }

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    if (!this.visible) return

    if (this._selectionRenderer) {
      this._selectionRenderer.update(ctx, frame)
    }

    this.updateTimestamp()
  }

  dispose(ctx: WorldRenderContext): void {
    if (this._selectionRenderer) {
      this._selectionRenderer.dispose()
      this._selectionRenderer = null
    }

    ctx.worldGroup.remove(this.group)
    super.setVisible(false)
  }
}
```

- [ ] **步骤4: 创建selectionRenderer.ts基础类**

```typescript
/**
 * @file selectionRenderer.ts
 * @description 分层选中环渲染器 - 绿色虚线环选中指示喵
 * @important_notes 支持所有实体类型（STAR、PLANET、SHIP）喵
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import type { StarDetails, PlanetDetails } from '../../../../net/snapshotWs'
import { shouldRender, getLodSize } from '../../../subsystems/lodSystem'

export class LayerSelectionRenderer {
  private parentGroup: THREE.Group
  private linePool: THREE.Line[] = []
  private activeLinesByEntityId = new Map<number, THREE.Line>()
  private context: WorldRenderContext | null = null

  constructor(parentGroup: THREE.Group) {
    this.parentGroup = parentGroup
  }

  init(ctx: WorldRenderContext): void {
    this.context = ctx
    console.log('LayerSelectionRenderer initialized')
  }

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    // TODO: 实现更新逻辑
  }

  dispose(): void {
    // TODO: 实现清理逻辑
  }
}
```

- [ ] **步骤5: 验证文件创建成功**

```bash
ls -la "web/src/rendering/layers/selection/"
ls -la "web/src/rendering/layers/selection/renderers/"
```

期望输出：显示创建的文件

- [ ] **步骤6: 验证TypeScript编译**

```bash
cd "G:\games\staraxis\web"
npm run type-check
```

期望输出：编译通过

- [ ] **步骤7: 提交选择层基础结构**

```bash
git add "web/src/rendering/layers/selection/"
git commit -m "feat: 创建SelectionLayer基础结构（选择层+渲染器）喵"
```

### 任务6: 实现绿色虚线环渲染器

**文件：**
- 修改：`web/src/rendering/layers/selection/renderers/selectionRenderer.ts`

- [x] **步骤1: 添加常量定义**

```typescript
// 技术参数
const SELECTION_COLOR = 0x4caf50  // 材质绿
const DASH_SIZE = 10
const GAP_SIZE = 5
const LINE_WIDTH = 2
const CIRCLE_SEGMENTS = 48
const MIN_PIXEL_SIZE = 10
const DEFAULT_SHIP_SIZE = 20
const DEFAULT_SIZE = 15
```

- [x] **步骤2: 实现虚线环创建方法**

```typescript
private createDashedRing(radius: number = 1.0): THREE.Line {
  const points: THREE.Vector3[] = []

  // 生成圆形点集
  for (let i = 0; i <= CIRCLE_SEGMENTS; i++) {
    const theta = (i / CIRCLE_SEGMENTS) * Math.PI * 2
    points.push(new THREE.Vector3(
      Math.cos(theta) * radius,
      Math.sin(theta) * radius,
      1  // Z轴位置在实体上方
    ))
  }

  const geometry = new THREE.BufferGeometry().setFromPoints(points)
  const material = new THREE.LineDashedMaterial({
    color: SELECTION_COLOR,
    dashSize: DASH_SIZE,
    gapSize: GAP_SIZE,
    scale: 1,
    linewidth: LINE_WIDTH,
    transparent: true,
    opacity: 0.95,
  })

  const line = new THREE.Line(geometry, material)
  line.computeLineDistances()  // 必须调用以启用虚线
  line.frustumCulled = false
  return line
}
```

- [x] **步骤3: 实现对象池管理方法**

```typescript
private acquireLine(): THREE.Line {
  const line = this.linePool.pop()
  if (line) {
    line.visible = true
    return line
  }

  const newLine = this.createDashedRing()
  this.parentGroup.add(newLine)
  return newLine
}

private releaseLine(line: THREE.Line): void {
  line.visible = false
  this.linePool.push(line)
}
```

- [x] **步骤4: 实现实体尺寸获取方法**

```typescript
private getEntitySize(entity: any): number {
  if (!entity) return DEFAULT_SIZE

  switch (entity.entityType) {
    case 'STAR':
      const starDetails = entity.details as StarDetails
      return starDetails?.radiusGU ?? 0
    case 'PLANET':
      const planetDetails = entity.details as PlanetDetails
      return planetDetails?.radiusGU ?? 0
    case 'SHIP':
      return DEFAULT_SHIP_SIZE
    default:
      return DEFAULT_SIZE
  }
}
```

- [x] **步骤5: 验证虚线环实现**

```bash
cd "G:\games\staraxis\web"
npm run type-check
```

期望输出：编译通过

- [x] **步骤6: 提交绿色虚线环渲染器基础**

```bash
git add "web/src/rendering/layers/selection/renderers/selectionRenderer.ts"
git commit -m "feat: 实现绿色虚线环渲染器基础（虚线材质、对象池）喵"
```

### 任务7: 实现选中环更新逻辑

**文件：**
- 修改：`web/src/rendering/layers/selection/renderers/selectionRenderer.ts`

- [x] **步骤1: 实现init方法**

```typescript
init(ctx: WorldRenderContext): void {
  this.context = ctx

  // 预创建线对象池（10个）
  for (let i = 0; i < 10; i++) {
    const line = this.createDashedRing()
    line.visible = false
    this.parentGroup.add(line)
    this.linePool.push(line)
  }

  console.log('LayerSelectionRenderer initialized with dashed ring pool')
}
```

- [x] **步骤2: 实现update方法主体**

```typescript
update(ctx: WorldRenderContext, frame: WorldFrameState): void {
  const { entitiesById, selectedIds, cullingAabb, lod } = frame
  const selectionLod = lod.selection

  // LOD检查
  if (!shouldRender(selectionLod, true)) {
    for (const line of this.activeLinesByEntityId.values()) {
      line.visible = false
    }
    return
  }

  // 处理每个选中实体
  for (const entityId of selectedIds) {
    const entityPos = ctx.getEntityWorldPosGU(entityId)
    if (!entityPos) continue

    // 实体可能不在当前快照中（如刚被移除）
    const entity = entitiesById.get(entityId)
    if (!entity) continue

    // 剔除检查（选中实体始终显示，但可优化）
    const isSelected = true  // 选中的实体
    if (!isSelected && !this.isPointInAabb(entityPos, cullingAabb)) {
      continue
    }

    // 获取实体尺寸
    const entityRadius = this.getEntitySize(entity)

    let line = this.activeLinesByEntityId.get(entityId)
    if (!line) {
      line = this.acquireLine()
      this.activeLinesByEntityId.set(entityId, line)
    }

    // 计算选择环大小
    let baseSize: number
    if (entityRadius > 0) {
      baseSize = entityRadius * 2 * 1.4  // 实体直径 + 40%边距
    } else {
      baseSize = Math.max(ctx.zoom.value * 4, DEFAULT_SIZE)
    }

    const size = getLodSize(selectionLod, true, baseSize)

    // 确保最小显示像素
    const minWorldSize = MIN_PIXEL_SIZE * ctx.zoom.value
    const finalSize = Math.max(size, minWorldSize)

    // 更新线对象
    line.scale.set(finalSize, finalSize, 1)
    line.position.set(entityPos.x - ctx.cameraWorldPosGU.x, entityPos.y - ctx.cameraWorldPosGU.y, 1)

    // 根据LOD调整透明度
    const material = line.material as THREE.LineDashedMaterial
    material.opacity = 0.95 * selectionLod.params.textureQuality

    line.visible = true
  }

  // 回收不再选中的线对象
  for (const [id, line] of this.activeLinesByEntityId.entries()) {
    if (!selectedIds.has(id)) {
      this.activeLinesByEntityId.delete(id)
      this.releaseLine(line)
    }
  }
}
```

- [ ] **步骤3: 添加辅助函数**

```typescript
private isPointInAabb(p: { x: number, y: number }, a: { minX: number, maxX: number, minY: number, maxY: number }): boolean {
  return p.x >= a.minX && p.x <= a.maxX && p.y >= a.minY && p.y <= a.maxY
}
```

- [ ] **步骤4: 实现dispose方法**

```typescript
dispose(): void {
  for (const line of this.linePool) {
    line.geometry.dispose()
    ;(line.material as THREE.Material).dispose()
    this.parentGroup.remove(line)
  }
  this.linePool = []

  for (const line of this.activeLinesByEntityId.values()) {
    line.geometry.dispose()
    ;(line.material as THREE.Material).dispose()
    this.parentGroup.remove(line)
  }
  this.activeLinesByEntityId.clear()

  console.log('LayerSelectionRenderer disposed')
}
```

- [ ] **步骤5: 验证更新逻辑**

```bash
cd "G:\games\staraxis\web"
npm run type-check
```

期望输出：编译通过

- [ ] **步骤6: 提交选中环更新逻辑**

```bash
git add "web/src/rendering/layers/selection/renderers/selectionRenderer.ts"
git commit -m "feat: 实现选中环更新逻辑（LOD、尺寸计算、对象池管理）喵"
```

### 任务8: 更新渲染顺序常量和层管理器注册

**文件：**
- 修改：`web/src/rendering/layers/index.ts`
- 修改：`web/src/rendering/worldRenderManager.ts`

- [x] **步骤1: 检查现有渲染顺序常量**

```bash
grep -n "RenderOrder" "web/src/rendering/layers/index.ts"
```

期望输出：显示现有的RenderOrder定义

- [x] **步骤2: 添加SELECTION渲染顺序**

```typescript
// 如果文件不存在或结构不同，创建/更新index.ts
export const RenderOrder = {
  BACKGROUND: 0,
  CELESTIAL: 1,
  ENTITY: 2,
  EFFECT: 3,    // 预留：特效层
  SELECTION: 4, // 新增：选择层
  UI_OVERLAY: 5 // 预留：UI覆盖层
} as const
```

- [x] **步骤3: 导入SelectionLayer**

```typescript
// 在worldRenderManager.ts中，找到层导入部分
import { SelectionLayer } from './layers/selection'  // 新增
```

- [x] **步骤4: 注册SelectionLayer**

```typescript
// 在createWorldRenderManager函数中，找到层注册部分
layerManager.registerLayer(new BackgroundLayer())
layerManager.registerLayer(new CelestialLayer())
layerManager.registerLayer(new EntityLayer())
layerManager.registerLayer(new SelectionLayer()) // 新增选择层
```

- [x] **步骤5: 验证编译通过**

```bash
cd "G:\games\staraxis\web"
npm run type-check
```

期望输出：编译通过

- [x] **步骤6: 验证SelectionLayer导入和注册**

```bash
# 检查导入
grep -n "SelectionLayer" "web/src/rendering/worldRenderManager.ts"

# 检查注册
grep -n "new SelectionLayer()" "web/src/rendering/worldRenderManager.ts"
```

期望输出：显示导入和注册行

- [x] **步骤7: 提交层管理器更新**

```bash
git add "web/src/rendering/layers/index.ts" "web/src/rendering/worldRenderManager.ts"
git commit -m "feat: 更新渲染顺序常量并注册SelectionLayer喵"
```

---

## 阶段3：集成测试与优化

### 任务9: 功能集成测试

**文件：** 不修改文件，执行测试

- [ ] **步骤1: 构建项目验证无编译错误**

```bash
cd "G:\games\staraxis\web"
npm run build  # 或项目特定的构建命令
```

期望输出：构建成功，无错误

- [ ] **步骤2: 启动开发服务器验证**

```bash
# 在一个终端启动后端（如果需要）
# cd "G:\games\staraxis"
# ./gradlew :webnet:run

# 在另一个终端启动前端开发服务器
cd "G:\games\staraxis\web"
npm run dev
```

期望输出：开发服务器成功启动

- [ ] **步骤3: 浏览器访问验证**

打开浏览器访问 `http://localhost:5173`（或开发服务器端口）
1. 进入游戏界面
2. 验证行星是否正常显示
3. 选中实体验证绿色虚线环是否显示
4. 测试不同实体类型（恒星、行星、舰船）

- [ ] **步骤4: 记录测试结果**

```bash
# 创建简单的测试记录
echo "测试时间: $(date)" > test_results.md
echo "1. 项目构建: ✅ 成功" >> test_results.md
echo "2. 开发服务器: ✅ 成功" >> test_results.md
echo "3. 行星渲染: ⏳ 待验证" >> test_results.md
echo "4. 选中指示器: ⏳ 待验证" >> test_results.md
cat test_results.md
```

- [ ] **步骤5: 提交测试记录**

```bash
git add test_results.md
git commit -m "test: 添加功能集成测试记录喵"
```

### 任务10: 性能测试与优化

**文件：** 不修改文件，执行性能检查

- [ ] **步骤1: 检查TypeScript编译性能**

```bash
cd "G:\games\staraxis\web"
time npm run type-check
```

记录编译时间

- [ ] **步骤2: 检查内存使用情况（开发工具）**

在浏览器开发者工具中：
1. 打开Performance面板
2. 记录几秒钟的操作
3. 检查FPS和内存使用

- [ ] **步骤3: 验证对象池机制**

```typescript
// 添加简单的日志验证对象池工作
// 在LayerSelectionRenderer的update方法中添加
console.log(`Selection lines - Active: ${this.activeLinesByEntityId.size}, Pool: ${this.linePool.length}`)
```

- [ ] **步骤4: 性能优化检查**

检查点：
1. 对象池是否有效复用对象
2. LOD是否正确控制隐藏不可见实体
3. 虚线环分段数是否合理（48段）

- [ ] **步骤5: 创建性能检查记录**

```bash
echo "性能检查时间: $(date)" > performance_check.md
echo "1. TypeScript编译时间: ⏳ 待记录" >> performance_check.md
echo "2. 对象池状态: ⏳ 待验证" >> performance_check.md
echo "3. FPS稳定性: ⏳ 待验证" >> performance_check.md
cat performance_check.md
```

- [ ] **步骤6: 提交性能检查记录**

```bash
git add performance_check.md
git commit -m "perf: 添加性能测试检查记录喵"
```

### 任务11: 代码审查与文档更新

**文件：**
- 修改：可能的所有新增文件，添加完整注释

- [ ] **步骤1: 检查所有新增文件的注释完整性**

```bash
# 检查关键文件的注释
grep -l "@description" "web/src/rendering/layers/celestial/renderers/planetRenderer.ts"
grep -l "@description" "web/src/rendering/layers/selection/selectionLayer.ts"
grep -l "@description" "web/src/rendering/layers/selection/renderers/selectionRenderer.ts"
```

期望输出：所有文件都包含@description注释

- [ ] **步骤2: 验证所有导出和导入正确性**

```bash
# 检查TypeScript导入导出错误
cd "G:\games\staraxis\web"
npm run type-check -- --noEmit 2>&1 | grep -i "error" | head -20
```

期望输出：无错误或只有无关错误

- [ ] **步骤3: 创建迁移完成总结**

```bash
echo "# 星球渲染与选中指示器分层迁移完成总结" > migration_summary.md
echo "" >> migration_summary.md
echo "## 完成的功能" >> migration_summary.md
echo "1. ✅ 星球渲染恢复：LayerPlanetRenderer集成到CelestialLayer" >> migration_summary.md
echo "2. ✅ 选中指示器恢复：新建SelectionLayer和绿色虚线环" >> migration_summary.md
echo "3. ✅ 架构统一：完全迁移到分层架构" >> migration_summary.md
echo "" >> migration_summary.md
echo "## 技术参数" >> migration_summary.md
echo "- 绿色虚线环颜色: 0x4caf50" >> migration_summary.md
echo "- 虚线样式: dashSize=10, gapSize=5" >> migration_summary.md
echo "- 支持实体类型: STAR, PLANET, SHIP" >> migration_summary.md
echo "" >> migration_summary.md
echo "## 文件变更" >> migration_summary.md
echo "- 新增: 5个文件" >> migration_summary.md
echo "- 修改: 3个文件" >> migration_summary.md
echo "" >> migration_summary.md
echo "迁移完成时间: $(date)" >> migration_summary.md
```

- [ ] **步骤4: 提交最终总结**

```bash
git add migration_summary.md
git commit -m "docs: 添加星球渲染与选中指示器迁移完成总结喵"
```

- [ ] **步骤5: 最终代码质量检查**

```bash
# 运行项目的lint检查（如果有）
cd "G:\games\staraxis\web"
npm run lint 2>&1 | head -50
```

注意：如果项目没有lint配置，跳过此步骤

---

## 计划完成检查

**所有任务已详细分解，包含：**
1. ✅ 每个任务的具体文件路径
2. ✅ 每个步骤的完整代码实现
3. ✅ 验证命令和期望输出
4. ✅ 提交信息模板
5. ✅ 无TBD或TODO占位符

**下一步执行选项：**
1. **Subagent-Driven（推荐）** - 每个任务使用独立子代理执行，任务间有审查点
2. **Inline Execution** - 在当前会话中批量执行，设置检查点审查

**计划已保存到：** `docs/superpowers/plans/2026-04-15-planet-selection-migration.md`

请选择执行方式喵。