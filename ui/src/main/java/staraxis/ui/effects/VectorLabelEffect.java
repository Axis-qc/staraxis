package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public class VectorLabelEffect extends EffectDef {

    public static class TextDef {
        public Color color = new Color(0.75f, 0.75f, 0.75f, 1f);
        public float size = 16f;
    }

    public TextDef text = new TextDef();

    @SuppressWarnings("unchecked")
    public static VectorLabelEffect fromMap(String name, java.util.Map<String, Object> map) {
        VectorLabelEffect e = new VectorLabelEffect();
        e.name = name;
        e.type = "vector_label";

        java.util.Map<String, Object> textMap = (java.util.Map<String, Object>) map.get("text");
        if (textMap != null) {
            e.text.color = parseColor((String) textMap.get("color"), e.text.color);
            e.text.size = toFloat(textMap.get("size"), e.text.size);
        }

        return e;
    }

    private static float toFloat(Object v, float def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.floatValue();
        try { return Float.parseFloat(v.toString()); } catch (NumberFormatException ex) { return def; }
    }
}
