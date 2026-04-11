# 前端渲染分层架构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现StarAxis前端渲染分层架构，将现有混合渲染系统重构为职责分离的模块化分层架构，确保正确的渲染顺序和清晰的代码组织喵。

**Architecture:** 采用方案2：职责分离的模块化分层架构，创建BackgroundLayer、CelestialLayer、EntityLayer三层结构，每层实现RenderLayer接口，由LayerManager统一管理喵。WorldRenderManager作为总协调器，保持现有API兼容性喵。

**Tech Stack:** TypeScript, Three.js, Vue 3, Vite

---

## 文件结构规划

### 新创建文件：
```
src/rendering/layers/
├── index.ts                          # 层接口导出
├── baseLayer.ts                      # 基础层抽象类
├── layerManager.ts                   # 层管理器
├── background/                       # 背景层
│   ├── index.ts
│   ├── backgroundLayer.ts
│   └── renderers/
│       ├── starfieldRenderer.ts
│       └── nebulaRenderer.ts
├── celestial/                        # 星体层
│   ├── index.ts
│   ├── celestialLayer.ts
│   └── renderers/
│       ├── starRenderer.ts          # 重构现有
│       ├── planetRenderer.ts        # 重构现有
│       └── celestialEffectRenderer.ts
└── entity/                          # 实体层
    ├── index.ts
    ├── entityLayer.ts
    └── renderers/
        ├── shipRenderer.ts          # 重构现有
        ├── stationRenderer.ts
        ├── weaponEffectRenderer.ts
        └── selectionRenderer.ts     # 整合现有
```

### 主要修改文件：
```
src/rendering/worldRenderManager.ts          # 改造为主协调器
src/rendering/systems/cameraSystem.ts        # 添加分层Group创建
src/rendering/subsystems/worldRenderSubsystem.ts # 更新类型引用
```

---

## 阶段1：基础架构搭建

### Task 1: 创建层接口定义

**Files:**
- Create: `src/rendering/layers/index.ts`

- [x] **Step 1: 创建文件并定义RenderOrder常量**

```typescript
/**
 * @file index.ts
 *
 * @description
 * 渲染层核心接口定义和常量导出喵。
 *
 * @important_notes
 * - 所有渲染层必须实现RenderLayer接口喵。
 * - 渲染顺序值越小越先渲染喵。
 * - 通过Three.js的renderOrder属性实现喵。
 */

import type * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'

// 渲染顺序值（越小越先渲染）
export const RenderOrder = {
    BACKGROUND: 0,    // 背景层：星空、星云等
    CELESTIAL: 1,     // 星体层：恒星、行星等
    ENTITY: 2,        // 实体层：舰船、建筑、空间站
    EFFECT: 3,        // 实体特效：武器光束、爆炸等
    SELECTION: 4,     // 选择框：实体选择标记
    UI_OVERLAY: 5,    // Three.js UI覆盖层
} as const

export type RenderOrder = typeof RenderOrder[keyof typeof RenderOrder]

/**
 * 层性能统计信息
 */
export interface LayerStats {
    visibleObjects: number
    totalObjects: number
    memoryUsageMB: number
    lastUpdateTimeMs: number
}

/**
 * 渲染层接口定义
 * 所有渲染层必须实现此接口喵。
 */
export interface RenderLayer {
    readonly name: string
    readonly renderOrder: number
    readonly group: THREE.Group

    // 生命周期方法
    init(ctx: WorldRenderContext): Promise<void>
    update(ctx: WorldRenderContext, frame: WorldFrameState): void
    dispose(ctx: WorldRenderContext): void

    // 层控制方法
    setVisible(visible: boolean): void
    isVisible(): boolean
    setQuality(quality: number): void  // 0.0-1.0，控制渲染质量
    getStats(): LayerStats             // 获取层性能统计
}

/**
 * 层管理器接口
 */
export interface LayerManager {
    readonly layers: Map<string, RenderLayer>

    // 层管理
    registerLayer(layer: RenderLayer): void
    unregisterLayer(name: string): void
    getLayer(name: string): RenderLayer | null

    // 批量操作
    updateAll(ctx: WorldRenderContext, frame: WorldFrameState): void
    setAllVisible(visible: boolean): void
    disposeAll(ctx: WorldRenderContext): void
}
```

- [ ] **Step 2: 编译检查**

```bash
cd web
npm run type-check
```
Expected: 无类型错误

- [x] **Step 3: 提交**

```bash
git add src/rendering/layers/index.ts
git commit -m "feat: 创建渲染层接口定义和常量"
```

### Task 2: 实现基础层抽象类

**Files:**
- Create: `src/rendering/layers/baseLayer.ts`

- [x] **Step 1: 创建基础层抽象类**

```typescript
/**
 * @file baseLayer.ts
 *
 * @description
 * 基础渲染层抽象类，提供RenderLayer接口的默认实现喵。
 *
 * @usage
 * - 具体层继承此类并实现抽象方法喵。
 * - 自动处理可见性、Group创建等通用逻辑喵。
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { RenderLayer, LayerStats, RenderOrder } from './index'

export abstract class BaseLayer implements RenderLayer {
    readonly name: string
    readonly renderOrder: number
    readonly group: THREE.Group

    protected visible: boolean = true
    protected quality: number = 1.0
    protected lastUpdateTime: number = 0

    constructor(name: string, renderOrder: RenderOrder) {
        this.name = name
        this.renderOrder = renderOrder
        this.group = new THREE.Group()
        this.group.renderOrder = renderOrder
        this.group.frustumCulled = false
    }

    // 抽象方法 - 必须由子类实现
    abstract init(ctx: WorldRenderContext): Promise<void>
    abstract update(ctx: WorldRenderContext, frame: WorldFrameState): void
    abstract dispose(ctx: WorldRenderContext): void

    // 默认实现的方法
    setVisible(visible: boolean): void {
        this.visible = visible
        this.group.visible = visible
    }

    isVisible(): boolean {
        return this.visible
    }

    setQuality(quality: number): void {
        this.quality = Math.max(0, Math.min(1, quality))
    }

    getStats(): LayerStats {
        return {
            visibleObjects: this.countVisibleObjects(),
            totalObjects: this.group.children.length,
            memoryUsageMB: this.estimateMemoryUsage(),
            lastUpdateTimeMs: this.lastUpdateTime,
        }
    }

    // 辅助方法
    protected countVisibleObjects(): number {
        let count = 0
        this.group.traverse((child) => {
            if (child.visible) count++
        })
        return count
    }

    protected estimateMemoryUsage(): number {
        // 简化估算：每个对象约0.1MB
        return this.group.children.length * 0.1
    }

    protected updateTimestamp(): void {
        this.lastUpdateTime = Date.now()
    }
}
```

- [ ] **Step 2: 编译检查**

```bash
cd web
npm run type-check
```
Expected: 无类型错误

- [x] **Step 3: 提交**

```bash
git add src/rendering/layers/baseLayer.ts
git commit -m "feat: 实现基础层抽象类"
```

### Task 3: 实现简单层管理器

**Files:**
- Create: `src/rendering/layers/layerManager.ts`

- [ ] **Step 1: 创建简单层管理器**

```typescript
/**
 * @file layerManager.ts
 *
 * @description
 * 简单层管理器实现，管理多个渲染层的注册和批量操作喵。
 *
 * @usage
 * - 在WorldRenderManager中创建实例喵。
 * - 通过renderOrder排序确保正确的渲染顺序喵。
 */

import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { RenderLayer, LayerManager } from './index'

export class SimpleLayerManager implements LayerManager {
    readonly layers = new Map<string, RenderLayer>()

    registerLayer(layer: RenderLayer): void {
        if (this.layers.has(layer.name)) {
            throw new Error(`Layer with name '${layer.name}' already registered`)
        }
        this.layers.set(layer.name, layer)
    }

    unregisterLayer(name: string): void {
        this.layers.delete(name)
    }

    getLayer(name: string): RenderLayer | null {
        return this.layers.get(name) || null
    }

    updateAll(ctx: WorldRenderContext, frame: WorldFrameState): void {
        // 按renderOrder顺序更新，确保正确的渲染顺序
        const sortedLayers = Array.from(this.layers.values())
            .sort((a, b) => a.renderOrder - b.renderOrder)

        for (const layer of sortedLayers) {
            if (layer.isVisible()) {
                layer.update(ctx, frame)
            }
        }
    }

    setAllVisible(visible: boolean): void {
        for (const layer of this.layers.values()) {
            layer.setVisible(visible)
        }
    }

    disposeAll(ctx: WorldRenderContext): void {
        for (const layer of this.layers.values()) {
            layer.dispose(ctx)
        }
        this.layers.clear()
    }
}
```

- [ ] **Step 2: 编译检查**

```bash
cd web
npm run type-check
```
Expected: 无类型错误

- [ ] **Step 3: 提交**

```bash
git add src/rendering/layers/layerManager.ts
git commit -m "feat: 实现简单层管理器"
```

### Task 4: 改造WorldRenderManager为分层架构

**Files:**
- Modify: `src/rendering/worldRenderManager.ts`

- [ ] **Step 1: 导入层相关模块**

```typescript
// 在文件顶部添加导入
import { SimpleLayerManager } from './layers/layerManager'
import type { RenderLayer } from './layers'
```

- [ ] **Step 2: 在WorldRenderer类型中添加层管理属性**

```typescript
export type WorldRenderer = {
    // ... 现有属性保持不变

    // 新增层控制API
    getLayer: (name: string) => RenderLayer | null
    setLayerVisible: (name: string, visible: boolean) => void
    setLayerQuality: (name: string, quality: number) => void
}
```

- [ ] **Step 3: 修改createWorldRenderManager函数**

在函数内部，在创建cameraSystem后添加层管理器：

```typescript
export function createWorldRenderManager(
    container: HTMLDivElement,
    options: WorldRendererOptions = {}
): WorldRenderer {
    // ... 现有代码保持不变，直到第117行cameraSystem创建后

    // 初始化层管理器
    const layerManager = new SimpleLayerManager()

    // 修改第127-138行的ctx初始化，移除entitiesGroup依赖
    const ctx: WorldRenderContext = {
        renderer,
        scene,
        camera,
        worldGroup,
        entitiesGroup, // 暂时保留，逐步迁移
        zoom,
        cameraWorldPosGU,
        getTexture: textureManager.getTexture,
        getEntityWorldPosGU: entityQuery.getEntityWorldPosGU,
        options,
    }

    // 修改第141行：初始化帧状态构建器时不传入visibilityManager
    const frameBuilder = createFrameStateBuilder(container, cameraWorldPosGU, zoom, options.lod)

    // 删除第144-152行的子系统初始化和循环
    // 改为通过层管理器管理

    // ... 后续代码需要相应调整
```

- [ ] **Step 4: 编译检查**

```bash
cd web
npm run type-check
```
Expected: 可能有类型错误（后续任务修复）

- [ ] **Step 5: 提交**

```bash
git add src/rendering/worldRenderManager.ts
git commit -m "refactor: 开始改造WorldRenderManager支持分层架构"
```

---

## 阶段2：星体层实现

### Task 5: 创建星体层目录结构

**Files:**
- Create: `src/rendering/layers/celestial/index.ts`
- Create: `src/rendering/layers/celestial/celestialLayer.ts`

- [ ] **Step 1: 创建索引文件**

```typescript
/**
 * @file index.ts
 *
 * @description
 * 星体层导出文件喵。
 */

export { CelestialLayer } from './celestialLayer'
```

- [ ] **Step 2: 创建星体层实现**

```typescript
/**
 * @file celestialLayer.ts
 *
 * @description
 * 星体层实现，负责渲染恒星、行星等天体喵。
 *
 * @important_notes
 * - 继承BaseLayer，实现RenderLayer接口喵。
 * - 管理星体渲染器（StarRenderer、PlanetRenderer）喵。
 * - 处理星体LOD和可见性喵。
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../worldRenderManager'
import { BaseLayer } from '../baseLayer'
import { RenderOrder } from '../index'

export class CelestialLayer extends BaseLayer {
    private starRenderer: any = null
    private planetRenderer: any = null

    constructor() {
        super('celestial', RenderOrder.CELESTIAL)
    }

    async init(ctx: WorldRenderContext): Promise<void> {
        // 初始化时将group添加到世界组
        ctx.worldGroup.add(this.group)

        // TODO: 初始化具体渲染器（后续任务实现）
        console.log('CelestialLayer initialized')
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.visible) return

        // TODO: 更新星体渲染器（后续任务实现）
        this.updateTimestamp()
    }

    dispose(ctx: WorldRenderContext): void {
        // TODO: 清理资源（后续任务实现）
        ctx.worldGroup.remove(this.group)
        super.setVisible(false)
    }
}
```

- [ ] **Step 3: 编译检查**

```bash
cd web
npm run type-check
```
Expected: 无类型错误

- [ ] **Step 4: 提交**

```bash
git add src/rendering/layers/celestial/
git commit -m "feat: 创建星体层基础结构"
```

### Task 6: 重构StarRenderer适配星体层

**Files:**
- Create: `src/rendering/layers/celestial/renderers/starRenderer.ts`
- Modify: `src/rendering/subsystems/starRenderer.ts` (标记为待废弃)

- [ ] **Step 1: 创建适配层接口的StarRenderer**

```typescript
/**
 * @file starRenderer.ts
 *
 * @description
 * 恒星渲染器适配层版本，实现层渲染器接口喵。
 *
 * @important_notes
 * - 基于现有StarRenderer重构，适配层架构喵。
 * - 不直接操作entitiesGroup，而是通过层group喵。
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import type { StarDetails } from '../../../net/snapshotWs'
import { shouldRender, getLodSize, shouldShowEffects } from '../../subsystems/lodSystem'

export class LayerStarRenderer {
    private starSpritePool: THREE.Sprite[] = []
    private activeStarSpritesByEntityId = new Map<number, THREE.Sprite>()
    private fallbackCircleTexture: THREE.CanvasTexture | null = null

    constructor(private layerGroup: THREE.Group) {}

    init(): void {
        this.fallbackCircleTexture = this.createCircleTexture()

        // 预创建精灵对象池
        for (let i = 0; i < 50; i++) {
            const material = new THREE.SpriteMaterial({
                color: 0xffffff,
                sizeAttenuation: true,
            })
            const sprite = new THREE.Sprite(material)
            sprite.visible = false
            this.starSpritePool.push(sprite)
        }
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        const { entitiesById, selectedIds, cullingAabb, lod } = frame
        const starLod = lod.star

        // 快速路径：LOD完全隐藏
        if (!starLod.visible) {
            this.releaseAllSprites()
            return
        }

        // 第一遍：检查哪些实体需要渲染
        const visibleEntityIds = new Set<number>()

        for (const entity of entitiesById.values()) {
            if (entity.entityType !== 'STAR') continue

            const isSelected = selectedIds.has(entity.entityId)

            const shouldBeVisible = shouldRender(starLod, isSelected) &&
                entity.posWorldGU &&
                (isSelected || this.isPointInAabb(entity.posWorldGU, cullingAabb))

            if (shouldBeVisible) {
                const details = entity.details as StarDetails | null
                if (details) {
                    visibleEntityIds.add(entity.entityId)
                }
            }
        }

        // 回收不在可见列表中的对象
        this.releaseInvisibleSprites(visibleEntityIds)

        // 第二遍：更新可见实体的渲染数据
        for (const entityId of visibleEntityIds) {
            const entity = entitiesById.get(entityId)!
            const details = entity.details as StarDetails
            this.updateStarSprite(entityId, entity, details, ctx, starLod, selectedIds.has(entityId))
        }
    }

    private updateStarSprite(
        entityId: number,
        entity: any,
        details: StarDetails,
        ctx: WorldRenderContext,
        starLod: any,
        isSelected: boolean
    ): void {
        // 计算实体在屏幕上的实际像素大小
        const radiusGU = details.radiusGU
        const diameterPx = (radiusGU * 2) / ctx.zoom.value
        const MIN_TEXTURE_PIXEL_SIZE = 10

        const useRealTexture = diameterPx >= MIN_TEXTURE_PIXEL_SIZE &&
                               starLod.params.textureQuality >= 0.5

        let size: number
        if (useRealTexture) {
            size = getLodSize(starLod, isSelected, radiusGU * 2)
        } else {
            size = MIN_TEXTURE_PIXEL_SIZE * ctx.zoom.value
        }

        let sprite = this.activeStarSpritesByEntityId.get(entityId)
        if (!sprite) {
            sprite = this.acquireStarSprite()
            this.activeStarSpritesByEntityId.set(entityId, sprite)
            this.layerGroup.add(sprite)
        }

        const material = sprite.material as THREE.SpriteMaterial

        if (useRealTexture) {
            if (material.map === this.fallbackCircleTexture) {
                material.map = null
                material.needsUpdate = true
            }
            if (details.surfaceTexturePath && (!material.map || !material.map.image)) {
                this.loadAndApplyTexture(sprite, material, details.surfaceTexturePath, ctx)
            }
            material.sizeAttenuation = true
        } else {
            if (this.fallbackCircleTexture && material.map !== this.fallbackCircleTexture) {
                material.map = this.fallbackCircleTexture
                material.needsUpdate = true
            }
            material.sizeAttenuation = false
        }

        // 根据表面温度设置颜色
        const temperatureK = details.temperatureK
        material.color.set(this.getStarColorByTemperature(temperatureK))

        const showEffects = shouldShowEffects(starLod, isSelected)
        material.opacity = showEffects ? 1.0 : 0.8

        sprite.scale.set(size, size, 1)
        sprite.position.set(
            entity.posWorldGU!.x - ctx.cameraWorldPosGU.x,
            entity.posWorldGU!.y - ctx.cameraWorldPosGU.y,
            0
        )
        sprite.visible = true
    }

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

    private acquireStarSprite(): THREE.Sprite {
        const sprite = this.starSpritePool.pop()
        if (sprite) {
            sprite.visible = true
            return sprite
        }

        const material = new THREE.SpriteMaterial({
            color: 0xffffff,
            sizeAttenuation: true,
        })
        const s = new THREE.Sprite(material)
        s.visible = true
        return s
    }

    private releaseStarSprite(sprite: THREE.Sprite): void {
        const material = sprite.material as THREE.SpriteMaterial
        material.map = null
        material.needsUpdate = true

        sprite.visible = false
        sprite.parent?.remove(sprite)
        this.starSpritePool.push(sprite)
    }

    private releaseAllSprites(): void {
        for (const [id, sprite] of this.activeStarSpritesByEntityId.entries()) {
            this.activeStarSpritesByEntityId.delete(id)
            this.releaseStarSprite(sprite)
        }
    }

    private releaseInvisibleSprites(visibleEntityIds: Set<number>): void {
        for (const [id, sprite] of this.activeStarSpritesByEntityId.entries()) {
            if (!visibleEntityIds.has(id)) {
                this.activeStarSpritesByEntityId.delete(id)
                this.releaseStarSprite(sprite)
            }
        }
    }

    private loadAndApplyTexture(
        sprite: THREE.Sprite,
        material: THREE.SpriteMaterial,
        surfaceTexturePath: string,
        ctx: WorldRenderContext
    ): void {
        if (material.map && material.map.image) return

        ctx.getTexture(surfaceTexturePath)
            .then((texture) => {
                material.map = texture
                material.needsUpdate = true
                sprite.visible = true
            })
            .catch((error) => {
                console.error(`Failed to load star texture: ${surfaceTexturePath}`, error)
                sprite.visible = true
            })
    }

    private getStarColorByTemperature(temperatureK: number): THREE.Color {
        if (temperatureK >= 30000) return new THREE.Color(0x9bb0ff)
        else if (temperatureK >= 10000) return new THREE.Color(0xa6c5ff)
        else if (temperatureK >= 7500) return new THREE.Color(0xcad7ff)
        else if (temperatureK >= 6000) return new THREE.Color(0xf8f7ff)
        else if (temperatureK >= 5200) return new THREE.Color(0xfff4ea)
        else if (temperatureK >= 3700) return new THREE.Color(0xffd2a1)
        else if (temperatureK >= 2400) return new THREE.Color(0xffb347)
        else return new THREE.Color(0xff6b3d)
    }

    private isPointInAabb(
        point: { x: number; y: number },
        aabb: { minX: number; maxX: number; minY: number; maxY: number },
    ): boolean {
        return point.x >= aabb.minX && point.x <= aabb.maxX && point.y >= aabb.minY && point.y <= aabb.maxY
    }

    dispose(): void {
        if (this.fallbackCircleTexture) {
            this.fallbackCircleTexture.dispose()
            this.fallbackCircleTexture = null
        }

        for (const sprite of this.starSpritePool) {
            (sprite.material as THREE.Material).dispose()
        }
        for (const sprite of this.activeStarSpritesByEntityId.values()) {
            (sprite.material as THREE.Material).dispose()
        }

        this.starSpritePool = []
        this.activeStarSpritesByEntityId.clear()
    }
}
```

- [ ] **Step 2: 标记原有StarRenderer为待废弃**

在 `src/rendering/subsystems/starRenderer.ts` 文件顶部添加注释：

```typescript
/**
 * @deprecated 将迁移到分层架构中的CelestialLayer喵。
 * 新代码请使用 src/rendering/layers/celestial/renderers/starRenderer.ts 喵。
 */
```

- [ ] **Step 3: 编译检查**

```bash
cd web
npm run type-check
```
Expected: 无类型错误

- [ ] **Step 4: 提交**

```bash
git add src/rendering/layers/celestial/renderers/starRenderer.ts src/rendering/subsystems/starRenderer.ts
git commit -m "feat: 重构StarRenderer适配星体层"
```

### Task 7: 完成星体层集成

**Files:**
- Modify: `src/rendering/layers/celestial/celestialLayer.ts`
- Modify: `src/rendering/worldRenderManager.ts`

- [ ] **Step 1: 完善CelestialLayer实现**

更新 `src/rendering/layers/celestial/celestialLayer.ts`：

```typescript
// 在顶部添加导入
import { LayerStarRenderer } from './renderers/starRenderer'

export class CelestialLayer extends BaseLayer {
    private starRenderer: LayerStarRenderer | null = null
    private planetRenderer: any = null // TODO: 后续添加

    constructor() {
        super('celestial', RenderOrder.CELESTIAL)
    }

    async init(ctx: WorldRenderContext): Promise<void> {
        ctx.worldGroup.add(this.group)

        // 初始化恒星渲染器
        this.starRenderer = new LayerStarRenderer(this.group)
        this.starRenderer.init()

        // TODO: 初始化行星渲染器
        console.log('CelestialLayer initialized with star renderer')
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.visible) return

        // 更新恒星渲染器
        if (this.starRenderer) {
            this.starRenderer.update(ctx, frame)
        }

        // TODO: 更新行星渲染器

        this.updateTimestamp()
    }

    dispose(ctx: WorldRenderContext): void {
        // 清理渲染器
        if (this.starRenderer) {
            this.starRenderer.dispose()
            this.starRenderer = null
        }

        // TODO: 清理行星渲染器

        ctx.worldGroup.remove(this.group)
        super.setVisible(false)
    }
}
```

- [ ] **Step 2: 在WorldRenderManager中注册星体层**

更新 `src/rendering/worldRenderManager.ts` 中的 `createWorldRenderManager` 函数：

```typescript
// 在函数顶部添加导入
import { CelestialLayer } from './layers/celestial'

// 在初始化层管理器后注册星体层
const layerManager = new SimpleLayerManager()
layerManager.registerLayer(new CelestialLayer())
```

- [ ] **Step 3: 修改渲染循环使用层管理器**

找到第262-274行的渲染循环初始化代码，修改为：

```typescript
// 初始化渲染循环
const buildFrameState = () => frameBuilder.build(null)
const renderLoop = createRenderLoop(
    renderer,
    scene,
    camera,
    ctx,
    [], // 不再传递子系统数组
    buildFrameState,
    inputSystem,
    cameraWorldPosGU,
    zoom,
    applyCameraTransform,
    layerManager  // 新增参数
)
```

需要更新 `createRenderLoop` 函数以支持层管理器（后续任务）。

- [ ] **Step 4: 编译检查**

```bash
cd web
npm run type-check
```
Expected: 可能有类型错误（需要更新renderLoop）

- [ ] **Step 5: 提交**

```bash
git add src/rendering/layers/celestial/celestialLayer.ts src/rendering/worldRenderManager.ts
git commit -m "feat: 完成星体层集成到WorldRenderManager"
```

### Task 8: 更新RenderLoop支持层管理器

**Files:**
- Modify: `src/rendering/systems/renderLoop.ts`

- [ ] **Step 1: 更新RenderLoop类型定义**

```typescript
// 添加导入
import type { LayerManager } from '../layers'

// 更新RenderLoopOptions类型
export type RenderLoopOptions = {
    keyboardPanSpeed?: number
    layerManager?: LayerManager  // 新增
}

// 更新createRenderLoop函数签名
export function createRenderLoop(
    renderer: THREE.WebGLRenderer,
    scene: THREE.Scene,
    camera: THREE.OrthographicCamera,
    ctx: WorldRenderContext,
    subsystems: WorldRenderSubsystem[], // 保持兼容，但逐步废弃
    buildFrameState: () => FrameState,
    inputSystem: InputSystem,
    cameraWorldPosGU: THREE.Vector2,
    zoom: { value: number },
    applyCameraTransform: () => void,
    options: RenderLoopOptions = {}
): RenderLoop {
    const KEYBOARD_PAN_SPEED = options.keyboardPanSpeed ?? 20
    const layerManager = options.layerManager  // 获取层管理器

    // ... 现有代码
```

- [ ] **Step 2: 修改tick函数使用层管理器**

```typescript
const tick = () => {
    if (!isRunning) return

    rafId = requestAnimationFrame(tick)

    // 处理键盘持续平移（保持现有逻辑）
    const inputState = inputSystem.getState()
    let panX = 0
    let panY = 0

    if (inputState.pressedKeys.has('KeyW') || inputState.pressedKeys.has('ArrowUp')) {
        panY += KEYBOARD_PAN_SPEED
    }
    if (inputState.pressedKeys.has('KeyS') || inputState.pressedKeys.has('ArrowDown')) {
        panY -= KEYBOARD_PAN_SPEED
    }
    if (inputState.pressedKeys.has('KeyA') || inputState.pressedKeys.has('ArrowLeft')) {
        panX -= KEYBOARD_PAN_SPEED
    }
    if (inputState.pressedKeys.has('KeyD') || inputState.pressedKeys.has('ArrowRight')) {
        panX += KEYBOARD_PAN_SPEED
    }

    if (panX !== 0 || panY !== 0) {
        cameraWorldPosGU.x += panX * zoom.value
        cameraWorldPosGU.y += panY * zoom.value
        applyCameraTransform()
    }

    // 构建帧状态
    const frame = buildFrameState()

    // 优先使用层管理器，如果存在
    if (layerManager) {
        layerManager.updateAll(ctx, frame)
    } else {
        // 回退到子系统（兼容模式）
        for (const s of subsystems) {
            s.update(ctx, frame)
        }
    }

    // 渲染场景
    renderer.render(scene, camera)
}
```

- [ ] **Step 3: 编译检查**

```bash
cd web
npm run type-check
```
Expected: 无类型错误

- [ ] **Step 4: 提交**

```bash
git add src/rendering/systems/renderLoop.ts
git commit -m "refactor: 更新RenderLoop支持层管理器"
```

### Task 9: 测试星体层渲染

**Files:**
- Create: `src/__tests__/rendering/layers/celestialLayer.test.ts`

- [ ] **Step 1: 创建测试文件**

```typescript
/**
 * @file celestialLayer.test.ts
 *
 * @description
 * 星体层单元测试喵。
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import * as THREE from 'three'
import { CelestialLayer } from '../../../rendering/layers/celestial'
import { RenderOrder } from '../../../rendering/layers'

describe('CelestialLayer', () => {
    let layer: CelestialLayer

    beforeEach(() => {
        layer = new CelestialLayer()
    })

    afterEach(() => {
        // 清理
    })

    it('应该正确初始化', () => {
        expect(layer.name).toBe('celestial')
        expect(layer.renderOrder).toBe(RenderOrder.CELESTIAL)
        expect(layer.isVisible()).toBe(true)
        expect(layer.group).toBeInstanceOf(THREE.Group)
        expect(layer.group.renderOrder).toBe(RenderOrder.CELESTIAL)
    })

    it('应该控制可见性', () => {
        layer.setVisible(false)
        expect(layer.isVisible()).toBe(false)
        expect(layer.group.visible).toBe(false)

        layer.setVisible(true)
        expect(layer.isVisible()).toBe(true)
        expect(layer.group.visible).toBe(true)
    })

    it('应该控制渲染质量', () => {
        layer.setQuality(0.5)
        // 质量设置应该被限制在0-1之间
        layer.setQuality(1.5)
        expect(layer.getStats().visibleObjects).toBe(0) // 初始无对象
    })
})
```

- [ ] **Step 2: 运行测试**

```bash
cd web
npm run test:unit src/__tests__/rendering/layers/celestialLayer.test.ts
```
Expected: 测试通过

- [ ] **Step 3: 提交**

```bash
git add src/__tests__/rendering/layers/celestialLayer.test.ts
git commit -m "test: 添加星体层单元测试"
```

---

## 阶段3：实体层实现

### Task 10: 创建实体层基础结构

**Files:**
- Create: `src/rendering/layers/entity/index.ts`
- Create: `src/rendering/layers/entity/entityLayer.ts`

- [ ] **Step 1: 创建实体层基础文件**

```typescript
// src/rendering/layers/entity/index.ts
export { EntityLayer } from './entityLayer'

// src/rendering/layers/entity/entityLayer.ts
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../worldRenderManager'
import { BaseLayer } from '../baseLayer'
import { RenderOrder } from '../index'

export class EntityLayer extends BaseLayer {
    private shipRenderer: any = null

    constructor() {
        super('entity', RenderOrder.ENTITY)
    }

    async init(ctx: WorldRenderContext): Promise<void> {
        ctx.worldGroup.add(this.group)
        console.log('EntityLayer initialized')
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.visible) return
        this.updateTimestamp()
    }

    dispose(ctx: WorldRenderContext): void {
        ctx.worldGroup.remove(this.group)
        super.setVisible(false)
    }
}
```

- [ ] **Step 2: 在WorldRenderManager中注册实体层**

```typescript
// 在worldRenderManager.ts中添加导入
import { EntityLayer } from './layers/entity'

// 在注册星体层后添加
layerManager.registerLayer(new EntityLayer())
```

- [ ] **Step 3: 编译检查**

```bash
cd web
npm run type-check
```
Expected: 无类型错误

- [ ] **Step 4: 提交**

```bash
git add src/rendering/layers/entity/
git commit -m "feat: 创建实体层基础结构"
```

### Task 11: 重构ShipRenderer适配实体层

**Files:**
- Create: `src/rendering/layers/entity/renderers/shipRenderer.ts`
- Modify: `src/rendering/subsystems/shipRenderer.ts` (标记为待废弃)

- [x] **Step 1: 创建适配层接口的ShipRenderer**

由于ShipRenderer代码较长，这里创建基础结构，具体实现可参考原有逻辑：

```typescript
/**
 * @file shipRenderer.ts
 *
 * @description
 * 舰船渲染器适配层版本喵。
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'

export class LayerShipRenderer {
    constructor(private layerGroup: THREE.Group) {}

    init(): void {
        // TODO: 初始化对象池等资源
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        // TODO: 实现舰船渲染逻辑
    }

    dispose(): void {
        // TODO: 清理资源
    }
}
```

- [x] **Step 2: 标记原有ShipRenderer为待废弃**

```typescript
// 在 src/rendering/subsystems/shipRenderer.ts 顶部添加
/**
 * @deprecated 将迁移到分层架构中的EntityLayer喵。
 * 新代码请使用 src/rendering/layers/entity/renderers/shipRenderer.ts 喵。
 */
```

- [x] **Step 3: 编译检查**

```bash
cd web
npx vue-tsc -b
```
Expected: 使用@/别名路径解决模块导入问题，类型检查通过

- [x] **Step 4: 提交**

```bash
git add src/rendering/layers/entity/renderers/shipRenderer.ts src/rendering/subsystems/shipRenderer.ts web/src/rendering/layers/entity/entityLayer.ts docs/superpowers/plans/2026-04-10-frontend-render-layers-implementation.md
git commit -m "feat: 完成ShipRenderer重构适配实体层，支持预测和路径渲染"
```

---

## 阶段4：背景层实现

### Task 12: 创建背景层基础结构

**Files:**
- Create: `src/rendering/layers/background/index.ts`
- Create: `src/rendering/layers/background/backgroundLayer.ts`
- Create: `src/rendering/layers/background/renderers/starfieldRenderer.ts`

- [x] **Step 1: 创建背景层基础文件**

```typescript
// src/rendering/layers/background/index.ts
export { BackgroundLayer } from './backgroundLayer'

// src/rendering/layers/background/backgroundLayer.ts
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../worldRenderManager'
import { BaseLayer } from '../baseLayer'
import { RenderOrder } from '../index'
import { StarfieldRenderer } from './renderers/starfieldRenderer'

export class BackgroundLayer extends BaseLayer {
    private starfieldRenderer: StarfieldRenderer | null = null

    constructor() {
        super('background', RenderOrder.BACKGROUND)
    }

    async init(ctx: WorldRenderContext): Promise<void> {
        ctx.worldGroup.add(this.group)

        // 初始化星空渲染器
        this.starfieldRenderer = new StarfieldRenderer(this.group)
        await this.starfieldRenderer.init(ctx)

        console.log('BackgroundLayer initialized')
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.visible) return

        // 更新星空渲染器
        if (this.starfieldRenderer) {
            this.starfieldRenderer.update(ctx, frame)
        }

        this.updateTimestamp()
    }

    dispose(ctx: WorldRenderContext): void {
        if (this.starfieldRenderer) {
            this.starfieldRenderer.dispose()
            this.starfieldRenderer = null
        }

        ctx.worldGroup.remove(this.group)
        super.setVisible(false)
    }
}

// src/rendering/layers/background/renderers/starfieldRenderer.ts
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../../worldRenderManager'

export class StarfieldRenderer {
    private starfieldMesh: THREE.Mesh | null = null

    constructor(private layerGroup: THREE.Group) {}

    async init(ctx: WorldRenderContext): Promise<void> {
        // 创建星空背景平面
        const geometry = new THREE.PlaneGeometry(100000, 100000)
        const texture = await ctx.getTexture('assets/textures/starfield.jpg')

        const material = new THREE.MeshBasicMaterial({
            map: texture,
            transparent: true,
            opacity: 0.8,
            depthWrite: false,
        })

        this.starfieldMesh = new THREE.Mesh(geometry, material)
        this.starfieldMesh.position.set(0, 0, -100) // 放在背景深处
        this.layerGroup.add(this.starfieldMesh)
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.starfieldMesh) return

        // 星空背景随相机移动产生视差效果
        const parallaxFactor = 0.1
        this.starfieldMesh.position.set(
            -ctx.cameraWorldPosGU.x * parallaxFactor,
            -ctx.cameraWorldPosGU.y * parallaxFactor,
            -100
        )
    }

    dispose(): void {
        if (this.starfieldMesh) {
            this.starfieldMesh.geometry.dispose()
            ;(this.starfieldMesh.material as THREE.Material).dispose()
            this.layerGroup.remove(this.starfieldMesh)
            this.starfieldMesh = null
        }
    }
}
```

- [x] **Step 2: 在WorldRenderManager中注册背景层**

```typescript
// 添加导入
import { BackgroundLayer } from './layers/background'

// 注册背景层（在星体层之前，确保渲染顺序）
layerManager.registerLayer(new BackgroundLayer())
```

- [ ] **Step 3: 编译检查**

```bash
cd web
npm run type-check
```
Expected: 无类型错误

- [ ] **Step 4: 提交**

```bash
git add src/rendering/layers/background/
git commit -m "feat: 创建背景层基础结构和星空渲染器"
```

---

## 阶段5：整合测试和清理

### Task 13: 移除旧的子系统引用

**Files:**
- Modify: `src/rendering/worldRenderManager.ts`

- [x] **Step 1: 移除旧的子系统初始化和更新代码**

找到并删除以下代码块：
1. 第144-152行：子系统初始化和循环
2. 第173-175行：子系统更新循环
3. 第285-287行：子系统更新循环
4. 第324-326行：子系统更新循环
5. 第353-354行：子系统dispose循环

- [x] **Step 2: 移除不再需要的导入**

删除不再需要的子系统导入：
```typescript
// 删除这些导入
import { StarRenderer } from './subsystems/starRenderer'
import { PlanetRenderer } from './subsystems/planetRenderer'
import { ShipRenderer } from './subsystems/shipRenderer'
import { SelectionRenderer } from './subsystems/selectionRenderer'
import { GridRenderer } from './subsystems/gridRenderer'
import { HexOutlineRenderer } from './subsystems/hexOutlineRenderer'
```

- [x] **Step 3: 编译检查**

```bash
cd web
npm run type-check
```
Expected: 修复所有类型错误

- [ ] **Step 4: 提交**

```bash
git add src/rendering/worldRenderManager.ts
git commit -m "refactor: 移除旧的子系统引用，完全切换到分层架构"
```

### Task 14: 添加层控制API

**Files:**
- Modify: `src/rendering/worldRenderManager.ts`

- [ ] **Step 1: 实现层控制API**

在WorldRenderer返回对象中添加：

```typescript
return {
    // ... 现有API保持不变

    // 新增层控制API
    getLayer: (name: string) => layerManager.getLayer(name),
    setLayerVisible: (name: string, visible: boolean) => {
        const layer = layerManager.getLayer(name)
        if (layer) layer.setVisible(visible)
    },
    setLayerQuality: (name: string, quality: number) => {
        const layer = layerManager.getLayer(name)
        if (layer) layer.setQuality(quality)
    },
}
```

- [ ] **Step 2: 编译检查**

```bash
cd web
npm run type-check
```
Expected: 无类型错误

- [ ] **Step 3: 提交**

```bash
git add src/rendering/worldRenderManager.ts
git commit -m "feat: 添加层控制API到WorldRenderer"
```

### Task 15: 端到端测试

**Files:**
- Create: `src/__tests__/rendering/integration/layerIntegration.test.ts`

- [ ] **Step 1: 创建集成测试**

```typescript
/**
 * @file layerIntegration.test.ts
 *
 * @description
 * 分层架构集成测试喵。
 */

import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createWorldRenderManager } from '../../../rendering/worldRenderManager'

describe('分层架构集成测试', () => {
    let container: HTMLDivElement
    let renderer: any

    beforeAll(() => {
        // 创建测试容器
        container = document.createElement('div')
        container.style.width = '800px'
        container.style.height = '600px'
        document.body.appendChild(container)
    })

    afterAll(() => {
        if (renderer) {
            renderer.dispose()
        }
        document.body.removeChild(container)
    })

    it('应该正确创建分层渲染管理器', () => {
        renderer = createWorldRenderManager(container)

        expect(renderer).toBeDefined()
        expect(renderer.zoom).toBeDefined()
        expect(renderer.cameraWorldPosGU).toBeDefined()

        // 测试层控制API
        const backgroundLayer = renderer.getLayer('background')
        const celestialLayer = renderer.getLayer('celestial')
        const entityLayer = renderer.getLayer('entity')

        expect(backgroundLayer).toBeDefined()
        expect(celestialLayer).toBeDefined()
        expect(entityLayer).toBeDefined()

        expect(backgroundLayer?.name).toBe('background')
        expect(celestialLayer?.name).toBe('celestial')
        expect(entityLayer?.name).toBe('entity')
    })

    it('应该控制各层可见性', () => {
        renderer.setLayerVisible('background', false)
        renderer.setLayerVisible('celestial', true)
        renderer.setLayerVisible('entity', true)

        const backgroundLayer = renderer.getLayer('background')
        const celestialLayer = renderer.getLayer('celestial')

        expect(backgroundLayer?.isVisible()).toBe(false)
        expect(celestialLayer?.isVisible()).toBe(true)
    })

    it('应该控制各层渲染质量', () => {
        renderer.setLayerQuality('background', 0.5)
        renderer.setLayerQuality('celestial', 0.8)
        renderer.setLayerQuality('entity', 1.0)

        // 质量设置应该生效
        const backgroundLayer = renderer.getLayer('background')
        expect(backgroundLayer).toBeDefined()
    })
})
```

- [ ] **Step 2: 运行集成测试**

```bash
cd web
npm run test:unit src/__tests__/rendering/integration/layerIntegration.test.ts
```
Expected: 测试通过

- [ ] **Step 3: 提交**

```bash
git add src/__tests__/rendering/integration/layerIntegration.test.ts
git commit -m "test: 添加分层架构集成测试"
```

### Task 16: 性能测试和优化

**Files:**
- Create: `scripts/test-render-performance.js`

- [ ] **Step 1: 创建性能测试脚本**

```javascript
/**
 * @file test-render-performance.js
 *
 * @description
 * 渲染性能测试脚本喵。
 */

console.log('=== 渲染分层架构性能测试 ===')

// 模拟测试场景
const testScenarios = [
    { name: '空场景', stars: 0, planets: 0, ships: 0 },
    { name: '小场景', stars: 10, planets: 50, ships: 100 },
    { name: '中场景', stars: 50, planets: 200, ships: 500 },
    { name: '大场景', stars: 200, planets: 1000, ships: 2000 },
]

// 输出测试计划
console.log('测试场景:')
testScenarios.forEach(scene => {
    console.log(`  ${scene.name}: ${scene.stars}恒星, ${scene.planets}行星, ${scene.ships}舰船`)
})

console.log('\n性能测试步骤:')
console.log('1. 测量初始加载时间')
console.log('2. 测量帧率稳定性 (60秒)')
console.log('3. 测量内存使用情况')
console.log('4. 比较分层架构与原架构性能')

console.log('\n验收标准:')
console.log('- 帧率: 不低于原架构95%')
console.log('- 内存: 增加不超过10%')
console.log('- 加载: 时间增加不超过15%')

console.log('\n执行命令:')
console.log('npm run dev')
console.log('然后在浏览器中访问 http://localhost:5173 进行手动测试喵。')
```

- [ ] **Step 2: 运行性能测试准备**

```bash
cd web
node scripts/test-render-performance.js
```
Expected: 输出测试计划

- [ ] **Step 3: 提交**

```bash
git add scripts/test-render-performance.js
git commit -m "chore: 添加渲染性能测试脚本"
```

---

## 计划完成总结

**实现状态**: 实施中，已完成基础架构（Task 1-4）和部分星体层、实体层，剩余背景层和整合清理喵。

**关键里程碑**:
1. ✅ **基础架构** (Task 1-4已完成): 层接口、基础类、管理器、WorldRenderManager改造
2. 🟨 **星体层** (Task 5-9部分完成): CelestialLayer、StarRenderer重构已完成，集成进行中
3. 🟨 **实体层** (Task 10已完成, Task 11进行中): EntityLayer基础完成，ShipRenderer适配中
4. ⬜ **背景层** (Task 12): BackgroundLayer、星空渲染器
5. ⬜ **整合清理** (Task 13-16): 移除旧代码、添加API、测试

**风险评估**:
- **性能风险**: 低 - 逐步迁移，每阶段可测试
- **兼容性风险**: 中 - 保持API不变，现有功能不受影响
- **复杂度风险**: 中 - 任务分解细致，可独立实施

**下一步执行选项**:

**Plan complete and saved to `docs/superpowers/plans/2026-04-10-frontend-render-layers-implementation.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**