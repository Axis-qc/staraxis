package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public class VectorTextFieldEffect extends EffectDef {

    public static class BackgroundDef {
        public Color color = new Color(0.12f, 0.12f, 0.12f, 0.85f);
        public Color focusedColor = new Color(0.18f, 0.22f, 0.28f, 0.90f);
        public float cornerRadius = 4f;
    }

    public static class TextDef {
        public Color color = new Color(0.75f, 0.75f, 0.75f, 1f);
        public Color focusedColor = new Color(1f, 1f, 1f, 1f);
        public Color placeholderColor = new Color(0.4f, 0.4f, 0.4f, 0.6f);
        public float size = 18f;
    }

    public static class CursorDef {
        public Color color = new Color(1f, 1f, 1f, 1f);
        public float width = 2f;
        public float blinkRate = 0.5f;
    }

    public static class SelectionDef {
        public Color color = new Color(0.3f, 0.65f, 0.95f, 0.3f);
    }

    public BackgroundDef background = new BackgroundDef();
    public TextDef text = new TextDef();
    public CursorDef cursor = new CursorDef();
    public SelectionDef selection = new SelectionDef();
    public BorderEffect border = new BorderEffect();

    @SuppressWarnings("unchecked")
    public static VectorTextFieldEffect fromMap(String name, java.util.Map<String, Object> map) {
        VectorTextFieldEffect e = new VectorTextFieldEffect();
        e.name = name;
        e.type = "vector_textfield";

        java.util.Map<String, Object> bgMap = (java.util.Map<String, Object>) map.get("background");
        if (bgMap != null) {
            e.background.color = parseColor((String) bgMap.get("color"), e.background.color);
            e.background.focusedColor = parseColor((String) bgMap.get("focusedColor"), e.background.focusedColor);
            e.background.cornerRadius = toFloat(bgMap.get("cornerRadius"), e.background.cornerRadius);
        }

        java.util.Map<String, Object> textMap = (java.util.Map<String, Object>) map.get("text");
        if (textMap != null) {
            e.text.color = parseColor((String) textMap.get("color"), e.text.color);
            e.text.focusedColor = parseColor((String) textMap.get("focusedColor"), e.text.focusedColor);
            e.text.placeholderColor = parseColor((String) textMap.get("placeholderColor"), e.text.placeholderColor);
            e.text.size = toFloat(textMap.get("size"), e.text.size);
        }

        java.util.Map<String, Object> cursorMap = (java.util.Map<String, Object>) map.get("cursor");
        if (cursorMap != null) {
            e.cursor.color = parseColor((String) cursorMap.get("color"), e.cursor.color);
            e.cursor.width = toFloat(cursorMap.get("width"), e.cursor.width);
            e.cursor.blinkRate = toFloat(cursorMap.get("blinkRate"), e.cursor.blinkRate);
        }

        java.util.Map<String, Object> selMap = (java.util.Map<String, Object>) map.get("selection");
        if (selMap != null) {
            e.selection.color = parseColor((String) selMap.get("color"), e.selection.color);
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
