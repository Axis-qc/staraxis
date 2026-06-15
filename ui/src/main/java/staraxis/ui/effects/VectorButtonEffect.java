package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public class VectorButtonEffect extends EffectDef {

    public static class BackgroundDef {
        public Color color = new Color(0.15f, 0.15f, 0.15f, 0.85f);
        public Color hoverColor = new Color(0.25f, 0.25f, 0.25f, 0.90f);
        public Color pressedColor = new Color(0.35f, 0.12f, 0.12f, 0.95f);
    }

    public static class TextDef {
        public Color color = new Color(0.75f, 0.75f, 0.75f, 1f);
        public Color hoverColor = new Color(1f, 1f, 1f, 1f);
    }

    public static class AccentDef {
        public boolean enabled = true;
        public Color color = new Color(0.3f, 0.65f, 0.95f, 1f);
        public float width = 3f;
    }

    public BackgroundDef background = new BackgroundDef();
    public BorderEffect border = new BorderEffect();
    public TextDef text = new TextDef();
    public AccentDef accent = new AccentDef();

    @SuppressWarnings("unchecked")
    public static VectorButtonEffect fromMap(String name, java.util.Map<String, Object> map) {
        VectorButtonEffect e = new VectorButtonEffect();
        e.name = name;
        e.type = "vector_button";

        java.util.Map<String, Object> bgMap = (java.util.Map<String, Object>) map.get("background");
        if (bgMap != null) {
            e.background.color = parseColor((String) bgMap.get("color"), e.background.color);
            e.background.hoverColor = parseColor((String) bgMap.get("hoverColor"), e.background.hoverColor);
            e.background.pressedColor = parseColor((String) bgMap.get("pressedColor"), e.background.pressedColor);
        }

        e.border = BorderEffect.fromMap((java.util.Map<String, Object>) map.get("border"));

        java.util.Map<String, Object> textMap = (java.util.Map<String, Object>) map.get("text");
        if (textMap != null) {
            e.text.color = parseColor((String) textMap.get("color"), e.text.color);
            e.text.hoverColor = parseColor((String) textMap.get("hoverColor"), e.text.hoverColor);
        }

        java.util.Map<String, Object> accentMap = (java.util.Map<String, Object>) map.get("accent");
        if (accentMap != null) {
            e.accent.enabled = !Boolean.FALSE.equals(accentMap.get("enabled"));
            e.accent.color = parseColor((String) accentMap.get("color"), e.accent.color);
            e.accent.width = toFloat(accentMap.get("width"), e.accent.width);
        }

        return e;
    }

    private static float toFloat(Object v, float def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).floatValue();
        try { return Float.parseFloat(v.toString()); } catch (Exception ex) { return def; }
    }
}
