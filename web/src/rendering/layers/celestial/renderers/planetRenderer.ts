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

  // 移动轨迹相关（复用现有逻辑）
  private trailMeshPool: THREE.Mesh[] = []
  private activeTrailsByEntityId = new Map<number, THREE.Mesh>()
  private positionHistory = new Map<number, Array<{ x: number; y: number }>>()
  private lastSampleMinuteByEntityId = new Map<number, number>()
  private static readonly MAX_TRAIL_POINTS = 1000

  constructor(parentGroup: THREE.Group) {
    if (!parentGroup) {
      throw new Error('parentGroup is required for LayerPlanetRenderer')
    }
    this.parentGroup = parentGroup
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

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    // TODO: [TASK-2/3] 实现更新逻辑
  }

  dispose(): void {
    // TODO: [TASK-2/3] 实现清理逻辑
  }
}
