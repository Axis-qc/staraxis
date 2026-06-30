package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public class VectorWindowEffect extends EffectDef {

    public static class TitleBarDef {
        public Color color = new Color(0.08f, 0.10f, 0.14f, 0.95f);
        public Color textColor = new Color(1f, 1f, 1f, 1f);
        public float height = 28f;
        public float textSize = 16f;
    }

    public static class ContentDef {
        public Color color = new Color(0.06f, 0.08f, 0.12f, 0.90f);
    }

    public TitleBarDef titleBar = new TitleBarDef();
    public ContentDef content = new ContentDef();
    public BorderEffect border = new BorderEffect();

    @SuppressWarnings("unchecked")
    public static VectorWindowEffect fromMap(String name, java.util.Map<String, Object> map) {
        VectorWindowEffect e = new VectorWindowEffect();
        e.name = name;
        e.type = "vector_window";

        java.util.Map<String, Object> tbMap = (java.util.Map<String, Object>) map.get("titleBar");
        if (tbMap != null) {
            e.titleBar.color = parseColor((String) tbMap.get("color"), e.titleBar.color);
            e.titleBar.textColor = parseColor((String) tbMap.get("textColor"), e.titleBar.textColor);
            e.titleBar.height = toFloat(tbMap.get("height"), e.titleBar.height);
            e.titleBar.textSize = toFloat(tbMap.get("textSize"), e.titleBar.textSize);
        }

        java.util.Map<String, Object> ctMap = (java.util.Map<String, Object>) map.get("content");
        if (ctMap != null) {
            e.content.color = parseColor((String) ctMap.get("color"), e.content.color);
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
