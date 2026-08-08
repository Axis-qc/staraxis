#if defined(diffuseTextureFlag) || defined(specularTextureFlag) || defined(emissiveTextureFlag)
#define textureFlag
#endif

#if defined(specularTextureFlag) || defined(specularColorFlag)
#define specularFlag
#endif

#if defined(specularFlag) || defined(fogFlag)
#define cameraPositionFlag
#endif

attribute vec3 a_position;
uniform mat4 u_projViewTrans;

#ifdef normalFlag
attribute vec3 a_normal;
uniform mat3 u_normalMatrix;
varying vec3 v_normal;
#endif // normalFlag

#ifdef normalTextureFlag
attribute vec4 a_tangent;
varying mat3 v_TBN;
#endif // normalTextureFlag

#ifdef textureFlag
attribute vec2 a_texCoord0;
#endif // textureFlag

#ifdef diffuseTextureFlag
uniform vec4 u_diffuseUVTransform;
varying vec2 v_diffuseUV;
#endif

#ifdef specularTextureFlag
uniform vec4 u_specularUVTransform;
varying vec2 v_specularUV;
#endif

#ifdef emissiveTextureFlag
uniform vec4 u_emissiveUVTransform;
varying vec2 v_emissiveUV;
#endif

uniform mat4 u_worldTrans;

#ifdef lightingFlag
varying vec3 v_worldPos;
#ifdef cameraPositionFlag
uniform vec4 u_cameraPosition;
#endif // cameraPositionFlag
#endif // lightingFlag

#ifdef fogFlag
varying float v_fog;
#endif // fogFlag

void main() {
	#ifdef diffuseTextureFlag
		v_diffuseUV = u_diffuseUVTransform.xy + a_texCoord0 * u_diffuseUVTransform.zw;
	#endif // diffuseTextureFlag

	#ifdef specularTextureFlag
		v_specularUV = u_specularUVTransform.xy + a_texCoord0 * u_specularUVTransform.zw;
	#endif // specularTextureFlag

	#ifdef emissiveTextureFlag
		v_emissiveUV = u_emissiveUVTransform.xy + a_texCoord0 * u_emissiveUVTransform.zw;
	#endif // emissiveTextureFlag

	vec4 pos = u_worldTrans * vec4(a_position, 1.0);
	gl_Position = u_projViewTrans * pos;

	#ifdef normalFlag
		vec3 normal = normalize(u_normalMatrix * a_normal);
		v_normal = normal;
	#endif // normalFlag

	// TBN 矩阵（世界空间）：由几何法线 + 切线构建，供 fragment 做 normal mapping
	// a_tangent.xyz 切线方向（Gram-Schmidt 相对法线正交化），a_tangent.w = bitangent 符号
	#ifdef normalTextureFlag
		vec3 N = normalize(u_normalMatrix * a_normal);
		vec3 T = normalize(u_normalMatrix * a_tangent.xyz - N * dot(N, u_normalMatrix * a_tangent.xyz));
		vec3 B = cross(N, T) * a_tangent.w;
		v_TBN = mat3(T, B, N);
	#endif // normalTextureFlag

	#ifdef lightingFlag
		v_worldPos = pos.xyz;
	#endif // lightingFlag

	#ifdef fogFlag
		vec3 flen = u_cameraPosition.xyz - pos.xyz;
		float fog = dot(flen, flen) * u_cameraPosition.w;
		v_fog = min(fog, 1.0);
	#endif // fogFlag
}
