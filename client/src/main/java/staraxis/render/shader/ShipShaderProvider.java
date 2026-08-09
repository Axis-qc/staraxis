package staraxis.render.shader;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;

/**
 * ShipShaderProvider（舰船着色器分发器）。
 *
 * 按材质分发 shader：
 * - 材质含 PlanetLightAttribute（行星）→ PlanetShader（世界空间恒星光照）
 * - 材质含 PlayerColorAttribute（舰船）→ ShipShader（玩家颜色自发光）
 * - 其余材质（恒星等）→ 默认 DefaultShader
 *
 * 使用方式：SystemViewRenderer 构造 modelBatch 时传入。
 */
public class ShipShaderProvider extends DefaultShaderProvider {

    public ShipShaderProvider() {
        this(null);
    }

    public ShipShaderProvider(DefaultShader.Config config) {
        super(config);
    }

    @Override
    protected Shader createShader(Renderable renderable) {
        if (PlanetShader.supports(renderable)) {
            return new PlanetShader(renderable, config);
        }
        if (ShipShader.supports(renderable)) {
            return new ShipShader(renderable, config);
        }
        return super.createShader(renderable);
    }
}
