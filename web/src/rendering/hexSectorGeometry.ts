import * as THREE from 'three'

export type SectorCenter2D = { x: number; y: number }

/**
 * 与 game 端一致的权威常量：
 * SECTOR_DIAMETER_GU = 200_000_000
 * SECTOR_SIZE_GU = SECTOR_DIAMETER_GU / 2
 */
export const SECTOR_DIAMETER_GU = 200_000_000
export const SECTOR_SIZE_GU = SECTOR_DIAMETER_GU / 2

/**
 * pointy-top hex 的 6 个顶点（围绕中心），返回线段端点对（适配 LineSegments）。
 */
export function buildHexSegmentPositions(centers: SectorCenter2D[]): Float32Array {
    // 每个 hex：6 条边 * 2 端点 * 3 floats
    const segFloatsPerHex = 6 * 2 * 3
    const segPositions = new Float32Array(centers.length * segFloatsPerHex)

    const size = SECTOR_SIZE_GU
    const anglesDeg = [30, 90, 150, 210, 270, 330]

    let o = 0
    for (const c of centers) {
        // 先计算 6 个顶点（不闭合）
        const vx: number[] = []
        const vy: number[] = []
        for (const deg of anglesDeg) {
            const rad = (deg * Math.PI) / 180
            vx.push(c.x + size * Math.cos(rad))
            vy.push(c.y + size * Math.sin(rad))
        }

        // 6 条边：i -> (i+1)%6
        for (let i = 0; i < 6; i++) {
            const j = (i + 1) % 6
            segPositions[o++] = vx[i] ?? 0
            segPositions[o++] = vy[i] ?? 0
            segPositions[o++] = 0
            segPositions[o++] = vx[j] ?? 0
            segPositions[o++] = vy[j] ?? 0
            segPositions[o++] = 0
        }
    }

    return segPositions
}

export function createHexOutlinesLine(centers: SectorCenter2D[]): THREE.LineSegments {
    const segPositions = buildHexSegmentPositions(centers)

    const geometry = new THREE.BufferGeometry()
    // 注意：GPU 最终仍是 float32，这里接受 float32，并依赖 renderer 做相对坐标（floating origin）
    geometry.setAttribute('position', new THREE.BufferAttribute(segPositions, 3))

    const material = new THREE.LineBasicMaterial({
        color: 0x7fd3ff,
        transparent: true,
        opacity: 0.55,
    })

    return new THREE.LineSegments(geometry, material)
}
