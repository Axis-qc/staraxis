/**
 * @file planetProfile.ts
 *
 * @description
 * 行星程序化渲染配置映射喵。
 *
 * 作用：
 * - 根据 `planetTypeId`（行星类型 ID）生成稳定的渲染参数喵。
 * - 为纹理生成器提供颜色、噪声、特征类型等配置喵。
 * - 与 `starProfile.ts` 对齐，保持设计一致性喵。
 */

import type { PlanetDetails } from '../../../../net/snapshotWs'

/**
 * 行星特征类型，决定纹理的主要视觉模式喵。
 */
export type PlanetFeatureType =
    | 'continents'     // 类地：大陆 + 海洋
    | 'bands'          // 气态巨行星：水平条纹
    | 'craters'        // 岩石/荒芜：撞击坑 + 荒漠
    | 'lava_flows'     // 熔岩：裂隙 + 岩浆
    | 'ice_caps'       // 冰雪/冰巨：极地冰盖 + 冰面
    | 'ocean_surface'  // 海洋：几乎全水面

/**
 * PlanetRenderProfile（行星渲染档案）喵。
 *
 * 说明：
 * - `baseColor`（主色调）负责行星主体颜色喵。
 * - `accentColor`（强调色）负责海洋/条纹/裂隙颜色喵。
 * - `detailColor`（细节色）负责山脉/极地/云层颜色喵。
 * - `atmosphereColor`（大气色）负责边缘大气散射颜色喵。
 * - 噪声参数控制地表特征的尺度与复杂度喵。
 * - `featureType` 决定纹理生成的主要视觉模式喵。
 */
export type PlanetRenderProfile = {
    // ─── 色板 ───
    baseColor: [number, number, number]       // RGB [0-255]
    accentColor: [number, number, number]
    detailColor: [number, number, number]
    atmosphereColor: [number, number, number]

    // ─── 噪声参数 ───
    noiseScale: number       // 主噪声频率（值越大 → 特征越小）
    noiseOctaves: number     // 噪声叠加层数
    noisePersistence: number // 层间振幅衰减（0-1）
    noiseLacunarity: number  // 层间频率倍增

    // ─── 特征 ───
    featureType: PlanetFeatureType
    featureParams: Record<string, number>
}

// ─── 类型 Profile 种子 ─────────────────────────────────────────────────

const DEFAULT_PROFILE: PlanetRenderProfile = {
    baseColor: [120, 120, 120],
    accentColor: [80, 80, 80],
    detailColor: [160, 160, 160],
    atmosphereColor: [180, 200, 220],
    noiseScale: 3.0,
    noiseOctaves: 5,
    noisePersistence: 0.5,
    noiseLacunarity: 2.0,
    featureType: 'craters',
    featureParams: {},
}

/**
 * 各行星类型的 Profile 配置喵。
 * 颜色为 RGB [0-255] 喵。
 */
const PLANET_PROFILE_SEEDS: Record<string, Partial<PlanetRenderProfile>> = {
    TERRESTRIAL: {
        baseColor: [74, 144, 217],         // 海洋蓝
        accentColor: [74, 140, 63],        // 大陆绿
        detailColor: [200, 210, 220],      // 极地冰白
        atmosphereColor: [140, 180, 230],  // 大气蓝
        noiseScale: 2.5,
        noiseOctaves: 6,
        noisePersistence: 0.52,
        noiseLacunarity: 2.1,
        featureType: 'continents',
        featureParams: {
            seaLevel: 0.45,       // 海平面阈值
            polarWidth: 0.12,     // 极地冰盖宽度（0-1，从极点算起）
            cloudDensity: 0.15,   // 云层密度
        },
    },
    ROCKY_BARREN: {
        baseColor: [139, 115, 85],         // 岩石棕
        accentColor: [90, 90, 90],         // 暗灰
        detailColor: [170, 155, 130],      // 浅岩色
        atmosphereColor: [160, 140, 120],  // 微弱大气
        noiseScale: 4.0,
        noiseOctaves: 5,
        noisePersistence: 0.48,
        noiseLacunarity: 2.2,
        featureType: 'craters',
        featureParams: {
            craterDensity: 0.3,   // 撞击坑密度
            craterDepth: 0.4,     // 撞击坑深度
            dustRatio: 0.4,       // 荒漠比例
        },
    },
    GAS_GIANT: {
        baseColor: [212, 160, 84],         // 气态金
        accentColor: [139, 94, 60],        // 深棕
        detailColor: [230, 200, 140],      // 亮金
        atmosphereColor: [200, 170, 120],  // 大气金
        noiseScale: 1.8,
        noiseOctaves: 4,
        noisePersistence: 0.45,
        noiseLacunarity: 2.0,
        featureType: 'bands',
        featureParams: {
            bandFrequency: 12.0,  // 条纹频率
            bandWarp: 0.3,        // 条纹扭曲强度
            stormProbability: 0.4, // 大红斑概率（基于 seed 决定有无）
            stormSize: 0.08,      // 大红斑相对大小
        },
    },
    ICE_GIANT: {
        baseColor: [126, 200, 227],        // 冰蓝
        accentColor: [58, 124, 165],       // 深蓝
        detailColor: [180, 220, 240],      // 浅冰蓝
        atmosphereColor: [140, 200, 230],  // 大气蓝
        noiseScale: 2.0,
        noiseOctaves: 4,
        noisePersistence: 0.42,
        noiseLacunarity: 2.0,
        featureType: 'bands',
        featureParams: {
            bandFrequency: 8.0,
            bandWarp: 0.2,
            stormProbability: 0.2,
            stormSize: 0.05,
        },
    },
    OCEAN_WORLD: {
        baseColor: [26, 82, 118],          // 深海蓝
        accentColor: [93, 173, 226],       // 浅蓝
        detailColor: [180, 220, 240],      // 浪花白
        atmosphereColor: [120, 180, 230],  // 大气蓝
        noiseScale: 3.0,
        noiseOctaves: 5,
        noisePersistence: 0.45,
        noiseLacunarity: 2.0,
        featureType: 'ocean_surface',
        featureParams: {
            islandRatio: 0.05,    // 岛屿比例
            waveScale: 6.0,       // 波纹频率
            deepRatio: 0.6,       // 深海比例
        },
    },
    LAVA_WORLD: {
        baseColor: [45, 45, 45],           // 暗岩
        accentColor: [231, 76, 60],        // 岩浆红橙
        detailColor: [241, 196, 15],       // 亮岩浆黄
        atmosphereColor: [200, 80, 40],    // 火山大气
        noiseScale: 3.5,
        noiseOctaves: 5,
        noisePersistence: 0.55,
        noiseLacunarity: 2.2,
        featureType: 'lava_flows',
        featureParams: {
            lavaRatio: 0.35,      // 岩浆区域比例
            glowIntensity: 0.8,   // 岩浆发光强度
            crackScale: 8.0,      // 裂隙频率
        },
    },
    FROZEN_WORLD: {
        baseColor: [213, 232, 240],        // 冰白
        accentColor: [168, 216, 234],      // 深冰蓝
        detailColor: [240, 248, 255],      // 亮冰白
        atmosphereColor: [180, 210, 240],  // 冰晶大气
        noiseScale: 3.2,
        noiseOctaves: 5,
        noisePersistence: 0.48,
        noiseLacunarity: 2.1,
        featureType: 'ice_caps',
        featureParams: {
            iceCapWidth: 0.25,    // 极地冰盖宽度
            crackDensity: 0.3,    // 冰裂纹密度
            frostNoise: 0.2,      // 霜冻噪声强度
        },
    },
}

// ─── 公开 API ──────────────────────────────────────────────────────────

/**
 * 根据行星详情获取渲染配置喵。
 * 未匹配到 typeId 时返回默认 profile 喵。
 */
export function getPlanetProfile(details: PlanetDetails): PlanetRenderProfile {
    const seed = PLANET_PROFILE_SEEDS[details.planetTypeId]
    if (!seed) {
        return { ...DEFAULT_PROFILE }
    }
    return { ...DEFAULT_PROFILE, ...seed }
}

/**
 * 根据 typeId 字符串获取渲染配置喵（不需要完整 PlanetDetails 时使用）喵。
 */
export function getPlanetProfileByTypeId(typeId: string): PlanetRenderProfile {
    const seed = PLANET_PROFILE_SEEDS[typeId]
    if (!seed) {
        return { ...DEFAULT_PROFILE }
    }
    return { ...DEFAULT_PROFILE, ...seed }
}
