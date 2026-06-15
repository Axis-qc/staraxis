package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public class BorderEffect {
    public float radius;
    public float width;
    public Color color = Color.WHITE;
    public String type; // "solid", "dashed", "none"

    public static BorderEffect fromMap(java.util.Map<String, Object> map) {
        BorderEffect e = new BorderEffect();
        if (map == null) return e;
        e.radius = toFloat(map.get("radius"), 0);
        e.width = toFloat(map.get("width"), 0);
        e.color = EffectDef.parseColor((String) map.get("color"), Color.WHITE);
        e.type = (String) map.getOrDefault("type", "solid");
        return e;
    }

    private static float toFloat(Object v, float def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).floatValue();
        try { return Float.parseFloat(v.toString()); } catch (Exception e) { return def; }
    }
}
