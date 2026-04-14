/**
 * @file particleStarfieldRenderer.ts
 *
 * @description
 * 粒子星空背景渲染器，使用 THREE.Points 创建动态星空粒子系统喵。
 *
 * 特性喵：
 * - 使用 BufferGeometry 生成大量随机分布的星星顶点喵。
 * - 支持星星大小、亮度和深度（z轴）的随机变化喵。
 * - 实现视差滚动效果，不同深度的星星以不同速度移动喵。
 * - 提供可配置的星星数量、大小范围和颜色喵。
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '@/rendering/worldRenderManager'

export class ParticleStarfieldRenderer {
    private starfieldPoints: THREE.Points | null = null
    private layerGroup: THREE.Group
    private starsCount: number = 1200
    private parallaxFactor: number = 0.0 // 屏幕固定背景不需要视差喵。
    private starSize: number = 6.0 // 星星基础大小喵。
    private circleTexture: THREE.CanvasTexture | null = null

    constructor(layerGroup: THREE.Group) {
        this.layerGroup = layerGroup
    }

    /**
     * 初始化粒子星空渲染器，创建几何体、材质并将点集添加到场景中喵。
     * @param ctx 世界渲染上下文，提供相机位置、纹理加载等功能喵。
     */
    init(ctx: WorldRenderContext): void {
        // 创建粒子几何体
        const geometry = new THREE.BufferGeometry()
        const positions = new Float32Array(this.starsCount * 3) // x, y, z
        const sizes = new Float32Array(this.starsCount) // 每个星星的大小
        const colors = new Float32Array(this.starsCount * 3) // 每个星星的颜色（RGB）喵。

        // 基于相机边界生成星星位置（屏幕空间背景，使用较大范围确保覆盖）喵。
        // 相机边界可能较小，乘以较大系数确保星星覆盖整个屏幕区域喵。
        const cameraLeft = ctx.camera.left
        const cameraRight = ctx.camera.right
        const cameraTop = ctx.camera.top
        const cameraBottom = ctx.camera.bottom

        // 如果相机边界为零或异常小，使用默认范围喵。
        const width = Math.abs(cameraRight - cameraLeft)
        const height = Math.abs(cameraTop - cameraBottom)

        let bounds
        if (width < 100 || height < 100) {
            // 使用默认屏幕范围喵。
            console.warn('Camera bounds too small, using default range:', { cameraLeft, cameraRight, cameraTop, cameraBottom, width, height })
            bounds = {
                left: -1000,
                right: 1000,
                top: 1000,
                bottom: -1000
            }
        } else {
            // 使用2.0倍边界，确保星星分布更自然，避免过于密集喵。
            const boundaryScale = 2.0
            bounds = {
                left: cameraLeft * boundaryScale,
                right: cameraRight * boundaryScale,
                top: cameraTop * boundaryScale,
                bottom: cameraBottom * boundaryScale
            }
        }

        for (let i = 0; i < this.starsCount; i++) {
            // 使用三角分布创建更自然的星星分布（中心密集，边缘稀疏）喵。
            // (Math.random() + Math.random()) / 2 产生三角分布喵。
            const randX = (Math.random() + Math.random()) * 0.5
            const randY = (Math.random() + Math.random()) * 0.5

            // 80%的星星使用三角分布，20%使用均匀分布增加随机性喵。
            const useTriangular = Math.random() < 0.8
            const finalRandX = useTriangular ? randX : Math.random()
            const finalRandY = useTriangular ? randY : Math.random()

            const x = THREE.MathUtils.lerp(bounds.left, bounds.right, finalRandX)
            const y = THREE.MathUtils.lerp(bounds.bottom, bounds.top, finalRandY)
            const z = 0 // 屏幕空间平面，深度由父组控制喵。

            positions[i * 3] = x
            positions[i * 3 + 1] = y
            positions[i * 3 + 2] = z

            // 星星大小分布：指数分布，大多数星星较小，少数星星较大较亮喵。
            // 使用Math.random()^2创建偏斜分布喵。
            const sizeRandom = Math.pow(Math.random(), 1.5)
            sizes[i] = (sizeRandom * 1.5 + 0.2) * this.starSize

            // 星星颜色：基于真实恒星类型分布喵。
            const starTypeRandom = Math.random()
            let r, g, b

            if (starTypeRandom < 0.1) {
                // 淡蓝色星星 (O/B/A型星，约10%) 喵。
                r = 0.831; g = 0.945; b = 1.0
            } else if (starTypeRandom < 0.25) {
                // 白色星星 (F型星，约15%) 喵。
                r = 1.0; g = 1.0; b = 1.0
            } else if (starTypeRandom < 0.5) {
                // 淡黄色星星 (G型星，像太阳，约25%) 喵。
                r = 1.0; g = 0.957; b = 0.831
            } else if (starTypeRandom < 0.85) {
                // 橙黄色星星 (K型星，约35%) 喵。
                r = 1.0; g = 0.847; b = 0.659
            } else {
                // 淡红色星星 (M型星，约15%) 喵。
                r = 1.0; g = 0.722; b = 0.722
            }

            // 添加亮度变化：基于星星大小调整亮度喵。
            const brightness = 0.7 + sizeRandom * 0.5 // 较大星星更亮喵。
            colors[i * 3] = r * brightness
            colors[i * 3 + 1] = g * brightness
            colors[i * 3 + 2] = b * brightness
        }

        geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
        geometry.setAttribute('size', new THREE.BufferAttribute(sizes, 1))
        geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))

        // 创建圆形纹理
        this.circleTexture = this.createCircleTexture()

        // 创建粒子材质
        const material = new THREE.PointsMaterial({
            size: this.starSize,
            sizeAttenuation: false, // 正交相机下，sizeAttenuation效果不佳喵。
            vertexColors: true, // 使用顶点颜色喵。
            transparent: true,
            opacity: 0.9,
            depthWrite: false,
            depthTest: false, // 背景层禁用深度测试，确保始终渲染喵。
            blending: THREE.AdditiveBlending, // 使用叠加混合增强亮度喵。
            map: this.circleTexture, // 使用圆形纹理喵。
            alphaTest: 0.01, // 降低透明度阈值确保星星可见喵。
        })

        this.starfieldPoints = new THREE.Points(geometry, material)
        // 点集位置为 (0, 0, 0)，相对于父组（背景组）
        // 背景组已经设置在相机后面的位置 (0, 0, -1500)
        this.layerGroup.add(this.starfieldPoints)

        // 计算平均星星大小用于日志喵。
        let avgSize = 0
        for (let i = 0; i < this.starsCount; i++) {
            avgSize += sizes[i]
        }
        avgSize /= this.starsCount

        console.log('[星空渲染器] 屏幕空间星空背景已初始化', {
            星星数量: this.starsCount,
            平均大小: avgSize.toFixed(2),
            基础大小: this.starSize,
            组位置: '相机子对象，深度-1500'
        })
    }

    /**
     * 更新粒子星空渲染器，背景已绑定到相机作为子对象，自动跟随相机移动喵。
     * @param ctx 世界渲染上下文，包含相机位置等信息喵。
     * @param _frame 当前帧状态（未使用）喵。
     */
    update(ctx: WorldRenderContext, _frame: WorldFrameState): void {
        if (!this.starfieldPoints) return

        // 背景组已经是相机的子对象，自动跟随相机移动，无需额外处理

        // 添加微弱的整体闪烁效果增强真实感喵。
        const time = Date.now() * 0.001
        const material = this.starfieldPoints.material as THREE.PointsMaterial

        // 使用多个频率的正弦波叠加创建更自然的闪烁效果喵。
        const flicker1 = Math.sin(time * 0.5) * 0.05
        const flicker2 = Math.sin(time * 1.7) * 0.03
        const flicker3 = Math.sin(time * 3.2) * 0.02
        const totalFlicker = flicker1 + flicker2 + flicker3

        // 基础透明度加上闪烁变化喵。
        material.opacity = 0.85 + totalFlicker
    }

    /**
     * 创建圆形纹理用于星星渲染喵。
     */
    private createCircleTexture(): THREE.CanvasTexture {
        const canvas = document.createElement('canvas')
        canvas.width = 64 // 增加尺寸以获得更高质量的星星纹理喵。
        canvas.height = 64
        const ctx = canvas.getContext('2d')!

        ctx.clearRect(0, 0, 64, 64)

        const centerX = 32
        const centerY = 32
        const innerRadius = 8  // 核心半径喵。
        const outerRadius = 28 // 光晕半径喵。

        // 创建核心渐变：中心亮白，边缘柔和过渡喵。
        const coreGradient = ctx.createRadialGradient(
            centerX, centerY, 0,
            centerX, centerY, innerRadius
        )
        coreGradient.addColorStop(0, 'rgba(255, 255, 255, 1.0)')   // 中心最亮喵。
        coreGradient.addColorStop(0.4, 'rgba(255, 255, 255, 0.9)') // 中间保持高亮喵。
        coreGradient.addColorStop(0.8, 'rgba(255, 255, 255, 0.5)') // 边缘半透明喵。
        coreGradient.addColorStop(1, 'rgba(255, 255, 255, 0.0)')   // 完全透明喵。

        // 创建外圈光晕：更大的柔和光晕喵。
        const glowGradient = ctx.createRadialGradient(
            centerX, centerY, innerRadius * 0.7,
            centerX, centerY, outerRadius
        )
        glowGradient.addColorStop(0, 'rgba(255, 255, 255, 0.3)')   // 内边缘轻微光晕喵。
        glowGradient.addColorStop(0.5, 'rgba(255, 255, 255, 0.15)') // 中间光晕喵。
        glowGradient.addColorStop(1, 'rgba(255, 255, 255, 0.0)')   // 外边缘完全透明喵。

        // 先绘制外圈光晕喵。
        ctx.beginPath()
        ctx.arc(centerX, centerY, outerRadius, 0, Math.PI * 2)
        ctx.fillStyle = glowGradient
        ctx.fill()

        // 再绘制核心星星喵。
        ctx.beginPath()
        ctx.arc(centerX, centerY, innerRadius, 0, Math.PI * 2)
        ctx.fillStyle = coreGradient
        ctx.fill()

        const texture = new THREE.CanvasTexture(canvas)
        texture.needsUpdate = true
        return texture
    }

    /**
     * 释放粒子星空渲染器资源，清理几何体、材质并从场景中移除点集喵。
     */
    dispose(): void {
        if (this.circleTexture) {
            this.circleTexture.dispose()
            this.circleTexture = null
        }

        if (this.starfieldPoints) {
            const geometry = this.starfieldPoints.geometry as THREE.BufferGeometry
            geometry.dispose()
                ; (this.starfieldPoints.material as THREE.Material).dispose()
            this.layerGroup.remove(this.starfieldPoints)
            this.starfieldPoints = null
        }
    }
}