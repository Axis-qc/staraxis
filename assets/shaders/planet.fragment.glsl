#ifdef GL_ES
#define LOWP lowp
#define MED mediump
#define HIGH highp
precision mediump float;
#else
#define MED
#define LOWP
#define HIGH
#endif

#ifdef diffuseColorFlag
uniform vec4 u_diffuseColor;
#else
const vec4 u_diffuseColor = vec4(1.0);
#endif

#ifdef emissiveColorFlag
uniform vec4 u_emissiveColor;
#else
const vec4 u_emissiveColor = vec4(0.0);
#endif

uniform vec3 u_planetLightDirection;
uniform vec4 u_planetLightColor;
uniform float u_planetAmbientIntensity;

varying vec3 v_worldNormal;

void main() {
    vec3 worldNormal = normalize(v_worldNormal);
    // PlanetLightAttribute.direction 是恒星到行星的传播方向，取反得到行星到恒星。
    vec3 toStar = normalize(-u_planetLightDirection);
    float diffuseFactor = max(dot(worldNormal, toStar), 0.0);
    vec3 lighting = vec3(u_planetAmbientIntensity) + u_planetLightColor.rgb * diffuseFactor;

    gl_FragColor = vec4(u_diffuseColor.rgb * lighting + u_emissiveColor.rgb,
            u_diffuseColor.a);
}
