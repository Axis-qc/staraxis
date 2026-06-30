package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public class VectorCheckBoxEffect extends EffectDef {

    public static class BoxDef {
        public Color color = new Color(0.15f, 0.15f, 0.15f, 0.85f);
        public Color hoverColor = new Color(0.25f, 0.25f, 0.25f, 0.90f);
        public Color checkedColor = new Color(0.3f, 0.65f, 0.95f, 0.8f);
        public float size = 20f;
        public float cornerRadius = 3f;
    }

    public static class CheckDef {
        public Color color = Color.WHITE;
        public float width = 2f;
    }

    public static class TextDef {
        public Color color = new Color(0.75f, 0.75f, 0.75f, 1f);
        public Color hoverColor = new Color(1f, 1f, 1f, 1f);
        public float size = 18f;
    }

    public BoxDef box = new BoxDef();
    public CheckDef check = new CheckDef();
    public TextDef text = new TextDef();

    @SuppressWarnings("unchecked")
    public static VectorCheckBoxEffect fromMap(String name, java.util.Map<String, Object> map) {
        VectorCheckBoxEffect e = new VectorCheckBoxEffect();
        e.name = name;
        e.type = "vector_checkbox";

        java.util.Map<String, Object> boxMap = (java.util.Map<String, Object>) map.get("box");
        if (boxMap != null) {
            e.box.color = parseColor((String) boxMap.get("color"), e.box.color);
            e.box.hoverColor = parseColor((String) boxMap.get("hoverColor"), e.box.hoverColor);
            e.box.checkedColor = parseColor((String) boxMap.get("checkedColor"), e.box.checkedColor);
            e.box.size = toFloat(boxMap.get("size"), e.box.size);
            e.box.cornerRadius = toFloat(boxMap.get("cornerRadius"), e.box.cornerRadius);
        }

        java.util.Map<String, Object> checkMap = (java.util.Map<String, Object>) map.get("check");
        if (checkMap != null) {
            e.check.color = parseColor((String) checkMap.get("color"), e.check.color);
            e.check.width = toFloat(checkMap.get("width"), e.check.width);
        }

        java.util.Map<String, Object> textMap = (java.util.Map<String, Object>) map.get("text");
        if (textMap != null) {
            e.text.color = parseColor((String) textMap.get("color"), e.text.color);
            e.text.hoverColor = parseColor((String) textMap.get("hoverColor"), e.text.hoverColor);
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
