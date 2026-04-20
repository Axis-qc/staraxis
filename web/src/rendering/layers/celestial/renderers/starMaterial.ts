/**
 * @file starMaterial.ts
 *
 * @description
 * 恒星程序化材质工厂喵。
 *
 * 作用：
 * - 创建 `ShaderMaterial`（Three.js 自定义着色材质）实例喵。
 * - 为渲染器提供统一的 uniform（着色器参数）写入入口喵。
 */

import * as THREE from 'three'
import type { IUniform } from 'three'
import type { StarRenderProfile } from './starProfile'
import { STAR_SURFACE_FRAGMENT_SHADER, STAR_SURFACE_VERTEX_SHADER } from './shaders/starSurface.glsl'

type StarMaterialUniformRecord = {
    uTime: IUniform<number>
    uBaseColor: IUniform<THREE.Color>
    uCoreColor: IUniform<THREE.Color>
    uHotColor: IUniform<THREE.Color>
    uRimColor: IUniform<THREE.Color>
    uNoiseScale: IUniform<number>
    uNoiseSpeed: IUniform<number>
    uPulseSpeed: IUniform<number>
    uPulseAmplitude: IUniform<number>
    uGlowIntensity: IUniform<number>
    uSurfaceBanding: IUniform<number>
    uFlareStrength: IUniform<number>
    uOpacity: IUniform<number>
}

export type ProceduralStarMaterial = THREE.ShaderMaterial & {
    uniforms: StarMaterialUniformRecord
}

/**
 * 创建一份新的程序化恒星材质喵。
 */
export function createProceduralStarMaterial(profile: StarRenderProfile): ProceduralStarMaterial {
    const material = new THREE.ShaderMaterial({
        uniforms: {
            uTime: { value: 0 },
            uBaseColor: { value: profile.baseColor.clone() },
            uCoreColor: { value: profile.coreColor.clone() },
            uHotColor: { value: profile.hotColor.clone() },
            uRimColor: { value: profile.rimColor.clone() },
            uNoiseScale: { value: profile.noiseScale },
            uNoiseSpeed: { value: profile.noiseSpeed },
            uPulseSpeed: { value: profile.pulseSpeed },
            uPulseAmplitude: { value: profile.pulseAmplitude },
            uGlowIntensity: { value: profile.glowIntensity },
            uSurfaceBanding: { value: profile.surfaceBanding },
            uFlareStrength: { value: profile.flareStrength },
            uOpacity: { value: 1 },
        },
        vertexShader: STAR_SURFACE_VERTEX_SHADER,
        fragmentShader: STAR_SURFACE_FRAGMENT_SHADER,
        transparent: true,
        depthWrite: false,
        depthTest: true,
        blending: THREE.NormalBlending,
    }) as ProceduralStarMaterial

    return material
}

/**
 * 将 profile（恒星渲染档案）应用到材质实例喵。
 */
export function applyStarProfileToMaterial(material: ProceduralStarMaterial, profile: StarRenderProfile): void {
    material.uniforms.uBaseColor.value.copy(profile.baseColor)
    material.uniforms.uCoreColor.value.copy(profile.coreColor)
    material.uniforms.uHotColor.value.copy(profile.hotColor)
    material.uniforms.uRimColor.value.copy(profile.rimColor)
    material.uniforms.uNoiseScale.value = profile.noiseScale
    material.uniforms.uNoiseSpeed.value = profile.noiseSpeed
    material.uniforms.uPulseSpeed.value = profile.pulseSpeed
    material.uniforms.uPulseAmplitude.value = profile.pulseAmplitude
    material.uniforms.uGlowIntensity.value = profile.glowIntensity
    material.uniforms.uSurfaceBanding.value = profile.surfaceBanding
    material.uniforms.uFlareStrength.value = profile.flareStrength
}

/**
 * 更新时间 uniform（着色器时间参数）喵。
 */
export function updateStarMaterialTime(material: ProceduralStarMaterial, elapsedSeconds: number): void {
    material.uniforms.uTime.value = elapsedSeconds
}

/**
 * 更新透明度 uniform（着色器透明度参数）喵。
 */
export function updateStarMaterialOpacity(material: ProceduralStarMaterial, opacity: number): void {
    material.uniforms.uOpacity.value = opacity
}
