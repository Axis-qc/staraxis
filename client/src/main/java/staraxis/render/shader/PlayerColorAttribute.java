package staraxis.render.shader;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Attribute;

/**
 * PlayerColorAttribute（舰船玩家颜色材质属性）。
 *
 * 作用：在舰船材质上标记归属国家颜色（0xRRGGBB → Color），
 * 供 ShipShader 读取后混合到自发光（normal 贴图蓝色通道掩码）。
 *
 * 使用方式：
 * - 材质设置：material.set(new PlayerColorAttribute(color))
 * - shader 读取：combinedAttributes.get(PlayerColorAttribute.Type)
 */
public class PlayerColorAttribute extends Attribute {

    /** 属性别名（libGDX 全局注册用）。 */
    public static final String Alias = "playerColor";

    /** 属性类型 ID（libGDX 注册表分配）。 */
    public static final long Type = register(Alias);

    /** 玩家颜色（RGB，alpha 恒为 1）。 */
    public final Color color = new Color();

    public PlayerColorAttribute(Color color) {
        super(Type);
        if (color != null) {
            this.color.set(color);
        }
    }

    public PlayerColorAttribute(float r, float g, float b) {
        this(new Color(r, g, b, 1f));
    }

    @Override
    public Attribute copy() {
        return new PlayerColorAttribute(this.color);
    }

    @Override
    public int hashCode() {
        return 991 * super.hashCode() + color.toIntBits();
    }

    @Override
    public int compareTo(Attribute o) {
        if (type != o.type) {
            return (int) (type - o.type);
        }
        return ((PlayerColorAttribute) o).color.toIntBits() - color.toIntBits();
    }
}
