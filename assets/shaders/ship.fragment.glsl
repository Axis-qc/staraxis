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

#if defined(specularTextureFlag) || defined(specularColorFlag)
#define specularFlag
#endif

#if defined(specularFlag) || defined(fogFlag)
#define cameraPositionFlag
#endif

#ifdef normalFlag
varying vec3 v_normal;
#endif //normalFlag

#ifdef normalTextureFlag
varying mat3 v_TBN;
#endif //normalTextureFlag

#if defined(colorFlag)
varying vec4 v_color;
#endif

#ifdef blendedFlag
varying float v_opacity;
#ifdef alphaTestFlag
varying float v_alphaTest;
#endif //alphaTestFlag
#endif //blendedFlag

#if defined(diffuseTextureFlag) || defined(specularTextureFlag) || defined(emissiveTextureFlag)
#define textureFlag
#endif

#ifdef diffuseTextureFlag
varying MED vec2 v_diffuseUV;
#endif

#ifdef specularTextureFlag
varying MED vec2 v_specularUV;
#endif

#ifdef emissiveTextureFlag
varying MED vec2 v_emissiveUV;
#endif

#ifdef diffuseColorFlag
uniform vec4 u_diffuseColor;
#endif

#ifdef diffuseTextureFlag
uniform sampler2D u_diffuseTexture;
#endif

#ifdef specularColorFlag
uniform vec4 u_specularColor;
#endif

#ifdef specularTextureFlag
uniform sampler2D u_specularTexture;
#endif

#ifdef normalTextureFlag
uniform sampler2D u_normalTexture;
#endif

#ifdef emissiveColorFlag
uniform vec4 u_emissiveColor;
#endif

#ifdef emissiveTextureFlag
uniform sampler2D u_emissiveTexture;
#endif

#ifdef playerColorFlag
uniform vec3 u_playerColor;
uniform float u_baseEmissive;
uniform float u_lightIntensity;
#endif

#ifdef lightingFlag
varying vec3 v_worldPos;

#ifdef cameraPositionFlag
uniform vec4 u_cameraPosition;
#endif // cameraPositionFlag

#if numDirectionalLights > 0
struct DirectionalLight {
	vec3 color;
	vec3 direction;
};
uniform DirectionalLight u_dirLights[numDirectionalLights];
#endif // numDirectionalLights

#if defined(ambientCubemapFlag)
uniform vec3 u_ambientCubemap[6];
#endif // ambientCubemapFlag
#endif // lightingFlag

#ifdef fogFlag
uniform vec4 u_fogColor;
varying float v_fog;
#endif // fogFlag

void main() {
	// 法线：normal mapping 时采样切线空间法线经 v_TBN 变换到世界空间，覆盖几何法线
	#ifdef normalTextureFlag
		vec3 n = texture2D(u_normalTexture, v_diffuseUV).rgb * 2.0 - 1.0;
		vec3 normal = normalize(v_TBN * n);
	#elif defined(normalFlag)
		vec3 normal = normalize(v_normal);
	#else
		vec3 normal = vec3(0.0, 1.0, 0.0);
	#endif

	#if defined(diffuseTextureFlag) && defined(diffuseColorFlag) && defined(colorFlag)
		vec4 diffuse = texture2D(u_diffuseTexture, v_diffuseUV) * u_diffuseColor * v_color;
	#elif defined(diffuseTextureFlag) && defined(diffuseColorFlag)
		vec4 diffuse = texture2D(u_diffuseTexture, v_diffuseUV) * u_diffuseColor;
	#elif defined(diffuseTextureFlag) && defined(colorFlag)
		vec4 diffuse = texture2D(u_diffuseTexture, v_diffuseUV) * v_color;
	#elif defined(diffuseTextureFlag)
		vec4 diffuse = texture2D(u_diffuseTexture, v_diffuseUV);
	#elif defined(diffuseColorFlag) && defined(colorFlag)
		vec4 diffuse = u_diffuseColor * v_color;
	#elif defined(diffuseColorFlag)
		vec4 diffuse = u_diffuseColor;
	#elif defined(colorFlag)
		vec4 diffuse = v_color;
	#else
		vec4 diffuse = vec4(1.0);
	#endif

	#if defined(emissiveTextureFlag) && defined(emissiveColorFlag)
		vec4 emissive = texture2D(u_emissiveTexture, v_emissiveUV) * u_emissiveColor;
	#elif defined(emissiveTextureFlag)
		vec4 emissive = texture2D(u_emissiveTexture, v_emissiveUV);
	#elif defined(emissiveColorFlag)
		vec4 emissive = u_emissiveColor;
	#else
		vec4 emissive = vec4(0.0);
	#endif

	// 逐像素光照（fragment 内计算，使 normal mapping 生效）：
	// 环境光（ambient cubemap 球谐近似）+ 方向光漫反射 + Blinn-Phong 高光
	vec3 lightDiffuse = vec3(0.0);
	vec3 lightSpecular = vec3(0.0);
	#ifdef lightingFlag
		#if defined(ambientCubemapFlag)
			vec3 squaredNormal = normal * normal;
			vec3 isPositive = step(0.0, normal);
			lightDiffuse += squaredNormal.x * mix(u_ambientCubemap[0], u_ambientCubemap[1], isPositive.x)
				+ squaredNormal.y * mix(u_ambientCubemap[2], u_ambientCubemap[3], isPositive.y)
				+ squaredNormal.z * mix(u_ambientCubemap[4], u_ambientCubemap[5], isPositive.z);
		#endif // ambientCubemapFlag

		#ifdef cameraPositionFlag
			vec3 viewVec = normalize(u_cameraPosition.xyz - v_worldPos);
		#endif // cameraPositionFlag

		#if numDirectionalLights > 0
			for (int i = 0; i < numDirectionalLights; i++) {
				vec3 lightDir = -u_dirLights[i].direction;
				float NdotL = clamp(dot(normal, lightDir), 0.0, 1.0);
				vec3 value = u_dirLights[i].color * NdotL;
				lightDiffuse += value;
				#ifdef specularFlag
					float halfDotView = max(0.0, dot(normal, normalize(lightDir + viewVec)));
					lightSpecular += value * pow(halfDotView, 20.0);
				#endif // specularFlag
			}
		#endif // numDirectionalLights
	#endif // lightingFlag

	// 镜面高光颜色（specular 贴图提供，无则用光照值本身）
	#if defined(specularTextureFlag) && defined(specularColorFlag)
		vec3 specular = texture2D(u_specularTexture, v_specularUV).rgb * u_specularColor.rgb * lightSpecular;
	#elif defined(specularTextureFlag)
		vec3 specular = texture2D(u_specularTexture, v_specularUV).rgb * lightSpecular;
	#elif defined(specularColorFlag)
		vec3 specular = u_specularColor.rgb * lightSpecular;
	#else
		vec3 specular = lightSpecular;
	#endif

	// 玩家颜色自发光：normal 贴图蓝色通道作为发光掩码，混合玩家颜色
	// （Stellaris 同款：normal 贴图 B 通道兼职自发光掩码，实现不同玩家不同灯光）
	// 两部分独立：
	//   u_baseEmissive：diffuse 底色基础自发光（防止背光面全黑）
	//   u_lightIntensity × mask：玩家颜色灯光亮度（normal 蓝通道掩码）
	#ifdef playerColorFlag
		#ifdef normalTextureFlag
			float glowMask = texture2D(u_normalTexture, v_diffuseUV).b;
		#else
			float glowMask = 0.0;
		#endif
		emissive.rgb += diffuse.rgb * u_baseEmissive;
		emissive.rgb += u_playerColor * glowMask * u_lightIntensity;
	#endif

	#if (!defined(lightingFlag))
		gl_FragColor.rgb = diffuse.rgb + emissive.rgb;
	#else
		gl_FragColor.rgb = (diffuse.rgb * lightDiffuse) + specular + emissive.rgb;
	#endif // lightingFlag

	// 法线可视化调试（仅模型测试场景启用 debugNormalFlag 时生效）：
	// 把逐像素法线直接映射为颜色（n * 0.5 + 0.5），直观验证 normal mapping 效果。
	// normal mapping 生效时表面显示彩色法线图（细节处有红绿变化）；
	// 不生效时只有光滑的几何法线渐变。
	#ifdef debugNormalFlag
		gl_FragColor.rgb = normal * 0.5 + 0.5;
		gl_FragColor.a = 1.0;
	#endif

	#ifdef fogFlag
		gl_FragColor.rgb = mix(gl_FragColor.rgb, u_fogColor.rgb, v_fog);
	#endif // end fogFlag

	#ifdef blendedFlag
		gl_FragColor.a = diffuse.a * v_opacity;
		#ifdef alphaTestFlag
			if (gl_FragColor.a <= v_alphaTest)
				discard;
		#endif
	#else
		gl_FragColor.a = 1.0;
	#endif
}
