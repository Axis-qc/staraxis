package staraxis.render.shader;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.math.Vector3;

/**
 * PlanetLightAttribute（行星世界空间光照材质属性）。
 *
 * 保存恒星到行星的光线传播方向和恒星光色，供 PlanetShader 读取。
 * 该属性不包含相机数据，保证行星明暗只由世界空间法线和恒星位置决定。
 */
public final class PlanetLightAttribute extends Attribute {

    /** 属性别名。 */
    public static final String Alias = "planetLight";

    /** 属性类型 ID。 */
    public static final long Type = register(Alias);

    /** 光线传播方向：恒星 -> 行星，shader 中取反得到行星 -> 恒星。 */
    public final Vector3 direction = new Vector3(0f, -1f, 0f);

    /** 恒星光色与强度。 */
    public final Color color = new Color(0.8f, 0.8f, 0.9f, 1f);

    public PlanetLightAttribute() {
        super(Type);
    }

    public PlanetLightAttribute(Vector3 direction, Color color) {
        this();
        if (direction != null) {
            this.direction.set(direction);
        }
        if (color != null) {
            this.color.set(color);
        }
    }

    @Override
    public Attribute copy() {
        return new PlanetLightAttribute(direction, color);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + direction.hashCode();
        result = 31 * result + color.toIntBits();
        return result;
    }

    @Override
    public int compareTo(Attribute other) {
        if (type != other.type) {
            return (int) (type - other.type);
        }
        PlanetLightAttribute that = (PlanetLightAttribute) other;
        int result = Float.compare(direction.x, that.direction.x);
        if (result != 0) return result;
        result = Float.compare(direction.y, that.direction.y);
        if (result != 0) return result;
        result = Float.compare(direction.z, that.direction.z);
        if (result != 0) return result;
        return Integer.compare(color.toIntBits(), that.color.toIntBits());
    }
}
