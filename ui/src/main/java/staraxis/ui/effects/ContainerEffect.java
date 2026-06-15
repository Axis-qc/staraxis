package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public class ContainerEffect extends EffectDef {

    public static class BackgroundDef {
        public Color color = new Color(0.1f, 0.1f, 0.1f, 1f);
        public float alpha = 1f;
    }

    public BackgroundDef background = new BackgroundDef();
    public BorderEffect border = new BorderEffect();

    @SuppressWarnings("unchecked")
    public static ContainerEffect fromMap(String name, java.util.Map<String, Object> map) {
        ContainerEffect e = new ContainerEffect();
        e.name = name;
        e.type = "container";

        java.util.Map<String, Object> bgMap = (java.util.Map<String, Object>) map.get("background");
        if (bgMap != null) {
            e.background.color = parseColor((String) bgMap.get("color"), e.background.color);
            e.background.alpha = toFloat(bgMap.get("alpha"), e.background.alpha);
        }

        e.border = BorderEffect.fromMap((java.util.Map<String, Object>) map.get("border"));

        return e;
    }

    private static float toFloat(Object v, float def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).floatValue();
        try { return Float.parseFloat(v.toString()); } catch (Exception ex) { return def; }
    }
}
