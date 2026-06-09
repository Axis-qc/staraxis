/**
 * @file planetTextureGenerator.ts
 *
 * @description
 * 行星程序化纹理生成器喵。
 *
 * 作用：
 * - 基于种子（seed）和行星类型 Profile，用 Canvas 2D 生成等距柱状投影纹理喵。
 * - 纹理生成后固定不变，相同 seed 永远产生相同纹理喵。
 * - 使用 Value Noise + FBM 实现自然地表特征喵。
 *
 * 纹理尺寸：256×128 像素（等距柱状投影）喵。
 */

import type { PlanetRenderProfile, PlanetFeatureType } from './planetProfile'

// ─── 常量 ──────────────────────────────────────────────────────────────

const TEXTURE_WIDTH = 1024
const TEXTURE_HEIGHT = 512

// ─── 工具函数 ──────────────────────────────────────────────────────────

/** 线性插值喵 */
function lerp(a: number, b: number, t: number): number {
    return a + (b - a) * t
}

/** clamp 到 [min, max] 喵 */
function clamp(v: number, min: number, max: number): number {
    return v < min ? min : v > max ? max : v
}

/** smoothstep 插值喵 */
function smoothstep(edge0: number, edge1: number, x: number): number {
    const t = clamp((x - edge0) / (edge1 - edge0), 0, 1)
    return t * t * (3 - 2 * t)
}

// ─── 确定性伪随机 ──────────────────────────────────────────────────────

/**
 * 种子化哈希函数（整数输入）喵。
 * 相同 (x, y, seed) 永远返回相同值喵。
 */
function hash(x: number, y: number, seed: number): number {
    let h = (x * 374761393 + y * 668265263 + seed) | 0
    h = Math.imul(h ^ (h >>> 13), 1274126177)
    h = h ^ (h >>> 16)
    return (h & 0x7fffffff) / 0x7fffffff  // [0, 1)
}

/**
 * 简单的 seedable PRNG（用于非空间随机）喵。
 */
function seededRandom(seed: number): () => number {
    let s = seed | 0
    return () => {
        s = Math.imul(s ^ (s >>> 15), 1 | s)
        s = (s + Math.imul(s ^ (s >>> 7), 61 | s)) ^ s
        return ((s ^ (s >>> 14)) >>> 0) / 4294967296
    }
}

// ─── 噪声函数 ──────────────────────────────────────────────────────────

/**
 * 2D Value Noise（双线性插值）喵。
 * 输出范围 [0, 1] 喵。
 */
function valueNoise(x: number, y: number, seed: number): number {
    const ix = Math.floor(x)
    const iy = Math.floor(y)
    const fx = x - ix
    const fy = y - iy

    // smoothstep 插值因子喵
    const sx = fx * fx * (3 - 2 * fx)
    const sy = fy * fy * (3 - 2 * fy)

    const n00 = hash(ix, iy, seed)
    const n10 = hash(ix + 1, iy, seed)
    const n01 = hash(ix, iy + 1, seed)
    const n11 = hash(ix + 1, iy + 1, seed)

    return lerp(lerp(n00, n10, sx), lerp(n01, n11, sx), sy)
}

/**
 * 分形布朗运动（FBM）喵。
 * 多层 Value Noise 叠加，产生自然的细节层次喵。
 *
 * @returns 值范围 [0, 1]（归一化后）喵。
 */
function fbm(
    x: number,
    y: number,
    seed: number,
    octaves: number,
    persistence: number,
    lacunarity: number,
): number {
    let value = 0
    let amplitude = 1
    let frequency = 1
    let maxValue = 0

    for (let i = 0; i < octaves; i++) {
        value += amplitude * valueNoise(x * frequency, y * frequency, seed + i * 31)
        maxValue += amplitude
        amplitude *= persistence
        frequency *= lacunarity
    }

    return value / maxValue
}

/**
 * 域扭曲（Domain Warp）喵。
 * 用另一组噪声偏移坐标，增加自然感和不规则性喵。
 */
function domainWarp(
    x: number,
    y: number,
    seed: number,
    strength: number,
): { wx: number; wy: number } {
    const wx = x + strength * (valueNoise(x * 0.8 + 3.7, y * 0.8 - 1.2, seed + 100) - 0.5) * 2
    const wy = y + strength * (valueNoise(x * 0.8 - 2.3, y * 0.8 + 4.5, seed + 200) - 0.5) * 2
    return { wx, wy }
}

// ─── 颜色工具 ──────────────────────────────────────────────────────────

type RGB = [number, number, number]

/** 线性混合两个 RGB 颜色喵 */
function mixColor(a: RGB, b: RGB, t: number): RGB {
    return [
        Math.round(lerp(a[0], b[0], t)),
        Math.round(lerp(a[1], b[1], t)),
        Math.round(lerp(a[2], b[2], t)),
    ]
}

/** 给颜色添加噪声扰动（保持色相但改变明度）喵 */
function perturbColor(color: RGB, noise: number, range: number): RGB {
    const factor = 1 + (noise - 0.5) * range
    return [
        clamp(Math.round(color[0] * factor), 0, 255),
        clamp(Math.round(color[1] * factor), 0, 255),
        clamp(Math.round(color[2] * factor), 0, 255),
    ]
}

// ─── 特征渲染器 ────────────────────────────────────────────────────────

/**
 * 类地行星：大陆 + 海洋 + 极地冰盖喵。
 */
function renderContinents(
    px: number, py: number,
    nx: number, ny: number,
    profile: PlanetRenderProfile,
    seed: number,
): RGB {
    const p = profile.featureParams
    const seaLevel = p.seaLevel ?? 0.45
    const polarWidth = p.polarWidth ?? 0.12
    const cloudDensity = p.cloudDensity ?? 0.15

    // 域扭曲后的高度图喵
    const { wx, wy } = domainWarp(nx * profile.noiseScale, ny * profile.noiseScale, seed, 0.3)
    const height = fbm(wx, wy, seed, profile.noiseOctaves, profile.noisePersistence, profile.noiseLacunarity)

    // 极地判定（基于纬度）喵
    const latitude = Math.abs(ny - 0.5) * 2  // 0=赤道, 1=极点
    const isPolar = latitude > (1 - polarWidth)

    // 云层噪声喵
    const cloudNoise = fbm(nx * 5 + 100, ny * 5 + 100, seed + 500, 3, 0.5, 2.0)

    let color: RGB
    if (height < seaLevel) {
        // 海洋：深度越深颜色越深喵
        const depth = (seaLevel - height) / seaLevel
        color = mixColor(profile.baseColor, [20, 40, 80], depth * 0.6)
    } else {
        // 大陆：高度越高颜色越亮喵
        const elevation = (height - seaLevel) / (1 - seaLevel)
        color = mixColor(profile.accentColor, profile.detailColor, elevation * 0.7)
    }

    // 极地冰盖喵
    if (isPolar) {
        const polarBlend = (latitude - (1 - polarWidth)) / polarWidth
        color = mixColor(color, profile.detailColor, smoothstep(0, 1, polarBlend))
    }

    // 云层叠加喵
    if (cloudNoise > (1 - cloudDensity)) {
        const cloudBlend = smoothstep(1 - cloudDensity, 1, cloudNoise) * 0.6
        color = mixColor(color, [240, 245, 255], cloudBlend)
    }

    // 细节噪声扰动喵
    const detail = valueNoise(px * 0.15, py * 0.15, seed + 700)
    color = perturbColor(color, detail, 0.12)

    return color
}

/**
 * 气态巨行星 / 冰巨行星：水平条纹 + 漩涡喵。
 */
function renderBands(
    _px: number, _py: number,
    nx: number, ny: number,
    profile: PlanetRenderProfile,
    seed: number,
): RGB {
    const p = profile.featureParams
    const bandFreq = p.bandFrequency ?? 10
    const bandWarp = p.bandWarp ?? 0.3
    const stormProb = p.stormProbability ?? 0.3
    const stormSize = p.stormSize ?? 0.06

    // 条纹：基于纬度的正弦波 + 噪声扭曲喵
    const warpNoise = fbm(nx * 3, ny * 3, seed + 300, 3, 0.5, 2.0)
    const band = Math.sin((ny + warpNoise * bandWarp) * bandFreq * Math.PI)

    // 条纹颜色混合喵
    const bandT = clamp(band * 0.5 + 0.5, 0, 1)
    let color = mixColor(profile.baseColor, profile.accentColor, bandT)

    // 细节纹理喵
    const detail = fbm(nx * profile.noiseScale * 2, ny * profile.noiseScale * 2, seed + 400, 3, 0.4, 2.0)
    color = perturbColor(color, detail, 0.15)

    // 大红斑/漩涡（基于 seed 确定性决定有无）喵
    const rng = seededRandom(seed + 999)
    const hasStorm = rng() < stormProb
    if (hasStorm) {
        const stormX = 0.3 + rng() * 0.4  // 在球面中间区域
        const stormY = 0.3 + rng() * 0.4
        const dx = nx - stormX
        const dy = ny - stormY
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < stormSize) {
            const stormBlend = smoothstep(stormSize, 0, dist)
            const stormColor = mixColor(profile.accentColor, [200, 80, 60], 0.4)
            color = mixColor(color, stormColor, stormBlend * 0.7)
        }
    }

    return color
}

/**
 * 岩石 / 荒芜行星：撞击坑 + 荒漠喵。
 */
function renderCraters(
    px: number, py: number,
    nx: number, ny: number,
    profile: PlanetRenderProfile,
    seed: number,
): RGB {
    const p = profile.featureParams
    const craterDensity = p.craterDensity ?? 0.3
    const craterDepth = p.craterDepth ?? 0.4
    const dustRatio = p.dustRatio ?? 0.4

    // 基础地形噪声喵
    const terrain = fbm(
        nx * profile.noiseScale,
        ny * profile.noiseScale,
        seed,
        profile.noiseOctaves,
        profile.noisePersistence,
        profile.noiseLacunarity,
    )

    // 荒漠区域判定喵
    const isDust = terrain > (1 - dustRatio)
    let color: RGB = isDust
        ? mixColor(profile.baseColor, profile.accentColor, 0.3)
        : profile.baseColor

    // 撞击坑：确定性随机放置多个圆环喵
    const rng = seededRandom(seed + 600)
    const craterCount = Math.floor(craterDensity * 20) + 2
    for (let i = 0; i < craterCount; i++) {
        const cx = rng()
        const cy = rng()
        const cr = 0.02 + rng() * 0.08  // 坑半径

        const dx = nx - cx
        const dy = ny - cy
        const dist = Math.sqrt(dx * dx + dy * dy)

        if (dist < cr) {
            // 坑内：暗色喵
            const rimBlend = smoothstep(cr, cr * 0.6, dist)
            color = mixColor(color, perturbColor(profile.accentColor, rng(), 0.2), rimBlend * craterDepth)
        } else if (dist < cr * 1.3) {
            // 坑缘：亮色隆起喵
            const rimBlend = smoothstep(cr * 1.3, cr, dist) * smoothstep(cr, cr * 0.95, dist)
            color = mixColor(color, profile.detailColor, rimBlend * 0.4)
        }
    }

    // 细节噪声喵
    const detail = valueNoise(px * 0.12, py * 0.12, seed + 800)
    color = perturbColor(color, detail, 0.1)

    return color
}

/**
 * 熔岩行星：暗岩 + 发光裂隙喵。
 */
function renderLavaFlows(
    _px: number, _py: number,
    nx: number, ny: number,
    profile: PlanetRenderProfile,
    seed: number,
): RGB {
    const p = profile.featureParams
    const lavaRatio = p.lavaRatio ?? 0.35
    const crackScale = p.crackScale ?? 8.0

    // 岩石基底喵
    const rock = fbm(
        nx * profile.noiseScale,
        ny * profile.noiseScale,
        seed,
        profile.noiseOctaves,
        profile.noisePersistence,
        profile.noiseLacunarity,
    )

    // 裂隙噪声（高频）喵
    const { wx, wy } = domainWarp(nx * crackScale, ny * crackScale, seed + 100, 0.15)
    const crack = fbm(wx, wy, seed + 150, 3, 0.6, 2.5)

    // 裂隙边缘判定喵
    const crackEdge = Math.abs(crack - 0.5) * 2  // 0=裂隙中心, 1=远离裂隙
    const isLava = crackEdge < lavaRatio * 0.5

    let color: RGB
    if (isLava) {
        // 岩浆：从亮黄到暗红的渐变喵
        const heat = 1 - crackEdge / (lavaRatio * 0.5)
        const lavaColor = mixColor(profile.accentColor, profile.detailColor, heat)
        color = lavaColor
    } else {
        // 岩石：基于高度变化喵
        color = mixColor(profile.baseColor, [60, 55, 50], rock * 0.5)
    }

    // 火山口热点喵
    const rng = seededRandom(seed + 500)
    const volcanoCount = 3 + Math.floor(rng() * 4)
    for (let i = 0; i < volcanoCount; i++) {
        const vx = rng()
        const vy = rng()
        const vr = 0.03 + rng() * 0.05
        const dx = nx - vx
        const dy = ny - vy
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < vr) {
            const glow = smoothstep(vr, 0, dist)
            color = mixColor(color, profile.detailColor, glow * 0.8)
        }
    }

    return color
}

/**
 * 冰雪行星：冰盖 + 冰面 + 裂纹喵。
 */
function renderIceCaps(
    px: number, py: number,
    nx: number, ny: number,
    profile: PlanetRenderProfile,
    seed: number,
): RGB {
    const p = profile.featureParams
    const iceCapWidth = p.iceCapWidth ?? 0.25
    const crackDensity = p.crackDensity ?? 0.3
    const frostNoise = p.frostNoise ?? 0.2

    // 冰面基础噪声喵
    const iceNoise = fbm(
        nx * profile.noiseScale,
        ny * profile.noiseScale,
        seed,
        profile.noiseOctaves,
        profile.noisePersistence,
        profile.noiseLacunarity,
    )

    // 纬度喵
    const latitude = Math.abs(ny - 0.5) * 2

    // 极地冰盖 vs 赤道冰面喵
    const isPolar = latitude > (1 - iceCapWidth)
    let color: RGB

    if (isPolar) {
        // 极地：纯白冰喵
        const polarBlend = (latitude - (1 - iceCapWidth)) / iceCapWidth
        color = mixColor(profile.accentColor, profile.detailColor, polarBlend)
    } else {
        // 中纬度：带有蓝灰色斑块的冰面喵
        color = mixColor(profile.baseColor, profile.accentColor, iceNoise * 0.6)
    }

    // 冰裂纹喵
    const rng = seededRandom(seed + 400)
    const crackCount = Math.floor(crackDensity * 15) + 1
    for (let i = 0; i < crackCount; i++) {
        const cx1 = rng(), cy1 = rng()
        const cx2 = cx1 + (rng() - 0.5) * 0.3, cy2 = cy1 + (rng() - 0.5) * 0.15
        // 简化：点到线段距离近似喵
        const t = clamp(
            ((nx - cx1) * (cx2 - cx1) + (ny - cy1) * (cy2 - cy1)) /
            ((cx2 - cx1) ** 2 + (cy2 - cy1) ** 2 + 0.001),
            0, 1,
        )
        const px2 = cx1 + t * (cx2 - cx1)
        const py2 = cy1 + t * (cy2 - cy1)
        const dist = Math.sqrt((nx - px2) ** 2 + (ny - py2) ** 2)
        if (dist < 0.008) {
            const crackBlend = smoothstep(0.008, 0, dist)
            color = mixColor(color, [40, 60, 90], crackBlend * 0.6)
        }
    }

    // 霜冻噪声扰动喵
    const frost = valueNoise(px * 0.2, py * 0.2, seed + 600)
    color = perturbColor(color, frost, frostNoise)

    return color
}

/**
 * 海洋行星：深海 + 少量岛屿喵。
 */
function renderOceanSurface(
    px: number, py: number,
    nx: number, ny: number,
    profile: PlanetRenderProfile,
    seed: number,
): RGB {
    const p = profile.featureParams
    const islandRatio = p.islandRatio ?? 0.05
    const waveScale = p.waveScale ?? 6.0

    // 深度噪声喵
    const { wx, wy } = domainWarp(nx * profile.noiseScale, ny * profile.noiseScale, seed, 0.2)
    const depth = fbm(wx, wy, seed, profile.noiseOctaves, profile.noisePersistence, profile.noiseLacunarity)

    // 基础海洋色（深度影响）喵
    let color: RGB = mixColor(profile.baseColor, profile.accentColor, depth * 0.5)

    // 波纹纹理喵
    const wave = Math.sin(nx * waveScale * Math.PI * 2 + depth * 3) * 0.5 + 0.5
    const waveDetail = valueNoise(px * 0.3, py * 0.3, seed + 300)
    color = perturbColor(color, wave * 0.3 + waveDetail * 0.7, 0.08)

    // 少量岛屿喵
    const islandNoise = fbm(nx * 8, ny * 8, seed + 700, 4, 0.5, 2.0)
    if (islandNoise > (1 - islandRatio)) {
        const islandBlend = smoothstep(1 - islandRatio, 1, islandNoise)
        color = mixColor(color, [80, 130, 60], islandBlend * 0.8)
    }

    // 浪花白顶喵
    const foam = valueNoise(px * 0.5, py * 0.5, seed + 900)
    if (foam > 0.85 && depth > 0.4) {
        color = mixColor(color, profile.detailColor, (foam - 0.85) * 4)
    }

    return color
}

// ─── 特征渲染器分发表 ──────────────────────────────────────────────────

const FEATURE_RENDERERS: Record<PlanetFeatureType, (
    px: number, py: number,
    nx: number, ny: number,
    profile: PlanetRenderProfile,
    seed: number,
) => RGB> = {
    continents: renderContinents,
    bands: renderBands,
    craters: renderCraters,
    lava_flows: renderLavaFlows,
    ice_caps: renderIceCaps,
    ocean_surface: renderOceanSurface,
}

// ─── 公开 API ──────────────────────────────────────────────────────────

/**
 * 生成行星程序化纹理喵。
 *
 * 确定性保证：相同 (worldSeed, entityId) 永远产生完全相同的纹理喵。
 *
 * @param worldSeed - 世界种子（由 worldId 哈希而来）喵。
 * @param entityId - 行星实体 ID 喵。
 * @param profile - 行星渲染配置喵。
 * @returns Canvas 元素（可用于 THREE.CanvasTexture）喵。
 */
export function generatePlanetCanvas(
    worldSeed: number,
    entityId: number,
    profile: PlanetRenderProfile,
): HTMLCanvasElement {
    // 混合世界种子与实体 ID，生成该行星独有的种子喵
    const seed = mixSeed(worldSeed, entityId)

    const canvas = document.createElement('canvas')
    canvas.width = TEXTURE_WIDTH
    canvas.height = TEXTURE_HEIGHT
    const ctx = canvas.getContext('2d')!
    const imageData = ctx.createImageData(TEXTURE_WIDTH, TEXTURE_HEIGHT)
    const pixels = imageData.data

    const renderer = FEATURE_RENDERERS[profile.featureType]

    for (let py = 0; py < TEXTURE_HEIGHT; py++) {
        // 归一化 y: 0=北极, 0.5=赤道, 1=南极喵
        const ny = py / TEXTURE_HEIGHT

        for (let px = 0; px < TEXTURE_WIDTH; px++) {
            // 归一化 x: 0-1 覆盖经度 0°-360°喵
            const nx = px / TEXTURE_WIDTH

            const [r, g, b] = renderer(px, py, nx, ny, profile, seed)

            const idx = (py * TEXTURE_WIDTH + px) * 4
            pixels[idx] = r
            pixels[idx + 1] = g
            pixels[idx + 2] = b
            pixels[idx + 3] = 255
        }
    }

    ctx.putImageData(imageData, 0, 0)
    return canvas
}

/**
 * 混合世界种子与行星 ID，产生该行星独有的确定性种子喵。
 * 参考后端 SurfaceNamingUtils.mixSeed 算法喵。
 */
function mixSeed(worldSeed: number, entityId: number): number {
    let h = worldSeed ^ Math.imul(entityId, 0x517cc1b7)
    h = Math.imul(h ^ (h >>> 16), 0x45d9f3b)
    h = Math.imul(h ^ (h >>> 16), 0x45d9f3b)
    h = h ^ (h >>> 16)
    return h | 0
}
