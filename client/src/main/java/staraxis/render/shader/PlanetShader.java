package staraxis.render.shader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.Attributes;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;

/**
 * PlanetShader（行星世界空间光照着色器）。
 *
 * 使用世界空间法线和 PlanetLightAttribute，避免通用方向光环境或相机相关
 * 计算改变行星的明暗方向。相机只参与顶点投影，不参与光照计算。
 */
public final class PlanetShader extends DefaultShader {

    /** 行星顶点着色器资产路径。 */
    private static final String VERTEX_SHADER_PATH = "shaders/planet.vertex.glsl";

    /** 行星片段着色器资产路径。 */
    private static final String FRAGMENT_SHADER_PATH = "shaders/planet.fragment.glsl";

    /** 低强度环境光，防止背光面完全消失。 */
    public static final float AMBIENT_INTENSITY = 0.12f;

    /** 世界空间恒星光方向 uniform setter。 */
    private static final BaseShader.Setter lightDirectionSetter = new BaseShader.LocalSetter() {
        @Override
        public void set(BaseShader shader, int inputId, Renderable renderable, Attributes combinedAttributes) {
            PlanetLightAttribute light = (PlanetLightAttribute)
                    combinedAttributes.get(PlanetLightAttribute.Type);
            if (light != null) {
                boolean written = shader.set(inputId, light.direction);
                logUniformWrite("u_planetLightDirection", written, light, true);
            }
        }
    };

    /** 恒星光色 uniform setter。 */
    private static final BaseShader.Setter lightColorSetter = new BaseShader.LocalSetter() {
        @Override
        public void set(BaseShader shader, int inputId, Renderable renderable, Attributes combinedAttributes) {
            PlanetLightAttribute light = (PlanetLightAttribute)
                    combinedAttributes.get(PlanetLightAttribute.Type);
            if (light != null) {
                boolean written = shader.set(inputId, light.color);
                logUniformWrite("u_planetLightColor", written, light, false);
            }
        }
    };

    /** 环境光强度 uniform setter。 */
    private static final BaseShader.Setter ambientIntensitySetter = new BaseShader.GlobalSetter() {
        @Override
        public void set(BaseShader shader, int inputId, Renderable renderable, Attributes combinedAttributes) {
            shader.set(inputId, AMBIENT_INTENSITY);
        }
    };

    /** 同一 uniform 的诊断日志最小间隔，避免每个行星每帧刷屏。 */
    private static final long DIAGNOSTIC_LOG_INTERVAL_NANOS = 1_000_000_000L;

    /** 方向 uniform 最近一次诊断日志时间。 */
    private static long lastDirectionDiagnosticNanos;

    /** 光色 uniform 最近一次诊断日志时间。 */
    private static long lastColorDiagnosticNanos;

    /** 世界空间恒星光方向 uniform ID。 */
    private final int uPlanetLightDirection;

    /** 恒星光色 uniform ID。 */
    private final int uPlanetLightColor;

    /** 环境光强度 uniform ID。 */
    private final int uPlanetAmbientIntensity;

    /** 记录 uniform 写入结果，直接暴露 shader 是否拿到有效 location。 */
    private static void logUniformWrite(String uniformName, boolean written,
                                        PlanetLightAttribute light, boolean direction) {
        if (Gdx.app == null) return;
        long now = System.nanoTime();
        long last = direction ? lastDirectionDiagnosticNanos : lastColorDiagnosticNanos;
        if (now - last < DIAGNOSTIC_LOG_INTERVAL_NANOS) return;
        if (direction) {
            lastDirectionDiagnosticNanos = now;
        } else {
            lastColorDiagnosticNanos = now;
        }
        Gdx.app.log("PlanetShader", uniformName + " written=" + written
                + " direction=(" + light.direction.x + "," + light.direction.y + ","
                + light.direction.z + ") color=(" + light.color.r + "," + light.color.g + ","
                + light.color.b + "," + light.color.a + ")");
    }

    public PlanetShader(Renderable renderable, Config sourceConfig) {
        this(renderable, planetConfig(sourceConfig), true);
    }

    private PlanetShader(Renderable renderable, Config config, boolean ignored) {
        super(renderable, config, createPlanetPrefix(renderable, config),
                Gdx.files.internal(VERTEX_SHADER_PATH).readString(),
                Gdx.files.internal(FRAGMENT_SHADER_PATH).readString());
        uPlanetLightDirection = register(new Uniform("u_planetLightDirection"), lightDirectionSetter);
        uPlanetLightColor = register(new Uniform("u_planetLightColor"), lightColorSetter);
        uPlanetAmbientIntensity = register(new Uniform("u_planetAmbientIntensity"), ambientIntensitySetter);
    }

    /** 为行星关闭通用方向光，避免共享 Environment 参与行星明暗计算。 */
    private static Config planetConfig(Config source) {
        Config config = new Config();
        config.numDirectionalLights = 0;
        config.numPointLights = 0;
        config.numSpotLights = 0;
        if (source != null) {
            config.numBones = source.numBones;
            config.numBoneWeights = source.numBoneWeights;
            config.ignoreUnimplemented = source.ignoreUnimplemented;
            config.defaultCullFace = source.defaultCullFace;
            config.defaultDepthFunc = source.defaultDepthFunc;
        }
        return config;
    }

    /** 在默认属性前缀上标记行星着色路径。 */
    private static String createPlanetPrefix(Renderable renderable, Config config) {
        return DefaultShader.createPrefix(renderable, config) + "#define planetLightFlag\n";
    }

    /** 供 ShaderProvider 判断材质是否属于行星。 */
    public static boolean supports(Renderable renderable) {
        return renderable != null
                && renderable.material != null
                && renderable.material.has(PlanetLightAttribute.Type);
    }
}
