/**
 * @file selectionRenderer.ts
 * @description 分层选中环渲染器 - 绿色虚线环选中指示喵
 * @important_notes 支持所有实体类型（STAR、PLANET、SHIP）喵
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import type { StarDetails, PlanetDetails } from '../../../../net/snapshotWs'
// import { shouldRender, getLodSize } from '../../../subsystems/lodSystem' // TODO: 在后续任务中启用LOD功能

export class LayerSelectionRenderer {
  private parentGroup: THREE.Group
  private linePool: THREE.Line[] = []
  private activeLinesByEntityId = new Map<number, THREE.Line>()
  private context: WorldRenderContext | null = null

  // 技术参数
  private static readonly SELECTION_COLOR = 0x4caf50  // 材质绿
  private static readonly DASH_SIZE = 10
  private static readonly GAP_SIZE = 5
  private static readonly LINE_WIDTH = 2
  private static readonly CIRCLE_SEGMENTS = 48
  private static readonly MIN_PIXEL_SIZE = 10
  private static readonly DEFAULT_SHIP_SIZE = 20
  private static readonly DEFAULT_SIZE = 15

  constructor(parentGroup: THREE.Group) {
    this.parentGroup = parentGroup
  }

  private createDashedRing(radius: number = 1.0): THREE.Line {
    const points: THREE.Vector3[] = []

    // 生成圆形点集
    for (let i = 0; i <= LayerSelectionRenderer.CIRCLE_SEGMENTS; i++) {
      const theta = (i / LayerSelectionRenderer.CIRCLE_SEGMENTS) * Math.PI * 2
      points.push(new THREE.Vector3(
        Math.cos(theta) * radius,
        Math.sin(theta) * radius,
        1  // Z轴位置在实体上方
      ))
    }

    const geometry = new THREE.BufferGeometry().setFromPoints(points)
    const material = new THREE.LineDashedMaterial({
      color: LayerSelectionRenderer.SELECTION_COLOR,
      dashSize: LayerSelectionRenderer.DASH_SIZE,
      gapSize: LayerSelectionRenderer.GAP_SIZE,
      scale: 1,
      linewidth: LayerSelectionRenderer.LINE_WIDTH,
      transparent: true,
      opacity: 0.95,
    })

    const line = new THREE.Line(geometry, material)
    line.computeLineDistances()  // 必须调用以启用虚线
    line.frustumCulled = false
    return line
  }

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

  private getEntitySize(entity: any): number {
    if (!entity) return LayerSelectionRenderer.DEFAULT_SIZE

    switch (entity.entityType) {
      case 'STAR':
        const starDetails = entity.details as StarDetails
        return starDetails?.radiusGU ?? 0
      case 'PLANET':
        const planetDetails = entity.details as PlanetDetails
        return planetDetails?.radiusGU ?? LayerSelectionRenderer.DEFAULT_SIZE
      case 'SHIP':
        return LayerSelectionRenderer.DEFAULT_SHIP_SIZE
      default:
        return LayerSelectionRenderer.DEFAULT_SIZE
    }
  }

  init(ctx: WorldRenderContext): void {
    this.context = ctx
    console.log('LayerSelectionRenderer initialized')
  }

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    // 获取选中实体列表
    const selectedEntities = frame.selection?.selectedEntities ?? []

    // 收集当前需要处理的实体ID
    const currentEntityIds = new Set<number>()

    // 为每个选中实体创建或更新环
    for (const entity of selectedEntities) {
      if (!entity?.id) continue

      const entityId = entity.id
      currentEntityIds.add(entityId)

      // 获取或创建环
      let line = this.activeLinesByEntityId.get(entityId)
      if (!line) {
        line = this.acquireLine()
        this.activeLinesByEntityId.set(entityId, line)
      }

      // 更新环位置和大小
      if (entity.position) {
        line.position.set(entity.position.x, entity.position.y, 1)
      }

      // 根据实体类型调整环大小
      const entitySize = this.getEntitySize(entity)
      const scale = entitySize > 0 ? entitySize : 1.0
      line.scale.set(scale, scale, 1)

      // 更新材质可见性
      line.visible = true
    }

    // 清理不再选中的实体环
    for (const [entityId, line] of this.activeLinesByEntityId.entries()) {
      if (!currentEntityIds.has(entityId)) {
        this.activeLinesByEntityId.delete(entityId)
        this.releaseLine(line)
      }
    }
  }

  dispose(): void {
    // 清理活跃的选中环
    for (const [entityId, line] of this.activeLinesByEntityId.entries()) {
      this.activeLinesByEntityId.delete(entityId)
      this.releaseLine(line)
    }

    // 清理对象池中的所有线对象
    for (const line of this.linePool) {
      // 清理几何体和材质
      if (line.geometry) {
        line.geometry.dispose()
      }
      if (line.material) {
        if (Array.isArray(line.material)) {
          for (const material of line.material) {
            material.dispose()
          }
        } else {
          line.material.dispose()
        }
      }
    }

    // 清空对象池
    this.linePool.length = 0

    console.log('LayerSelectionRenderer disposed')
  }
}