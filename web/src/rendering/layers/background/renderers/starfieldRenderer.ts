/**
 * @file starfieldRenderer.ts
 *
 * @description
 * 星空背景渲染器，负责创建和更新星空背景平面喵。
 *
 * 特性喵：
 * - 使用大型平面几何体作为星空背景喵。
 * - 支持纹理贴图（starfield.jpg）喵。
 * - 提供视差滚动效果，随相机移动产生深度感喵。
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '@/rendering/worldRenderManager'

export class StarfieldRenderer {
    private starfieldMesh: THREE.Mesh | null = null
    private layerGroup: THREE.Group

    constructor(layerGroup: THREE.Group) {
        this.layerGroup = layerGroup
    }

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

    update(ctx: WorldRenderContext, _frame: WorldFrameState): void {
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