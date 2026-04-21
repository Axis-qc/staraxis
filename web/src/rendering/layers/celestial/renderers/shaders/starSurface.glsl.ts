/**
 * @file starSurface.glsl.ts
 *
 * @description
 * 恒星程序化表面着色器源码喵。
 *
 * 说明：
 * - 顶点着色器只负责传递 UV（纹理坐标）与平面位置喵。
 * - 片元着色器在圆盘内合成表面噪声、热点、边缘辉光与外层日冕喵。
 */

export const STAR_SURFACE_VERTEX_SHADER = `
varying vec3 vObjectNormal;
varying vec3 vWorldNormal;
varying vec3 vWorldPosition;

void main() {
    vec4 worldPosition = modelMatrix * vec4(position, 1.0);
    vObjectNormal = normalize(position);
    vWorldNormal = normalize(mat3(modelMatrix) * normal);
    vWorldPosition = worldPosition.xyz;
    gl_Position = projectionMatrix * viewMatrix * worldPosition;
}
`

export const STAR_SURFACE_FRAGMENT_SHADER = `
precision highp float;

uniform float uTime;
uniform vec3 uBaseColor;
uniform vec3 uCoreColor;
uniform vec3 uHotColor;
uniform vec3 uRimColor;
uniform float uNoiseScale;
uniform float uNoiseSpeed;
uniform float uPulseSpeed;
uniform float uPulseAmplitude;
uniform float uGlowIntensity;
uniform float uSurfaceBanding;
uniform float uFlareStrength;
uniform float uOpacity;

varying vec3 vObjectNormal;
varying vec3 vWorldNormal;
varying vec3 vWorldPosition;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 191.9))) * 43758.5453123);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);

    float n000 = hash(i);
    float n100 = hash(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash(i + vec3(1.0, 1.0, 1.0));

    vec3 u = f * f * (3.0 - 2.0 * f);

    float nx00 = mix(n000, n100, u.x);
    float nx10 = mix(n010, n110, u.x);
    float nx01 = mix(n001, n101, u.x);
    float nx11 = mix(n011, n111, u.x);
    float nxy0 = mix(nx00, nx10, u.y);
    float nxy1 = mix(nx01, nx11, u.y);
    return mix(nxy0, nxy1, u.z);
}

float fbm(vec3 p) {
    float value = 0.0;
    float amplitude = 0.5;

    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p);
        p = p * 2.03 + vec3(13.1, 7.3, 5.7);
        amplitude *= 0.5;
    }

    return value;
}

float ridge(float value) {
    return 1.0 - abs(value * 2.0 - 1.0);
}

void main() {
    vec3 objectNormal = normalize(vObjectNormal);
    vec3 worldNormal = normalize(vWorldNormal);
    vec3 viewDir = normalize(cameraPosition - vWorldPosition);
    float mu = clamp(dot(worldNormal, viewDir), 0.0, 1.0);

    if (mu <= 0.001) {
        discard;
    }

    vec3 flowPos = objectNormal * uNoiseScale;
    float flowTime = uTime * uNoiseSpeed;
    vec3 driftA = vec3(flowTime * 0.33, -flowTime * 0.21, flowTime * 0.17);
    vec3 driftB = vec3(-flowTime * 0.16, flowTime * 0.27, -flowTime * 0.12);
    vec3 warp = vec3(
        fbm(flowPos.yzx * 0.72 + driftA + vec3(3.1, -2.4, 1.8)),
        fbm(flowPos.zxy * 0.68 + driftB + vec3(-4.8, 5.2, -3.3)),
        fbm(flowPos.xyz * 0.64 + driftA - driftB + vec3(2.5, -1.9, 4.6))
    ) - 0.5;
    warp *= 0.12 + 0.04 * mu;

    vec3 granulationPos = flowPos + warp;
    vec3 convectionPos = flowPos * 1.08 - warp * 0.35 + vec3(17.3, -9.4, 6.8);
    vec3 plumePos = flowPos * 0.74 + warp * 0.82 + vec3(-8.0, 12.0, -5.4);

    float granulation = fbm(granulationPos * 2.4 + driftA);
    float convection = fbm(convectionPos * 0.92 + driftB);
    float plumeNoise = fbm(plumePos + driftA * 0.55);
    float laneMask = ridge(granulation) * (0.55 + 0.45 * plumeNoise);
    float banding = sin((objectNormal.y + convection * 0.28) * 18.0 * max(uSurfaceBanding, 0.01));
    float pulse = 1.0 + sin(uTime * uPulseSpeed * 1.4) * uPulseAmplitude;

    float hotspot = smoothstep(0.46, 0.9, granulation * 0.52 + convection * 0.4 + banding * 0.08);
    float coolLanes = smoothstep(0.3, 0.85, laneMask) * 0.18;
    float coreGlow = pow(mu, 1.65);
    float limbDarkening = mix(0.72, 1.08, pow(mu, 0.55));
    float rim = pow(1.0 - mu, 2.6);
    float corona = pow(1.0 - mu, 3.8);
    float halo = pow(1.0 - mu, 1.8);

    vec3 photosphereColor = mix(uBaseColor, uHotColor, hotspot);
    photosphereColor = mix(photosphereColor, uCoreColor, coreGlow * 0.34);
    photosphereColor *= limbDarkening;
    photosphereColor *= pulse;
    photosphereColor -= uBaseColor * coolLanes;
    photosphereColor += uRimColor * rim * (0.16 + uGlowIntensity * 0.1);

    vec3 coronaColor = mix(uRimColor, uHotColor, 0.24);
    vec3 haloColor = mix(uRimColor, uCoreColor, 0.28);

    vec3 finalColor = photosphereColor;
    finalColor += coronaColor * corona * uGlowIntensity * 0.34;
    finalColor += haloColor * halo * uGlowIntensity * (0.08 + uFlareStrength * 0.12);

    float alpha = uOpacity * clamp(0.84 + rim * 0.22 + halo * 0.05, 0.0, 1.0);

    gl_FragColor = vec4(finalColor, clamp(alpha, 0.0, 1.0));
}
`
