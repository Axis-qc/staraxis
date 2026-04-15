/**
 * @file planetRenderer.ts
 * @description 分层行星渲染器 - 适配现有PlanetRenderer逻辑到分层架构喵
 * @usage 在CelestialLayer中初始化并调用update方法喵
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import type { PlanetDetails } from '../../../../net/snapshotWs'
// TODO: [TASK-2/3] 任务2/3实现时取消注释
// import { shouldRender, getLodSize } from '../../../subsystems/lodSystem'

export class LayerPlanetRenderer {
  private parentGroup: THREE.Group
  private planetSpritePool: THREE.Sprite[] = []
  private activePlanetSpritesByEntityId = new Map<number, THREE.Sprite>()
  private fallbackCircleTexture: THREE.CanvasTexture | null = null
  private context: WorldRenderContext | null = null
  private poolSize: number
  private disposed = false

  // 移动轨迹相关（复用现有逻辑）
  private trailMeshPool: THREE.Mesh[] = []
  private activeTrailsByEntityId = new Map<number, THREE.Mesh>()
  private positionHistory = new Map<number, Array<{ x: number; y: number }>>()
  private lastSampleMinuteByEntityId = new Map<number, number>()
  private static readonly MAX_TRAIL_POINTS = 1000
  private static readonly DEFAULT_POOL_SIZE = 20

  constructor(parentGroup: THREE.Group, poolSize: number = LayerPlanetRenderer.DEFAULT_POOL_SIZE) {
    if (!parentGroup) {
      throw new Error('parentGroup is required for LayerPlanetRenderer')
    }
    if (poolSize <= 0) {
      throw new Error('poolSize must be positive')
    }
    this.parentGroup = parentGroup
    this.poolSize = poolSize
  }

  private createCircleTexture(): THREE.CanvasTexture {
    const canvas = document.createElement('canvas')
    canvas.width = 64
    canvas.height = 64
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      // 提供降级方案或抛出明确错误喵
      console.error('Failed to get 2D context for planet fallback texture')
      // 创建简单的纯色纹理作为降级喵
      const fallbackCanvas = document.createElement('canvas')
      fallbackCanvas.width = 64
      fallbackCanvas.height = 64
      const fallbackCtx = fallbackCanvas.getContext('2d')
      if (fallbackCtx) {
        fallbackCtx.fillStyle = '#ffffff'
        fallbackCtx.fillRect(0, 0, 64, 64)
        const texture = new THREE.CanvasTexture(fallbackCanvas)
        texture.needsUpdate = true
        return texture
      }
      throw new Error('Canvas 2D context not supported in this environment')
    }

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

  init(ctx: WorldRenderContext): void {
    this.context = ctx
    this.fallbackCircleTexture = this.createCircleTexture()

    // 预创建精灵对象池喵
    for (let i = 0; i < this.poolSize; i++) {
      const material = new THREE.SpriteMaterial({
        color: 0xffffff,
        sizeAttenuation: true,
        map: this.fallbackCircleTexture || undefined  // 使用后备纹理喵
      })
      const sprite = new THREE.Sprite(material)
      sprite.visible = false
      this.parentGroup.add(sprite)
      this.planetSpritePool.push(sprite)
    }

    console.log('LayerPlanetRenderer initialized')
  }

  private acquirePlanetSprite(): THREE.Sprite {
    const sprite = this.planetSpritePool.pop()
    if (sprite) {
      sprite.visible = true
      return sprite
    }

    const material = new THREE.SpriteMaterial({
      color: 0xffffff,
      sizeAttenuation: true,
      map: this.fallbackCircleTexture || undefined  // 使用后备纹理喵
    })
    const newSprite = new THREE.Sprite(material)
    this.parentGroup.add(newSprite)
    return newSprite
  }

  private releasePlanetSprite(sprite: THREE.Sprite): void {
    // 安全的类型检查喵
    if (sprite.material instanceof THREE.SpriteMaterial) {
      const material = sprite.material
      material.map = null
    } else {
      console.warn('Unexpected material type in planet sprite', sprite.material)
    }
    sprite.visible = false
    this.planetSpritePool.push(sprite)
  }

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    // TODO: [TASK-2/3] 实现更新逻辑
  }

  dispose(): void {
    // 防止重复清理喵
    if (this.disposed) {
      return
    }

    // 首先解除所有材质的纹理引用，防止双重释放喵
    const allSprites = [...this.planetSpritePool, ...this.activePlanetSpritesByEntityId.values()]
    for (const sprite of allSprites) {
      if (sprite.material instanceof THREE.SpriteMaterial) {
        const material = sprite.material
        material.map = null
      }
    }

    // 清理后备纹理喵
    if (this.fallbackCircleTexture) {
      this.fallbackCircleTexture.dispose()
      this.fallbackCircleTexture = null
    }

    // 清理对象池中的精灵喵
    for (const sprite of this.planetSpritePool) {
      if (sprite.material instanceof THREE.SpriteMaterial) {
        const material = sprite.material
        // 纹理已统一释放，此处不再单独释放喵
        material.dispose()
      } else {
        console.warn('Unexpected material type in planet sprite pool', sprite.material)
        sprite.material.dispose()
      }
      this.parentGroup.remove(sprite)
    }
    this.planetSpritePool = []

    // 清理活跃精灵喵
    for (const sprite of this.activePlanetSpritesByEntityId.values()) {
      if (sprite.material instanceof THREE.SpriteMaterial) {
        const material = sprite.material
        // 纹理已统一释放，此处不再单独释放喵
        material.dispose()
      } else {
        console.warn('Unexpected material type in active planet sprite', sprite.material)
        sprite.material.dispose()
      }
      this.parentGroup.remove(sprite)
    }
    this.activePlanetSpritesByEntityId.clear()

    // 清理轨迹网格池喵
    for (const mesh of this.trailMeshPool) {
      if (mesh.material instanceof THREE.Material) {
        const material = mesh.material
        const geometry = mesh.geometry
        material.dispose()
        geometry.dispose()
      } else {
        console.warn('Unexpected material type in trail mesh pool', mesh.material)
        mesh.material.dispose()
        mesh.geometry.dispose()
      }
      this.parentGroup.remove(mesh)
    }
    this.trailMeshPool = []

    // 清理活跃轨迹喵
    for (const mesh of this.activeTrailsByEntityId.values()) {
      if (mesh.material instanceof THREE.Material) {
        const material = mesh.material
        const geometry = mesh.geometry
        material.dispose()
        geometry.dispose()
      } else {
        console.warn('Unexpected material type in active trail mesh', mesh.material)
        mesh.material.dispose()
        mesh.geometry.dispose()
      }
      this.parentGroup.remove(mesh)
    }
    this.activeTrailsByEntityId.clear()

    // 清理历史数据喵
    this.positionHistory.clear()
    this.lastSampleMinuteByEntityId.clear()

    console.log('LayerPlanetRenderer disposed')
    this.disposed = true
  }
}
