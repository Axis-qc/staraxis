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
varying vec2 vUv;
varying vec2 vPlanePosition;

void main() {
    vUv = uv;
    vPlanePosition = position.xy;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
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

varying vec2 vUv;
varying vec2 vPlanePosition;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(a, b, u.x) +
        (c - a) * u.y * (1.0 - u.x) +
        (d - b) * u.x * u.y;
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;

    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p);
        p = p * 2.03 + vec2(13.1, 7.3);
        amplitude *= 0.5;
    }

    return value;
}

float ridge(float value) {
    return 1.0 - abs(value * 2.0 - 1.0);
}

void main() {
    vec2 centeredUv = vUv * 2.0 - 1.0;
    float radius = length(centeredUv);
    float mu = clamp(1.0 - radius * radius, 0.0, 1.0);

    float discMask = 1.0 - smoothstep(0.82, 1.0, radius);
    float coronaMask = 1.0 - smoothstep(0.72, 1.2, radius);

    if (discMask <= 0.001 && coronaMask <= 0.001) {
        discard;
    }

    vec2 flowUv = centeredUv * uNoiseScale;
    vec2 driftA = vec2(uTime * uNoiseSpeed * 0.26, -uTime * uNoiseSpeed * 0.17);
    vec2 driftB = vec2(-uTime * uNoiseSpeed * 0.16, uTime * uNoiseSpeed * 0.11);
    vec2 warp = vec2(
        fbm(flowUv * 0.55 + driftA + vec2(3.1, -2.4)),
        fbm(flowUv * 0.55 + driftB + vec2(-4.8, 5.2))
    ) - 0.5;
    warp *= 0.08 + 0.03 * mu;

    vec2 granulationUv = flowUv + warp;
    vec2 convectionUv = flowUv * 1.06 - warp * 0.45 + vec2(17.3, -9.4);
    vec2 plumeUv = flowUv * 0.7 + warp * 0.9 + vec2(-8.0, 12.0);

    float granulation = fbm(granulationUv * 2.7 + driftA);
    float convection = fbm(convectionUv * 0.95 + driftB);
    float plumeNoise = fbm(plumeUv + driftA * 0.6);
    float laneMask = ridge(granulation) * (0.55 + 0.45 * plumeNoise);
    float banding = sin((centeredUv.y + convection * 0.3) * 18.0 * max(uSurfaceBanding, 0.01));
    float pulse = 1.0 + sin(uTime * uPulseSpeed * 1.4) * uPulseAmplitude;

    float hotspot = smoothstep(0.46, 0.9, granulation * 0.52 + convection * 0.4 + banding * 0.08);
    float coolLanes = smoothstep(0.3, 0.85, laneMask) * 0.18;
    float coreGlow = pow(mu, 1.65);
    float limbDarkening = mix(0.72, 1.08, pow(mu, 0.55));
    float rim = smoothstep(0.44, 0.98, radius) * discMask;

    float coronaShell = 1.0 - smoothstep(0.76, 1.18, radius);
    float coronaSoft = pow(max(0.0, 1.06 - radius), 2.6);
    float haloFalloff = pow(max(0.0, 1.24 - radius), 1.75);
    float corona = coronaSoft * coronaShell;
    float halo = haloFalloff * coronaShell;

    vec3 photosphereColor = mix(uBaseColor, uHotColor, hotspot);
    photosphereColor = mix(photosphereColor, uCoreColor, coreGlow * 0.34);
    photosphereColor *= limbDarkening;
    photosphereColor *= pulse;
    photosphereColor -= uBaseColor * coolLanes;
    photosphereColor += uRimColor * rim * (0.18 + uGlowIntensity * 0.08);

    vec3 coronaColor = mix(uRimColor, uHotColor, 0.22);
    vec3 haloColor = mix(uRimColor, uCoreColor, 0.25);

    vec3 finalColor = photosphereColor * discMask;
    finalColor += coronaColor * corona * uGlowIntensity * 0.42 * coronaMask;
    finalColor += haloColor * halo * uGlowIntensity * (0.26 + uFlareStrength * 0.3) * coronaMask;

    float alpha = discMask * uOpacity;
    alpha += corona * 0.14 * uGlowIntensity * coronaMask * uOpacity;
    alpha += halo * (0.1 + uFlareStrength * 0.12) * uGlowIntensity * coronaMask * uOpacity;

    gl_FragColor = vec4(finalColor, clamp(alpha, 0.0, 1.0));
}
`
