/**
 * @file starProfile.ts
 *
 * @description
 * 恒星程序化渲染配置映射喵。
 *
 * 作用：
 * - 根据 `starTypeId`（恒星类型 ID）与 `temperatureK`（恒星表面温度）生成稳定的渲染参数喵。
 * - 将类型特征与温度梯度拆分，避免在渲染器里散落硬编码喵。
 */

import * as THREE from 'three'
import type { StarDetails } from '../../../../net/snapshotWs'

/**
 * StarRenderProfile（恒星渲染档案）。
 *
 * 说明：
 * - `baseColor`（基础色）负责恒星主体颜色喵。
 * - `coreColor`（核心色）负责恒星内核高亮喵。
 * - `hotColor`（热点色）负责局部炽热点颜色喵。
 * - `rimColor`（边缘辉光色）负责外沿与日冕过渡喵。
 * - 其余数值负责控制颗粒、表面流动、脉动和日冕活跃度喵。
 */
export type StarRenderProfile = {
    baseColor: THREE.Color
    coreColor: THREE.Color
    hotColor: THREE.Color
    rimColor: THREE.Color
    noiseScale: number
    noiseSpeed: number
    pulseSpeed: number
    pulseAmplitude: number
    glowIntensity: number
    surfaceBanding: number
    flareStrength: number
}

type StarProfileSeed = Omit<StarRenderProfile, 'baseColor' | 'coreColor' | 'hotColor' | 'rimColor'>

const DEFAULT_PROFILE_SEED: StarProfileSeed = {
    noiseScale: 3.2,
    noiseSpeed: 0.04,
    pulseSpeed: 0.35,
    pulseAmplitude: 0.026,
    glowIntensity: 0.85,
    surfaceBanding: 0.18,
    flareStrength: 0.28,
}

const STAR_PROFILE_SEEDS: Record<string, Partial<StarProfileSeed>> = {
    G_MAIN_SEQUENCE: {
        noiseScale: 3.1,
        noiseSpeed: 0.034,
        pulseSpeed: 0.32,
        pulseAmplitude: 0.022,
        glowIntensity: 0.9,
        surfaceBanding: 0.16,
        flareStrength: 0.24,
    },
    K_MAIN_SEQUENCE: {
        noiseScale: 3.5,
        noiseSpeed: 0.03,
        pulseSpeed: 0.28,
        pulseAmplitude: 0.02,
        glowIntensity: 0.92,
        surfaceBanding: 0.2,
        flareStrength: 0.26,
    },
    M_MAIN_SEQUENCE: {
        noiseScale: 3.9,
        noiseSpeed: 0.024,
        pulseSpeed: 0.22,
        pulseAmplitude: 0.03,
        glowIntensity: 1.0,
        surfaceBanding: 0.26,
        flareStrength: 0.3,
    },
    A_MAIN_SEQUENCE: {
        noiseScale: 2.7,
        noiseSpeed: 0.043,
        pulseSpeed: 0.4,
        pulseAmplitude: 0.022,
        glowIntensity: 0.82,
        surfaceBanding: 0.14,
        flareStrength: 0.23,
    },
    B_MAIN_SEQUENCE: {
        noiseScale: 2.4,
        noiseSpeed: 0.05,
        pulseSpeed: 0.5,
        pulseAmplitude: 0.028,
        glowIntensity: 1.08,
        surfaceBanding: 0.1,
        flareStrength: 0.38,
    },
    RED_GIANT: {
        noiseScale: 4.6,
        noiseSpeed: 0.02,
        pulseSpeed: 0.18,
        pulseAmplitude: 0.04,
        glowIntensity: 1.12,
        surfaceBanding: 0.34,
        flareStrength: 0.34,
    },
    BLUE_GIANT: {
        noiseScale: 2.2,
        noiseSpeed: 0.056,
        pulseSpeed: 0.62,
        pulseAmplitude: 0.034,
        glowIntensity: 1.18,
        surfaceBanding: 0.08,
        flareStrength: 0.44,
    },
}

/**
 * 将表面温度映射为近似黑体颜色喵。
 */
function getTemperatureColor(temperatureK: number): THREE.Color {
    if (temperatureK >= 30000) return new THREE.Color(0x9bb0ff)
    if (temperatureK >= 10000) return new THREE.Color(0xa6c5ff)
    if (temperatureK >= 7500) return new THREE.Color(0xcad7ff)
    if (temperatureK >= 6000) return new THREE.Color(0xf8f7ff)
    if (temperatureK >= 5200) return new THREE.Color(0xfff4ea)
    if (temperatureK >= 3700) return new THREE.Color(0xffd2a1)
    if (temperatureK >= 2400) return new THREE.Color(0xffb347)
    return new THREE.Color(0xff6b3d)
}

/**
 * 基于恒星类型做颜色偏置喵。
 */
function getTypeAccentColor(starTypeId: string): THREE.Color {
    switch (starTypeId) {
        case 'G_MAIN_SEQUENCE':
            return new THREE.Color(0xffe07a)
        case 'K_MAIN_SEQUENCE':
            return new THREE.Color(0xffb85e)
        case 'M_MAIN_SEQUENCE':
            return new THREE.Color(0xff7e4d)
        case 'A_MAIN_SEQUENCE':
            return new THREE.Color(0xe6f0ff)
        case 'B_MAIN_SEQUENCE':
            return new THREE.Color(0x9fd8ff)
        case 'RED_GIANT':
            return new THREE.Color(0xff8457)
        case 'BLUE_GIANT':
            return new THREE.Color(0x7fd0ff)
        default:
            return new THREE.Color(0xffd27a)
    }
}

/**
 * 合并温度色和类型色，形成主体与高温热点色喵。
 */
function buildProfileColors(starTypeId: string, temperatureK: number): Pick<StarRenderProfile, 'baseColor' | 'coreColor' | 'hotColor' | 'rimColor'> {
    const temperatureColor = getTemperatureColor(temperatureK)
    const typeAccent = getTypeAccentColor(starTypeId)

    const baseColor = temperatureColor.clone().lerp(typeAccent, 0.28)
    const coreColor = temperatureColor.clone().lerp(new THREE.Color(0xffffff), 0.68)
    const hotColor = temperatureColor.clone().lerp(new THREE.Color(0xffffff), 0.42)
    const rimColor = temperatureColor.clone().lerp(typeAccent, 0.55).lerp(new THREE.Color(0xffffff), 0.18)

    return {
        baseColor,
        coreColor,
        hotColor,
        rimColor,
    }
}

/**
 * 根据类型和物理量生成程序化渲染档案喵。
 */
export function getStarProfile(details: Pick<StarDetails, 'starTypeId' | 'temperatureK' | 'massSolar' | 'radiusGU'>): StarRenderProfile {
    const seed = {
        ...DEFAULT_PROFILE_SEED,
        ...(STAR_PROFILE_SEEDS[details.starTypeId] ?? {}),
    }

    const colors = buildProfileColors(details.starTypeId, details.temperatureK)
    const massFactor = THREE.MathUtils.clamp(Math.log10(Math.max(details.massSolar, 0.08)) + 1, 0.5, 2.2)
    const radiusFactor = THREE.MathUtils.clamp(Math.log10(Math.max(details.radiusGU, 1)) / 6, 0.15, 1.2)

    return {
        ...colors,
        noiseScale: seed.noiseScale + radiusFactor * 0.9,
        noiseSpeed: seed.noiseSpeed + massFactor * 0.0018,
        pulseSpeed: seed.pulseSpeed + massFactor * 0.04,
        pulseAmplitude: THREE.MathUtils.clamp(seed.pulseAmplitude + radiusFactor * 0.006, 0.015, 0.05),
        glowIntensity: seed.glowIntensity + massFactor * 0.08 + radiusFactor * 0.06,
        surfaceBanding: THREE.MathUtils.clamp(seed.surfaceBanding + radiusFactor * 0.05, 0.06, 0.42),
        flareStrength: THREE.MathUtils.clamp(seed.flareStrength + massFactor * 0.024 + radiusFactor * 0.024, 0.18, 0.54),
    }
}
