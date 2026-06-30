package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public class VectorProgressBarEffect extends EffectDef {

    public static class BackgroundDef {
        public Color color = new Color(0.15f, 0.15f, 0.15f, 0.6f);
        public float cornerRadius = 4f;
    }

    public static class FillDef {
        public Color color = new Color(0.3f, 0.65f, 0.95f, 0.8f);
        public float cornerRadius = 4f;
    }

    public BackgroundDef background = new BackgroundDef();
    public FillDef fill = new FillDef();
    public BorderEffect border = new BorderEffect();

    @SuppressWarnings("unchecked")
    public static VectorProgressBarEffect fromMap(String name, java.util.Map<String, Object> map) {
        VectorProgressBarEffect e = new VectorProgressBarEffect();
        e.name = name;
        e.type = "vector_progressbar";

        java.util.Map<String, Object> bgMap = (java.util.Map<String, Object>) map.get("background");
        if (bgMap != null) {
            e.background.color = parseColor((String) bgMap.get("color"), e.background.color);
            e.background.cornerRadius = toFloat(bgMap.get("cornerRadius"), e.background.cornerRadius);
        }

        java.util.Map<String, Object> fillMap = (java.util.Map<String, Object>) map.get("fill");
        if (fillMap != null) {
            e.fill.color = parseColor((String) fillMap.get("color"), e.fill.color);
            e.fill.cornerRadius = toFloat(fillMap.get("cornerRadius"), e.fill.cornerRadius);
        }

        e.border = BorderEffect.fromMap((java.util.Map<String, Object>) map.get("border"));

        return e;
    }

    private static float toFloat(Object v, float def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.floatValue();
        try { return Float.parseFloat(v.toString()); } catch (NumberFormatException ex) { return def; }
    }
}
