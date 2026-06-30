package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public class VectorScrollPaneEffect extends EffectDef {

    public static class ScrollBarDef {
        public Color color = new Color(0.3f, 0.3f, 0.3f, 0.5f);
        public Color hoverColor = new Color(0.4f, 0.4f, 0.4f, 0.7f);
        public float width = 8f;
        public float cornerRadius = 4f;
        public float minHeight = 30f;
    }

    public ScrollBarDef scrollBar = new ScrollBarDef();

    @SuppressWarnings("unchecked")
    public static VectorScrollPaneEffect fromMap(String name, java.util.Map<String, Object> map) {
        VectorScrollPaneEffect e = new VectorScrollPaneEffect();
        e.name = name;
        e.type = "vector_scrollpane";

        java.util.Map<String, Object> sbMap = (java.util.Map<String, Object>) map.get("scrollBar");
        if (sbMap != null) {
            e.scrollBar.color = parseColor((String) sbMap.get("color"), e.scrollBar.color);
            e.scrollBar.hoverColor = parseColor((String) sbMap.get("hoverColor"), e.scrollBar.hoverColor);
            e.scrollBar.width = toFloat(sbMap.get("width"), e.scrollBar.width);
            e.scrollBar.cornerRadius = toFloat(sbMap.get("cornerRadius"), e.scrollBar.cornerRadius);
            e.scrollBar.minHeight = toFloat(sbMap.get("minHeight"), e.scrollBar.minHeight);
        }

        return e;
    }

    private static float toFloat(Object v, float def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.floatValue();
        try { return Float.parseFloat(v.toString()); } catch (NumberFormatException ex) { return def; }
    }
}
