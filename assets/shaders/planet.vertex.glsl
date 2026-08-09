attribute vec3 a_position;
attribute vec3 a_normal;

uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform mat3 u_normalMatrix;

varying vec3 v_worldNormal;

void main() {
    vec4 worldPosition = u_worldTrans * vec4(a_position, 1.0);
    v_worldNormal = normalize(u_normalMatrix * a_normal);
    gl_Position = u_projViewTrans * worldPosition;
}
