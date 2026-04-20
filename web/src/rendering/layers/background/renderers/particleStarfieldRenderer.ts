import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '@/rendering/worldRenderManager'

export class ParticleStarfieldRenderer {
    private starfieldPoints: THREE.Points | null = null
    private layerGroup: THREE.Group
    private starsCount = 1200
    private starSize = 6.0
    private circleTexture: THREE.CanvasTexture | null = null

    constructor(layerGroup: THREE.Group) {
        this.layerGroup = layerGroup
    }

    init(ctx: WorldRenderContext): void {
        const geometry = new THREE.BufferGeometry()
        const positions = new Float32Array(this.starsCount * 3)
        const sizes = new Float32Array(this.starsCount)
        const colors = new Float32Array(this.starsCount * 3)

        const depthFromCamera = Math.abs(this.layerGroup.position.z)
        const viewSize = ctx.getViewSizeAtDepth(depthFromCamera)
        const width = Math.abs(viewSize.widthGU)
        const height = Math.abs(viewSize.heightGU)

        let bounds: { left: number; right: number; top: number; bottom: number }
        if (width < 100 || height < 100) {
            console.warn('Camera bounds too small, using default range:', { depthFromCamera, width, height })
            bounds = {
                left: -1000,
                right: 1000,
                top: 1000,
                bottom: -1000,
            }
        } else {
            const boundaryScale = 2.0
            bounds = {
                left: (-width * boundaryScale) / 2,
                right: (width * boundaryScale) / 2,
                top: (height * boundaryScale) / 2,
                bottom: (-height * boundaryScale) / 2,
            }
        }

        for (let i = 0; i < this.starsCount; i++) {
            const randX = (Math.random() + Math.random()) * 0.5
            const randY = (Math.random() + Math.random()) * 0.5
            const useTriangular = Math.random() < 0.8
            const finalRandX = useTriangular ? randX : Math.random()
            const finalRandY = useTriangular ? randY : Math.random()

            const x = THREE.MathUtils.lerp(bounds.left, bounds.right, finalRandX)
            const y = THREE.MathUtils.lerp(bounds.bottom, bounds.top, finalRandY)
            positions[i * 3] = x
            positions[i * 3 + 1] = y
            positions[i * 3 + 2] = 0

            const sizeRandom = Math.pow(Math.random(), 1.5)
            sizes[i] = (sizeRandom * 1.5 + 0.2) * this.starSize

            const starTypeRandom = Math.random()
            let r = 1.0
            let g = 1.0
            let b = 1.0

            if (starTypeRandom < 0.1) {
                r = 0.831
                g = 0.945
                b = 1.0
            } else if (starTypeRandom < 0.25) {
                r = 1.0
                g = 1.0
                b = 1.0
            } else if (starTypeRandom < 0.5) {
                r = 1.0
                g = 0.957
                b = 0.831
            } else if (starTypeRandom < 0.85) {
                r = 1.0
                g = 0.847
                b = 0.659
            } else {
                r = 1.0
                g = 0.722
                b = 0.722
            }

            const brightness = 0.7 + sizeRandom * 0.5
            colors[i * 3] = r * brightness
            colors[i * 3 + 1] = g * brightness
            colors[i * 3 + 2] = b * brightness
        }

        geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
        geometry.setAttribute('size', new THREE.BufferAttribute(sizes, 1))
        geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))

        this.circleTexture = this.createCircleTexture()

        const material = new THREE.PointsMaterial({
            size: this.starSize,
            sizeAttenuation: false,
            vertexColors: true,
            transparent: true,
            opacity: 0.9,
            depthWrite: false,
            depthTest: false,
            blending: THREE.AdditiveBlending,
            map: this.circleTexture,
            alphaTest: 0.01,
        })

        this.starfieldPoints = new THREE.Points(geometry, material)
        this.layerGroup.add(this.starfieldPoints)
    }

    update(ctx: WorldRenderContext, _frame: WorldFrameState): void {
        void ctx

        if (!this.starfieldPoints) return

        const time = Date.now() * 0.001
        const material = this.starfieldPoints.material as THREE.PointsMaterial
        const flicker1 = Math.sin(time * 0.5) * 0.05
        const flicker2 = Math.sin(time * 1.7) * 0.03
        const flicker3 = Math.sin(time * 3.2) * 0.02
        material.opacity = 0.85 + flicker1 + flicker2 + flicker3
    }

    private createCircleTexture(): THREE.CanvasTexture {
        const canvas = document.createElement('canvas')
        canvas.width = 64
        canvas.height = 64
        const ctx = canvas.getContext('2d')!

        ctx.clearRect(0, 0, 64, 64)

        const centerX = 32
        const centerY = 32
        const innerRadius = 8
        const outerRadius = 28

        const coreGradient = ctx.createRadialGradient(centerX, centerY, 0, centerX, centerY, innerRadius)
        coreGradient.addColorStop(0, 'rgba(255, 255, 255, 1.0)')
        coreGradient.addColorStop(0.4, 'rgba(255, 255, 255, 0.9)')
        coreGradient.addColorStop(0.8, 'rgba(255, 255, 255, 0.5)')
        coreGradient.addColorStop(1, 'rgba(255, 255, 255, 0.0)')

        const glowGradient = ctx.createRadialGradient(centerX, centerY, innerRadius * 0.7, centerX, centerY, outerRadius)
        glowGradient.addColorStop(0, 'rgba(255, 255, 255, 0.3)')
        glowGradient.addColorStop(0.5, 'rgba(255, 255, 255, 0.15)')
        glowGradient.addColorStop(1, 'rgba(255, 255, 255, 0.0)')

        ctx.beginPath()
        ctx.arc(centerX, centerY, outerRadius, 0, Math.PI * 2)
        ctx.fillStyle = glowGradient
        ctx.fill()

        ctx.beginPath()
        ctx.arc(centerX, centerY, innerRadius, 0, Math.PI * 2)
        ctx.fillStyle = coreGradient
        ctx.fill()

        const texture = new THREE.CanvasTexture(canvas)
        texture.needsUpdate = true
        return texture
    }

    dispose(): void {
        if (this.circleTexture) {
            this.circleTexture.dispose()
            this.circleTexture = null
        }

        if (this.starfieldPoints) {
            const geometry = this.starfieldPoints.geometry as THREE.BufferGeometry
            geometry.dispose()
            ;(this.starfieldPoints.material as THREE.Material).dispose()
            this.layerGroup.remove(this.starfieldPoints)
            this.starfieldPoints = null
        }
    }
}
