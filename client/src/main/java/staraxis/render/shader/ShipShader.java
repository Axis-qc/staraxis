package staraxis.render.shader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.Attributes;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;

import staraxis.render.shader.PlayerColorAttribute;

/**
 * ShipShader（舰船自定义着色器）。
 *
 * 基于 libGDX DefaultShader 扩展，实现 Stellaris 同款"玩家颜色自发光"：
 * - fragment shader 读取 normal 贴图蓝色通道作为发光掩码
 * - 发光颜色 = 掩码 × 玩家颜色（PlayerColorAttribute，每艘舰船不同）
 * - 叠加基础自发光 u_baseEmissive，避免背光面全黑
 *
 * 仅当材质包含 PlayerColorAttribute 时启用 playerColorFlag 分支；
 * 其余实体（行星/恒星）仍由默认 shader 渲染。
 */
public class ShipShader extends DefaultShader {

    /** 自定义 vertex shader 资产路径（逐像素光照 + TBN normal mapping）。 */
    private static final String VERTEX_SHADER_PATH = "shaders/ship.vertex.glsl";

    /** 自定义 fragment shader 资产路径。 */
    private static final String FRAGMENT_SHADER_PATH = "shaders/ship.fragment.glsl";

    /** 基础自发光强度（diffuse 底色自发光，防止背光面全黑）。 */
    public static final float BASE_EMISSIVE = 0.2f;

    /** 玩家颜色灯光亮度（normal 蓝通道掩码 × 玩家颜色 的强度系数）。 */
    public static final float LIGHT_INTENSITY = 1.0f;

    /** 玩家颜色 uniform 位置。 */
    private final int u_playerColor;

    /** 基础自发光 uniform 位置。 */
    private final int u_baseEmissive;

    /** 玩家灯光亮度 uniform 位置。 */
    private final int u_lightIntensity;

    /**
     * 玩家颜色 setter：每渲染一个 renderable 前，从材质读取玩家颜色写入 uniform。
     */
    private static final BaseShader.Setter playerColorSetter = new BaseShader.LocalSetter() {
        @Override
        public void set(BaseShader shader, int inputID, Renderable renderable, Attributes combinedAttributes) {
            PlayerColorAttribute attr = (PlayerColorAttribute) combinedAttributes.get(PlayerColorAttribute.Type);
            if (attr != null) {
                shader.set(inputID, attr.color.r, attr.color.g, attr.color.b);
            }
        }
    };

    /** 基础自发光 setter：diffuse 底色自发光（固定值）。 */
    private static final BaseShader.Setter baseEmissiveSetter = new BaseShader.LocalSetter() {
        @Override
        public void set(BaseShader shader, int inputID, Renderable renderable, Attributes combinedAttributes) {
            shader.set(inputID, BASE_EMISSIVE);
        }
    };

    /** 玩家灯光亮度 setter：固定值。 */
    private static final BaseShader.Setter lightIntensitySetter = new BaseShader.LocalSetter() {
        @Override
        public void set(BaseShader shader, int inputID, Renderable renderable, Attributes combinedAttributes) {
            shader.set(inputID, LIGHT_INTENSITY);
        }
    };

    /**
     * 构造：使用自定义 fragment shader，并注册玩家颜色 uniform。
     *
     * @param renderable 首个用于创建此 shader 的 renderable（决定 prefix flag）
     * @param config     DefaultShader 配置
     */
    public ShipShader(Renderable renderable, Config config) {
        this(renderable, config, createPrefix(renderable, config));
    }

    public ShipShader(Renderable renderable, Config config, String prefix) {
        this(renderable, config, prefix,
                config.vertexShader != null ? config.vertexShader
                        : Gdx.files.internal(VERTEX_SHADER_PATH).readString(),
                config.fragmentShader != null ? config.fragmentShader
                        : Gdx.files.internal(FRAGMENT_SHADER_PATH).readString());
    }

    public ShipShader(Renderable renderable, Config config, String prefix, String vertexShader, String fragmentShader) {
        this(renderable, config, prefix, vertexShader, fragmentShader, null);
    }

    public ShipShader(Renderable renderable, Config config, String prefix, String vertexShader, String fragmentShader,
                      String defaultCullFace) {
        super(renderable, config, prefix, vertexShader, fragmentShader);
        // 注册玩家颜色 / 基础自发光 / 灯光亮度 uniform（setter 见类内静态实现）
        u_playerColor = register(new Uniform("u_playerColor", PlayerColorAttribute.Type), playerColorSetter);
        u_baseEmissive = register(new Uniform("u_baseEmissive"), baseEmissiveSetter);
        u_lightIntensity = register(new Uniform("u_lightIntensity"), lightIntensitySetter);
    }

    /**
     * 在默认 prefix 基础上追加 playerColorFlag（材质含玩家颜色时启用发光分支）。
     */
    public static String createPrefix(Renderable renderable, Config config) {
        String prefix = DefaultShader.createPrefix(renderable, config);
        if (renderable.material != null
                && renderable.material.has(PlayerColorAttribute.Type)) {
            prefix += "#define playerColorFlag\n";
        }
        return prefix;
    }

    @Override
    public boolean canRender(Renderable renderable) {
        // 仅渲染含玩家颜色属性的材质，避免误用普通实体
        if (renderable.material == null
                || !renderable.material.has(PlayerColorAttribute.Type)) {
            return false;
        }
        return super.canRender(renderable);
    }

    /** 供 provider 判断当前材质是否走自定义 shader。 */
    public static boolean supports(Renderable renderable) {
        return renderable != null && renderable.material != null
                && renderable.material.has(PlayerColorAttribute.Type);
    }
}
