package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public class VectorSelectBoxEffect extends EffectDef {

    public static class ButtonDef {
        public Color color = new Color(0.12f, 0.12f, 0.12f, 0.85f);
        public Color hoverColor = new Color(0.20f, 0.20f, 0.20f, 0.90f);
        public Color openColor = new Color(0.18f, 0.22f, 0.28f, 0.95f);
        public float cornerRadius = 4f;
    }

    public static class TextDef {
        public Color color = new Color(0.75f, 0.75f, 0.75f, 1f);
        public float size = 18f;
    }

    public static class ListDef {
        public Color bgColor = new Color(0.08f, 0.08f, 0.12f, 0.95f);
        public Color itemColor = new Color(0.12f, 0.12f, 0.16f, 0f);
        public Color itemHoverColor = new Color(0.25f, 0.25f, 0.30f, 0.8f);
        public Color itemTextColor = new Color(0.75f, 0.75f, 0.75f, 1f);
        public float cornerRadius = 4f;
    }

    public ButtonDef button = new ButtonDef();
    public TextDef text = new TextDef();
    public ListDef list = new ListDef();
    public BorderEffect border = new BorderEffect();

    @SuppressWarnings("unchecked")
    public static VectorSelectBoxEffect fromMap(String name, java.util.Map<String, Object> map) {
        VectorSelectBoxEffect e = new VectorSelectBoxEffect();
        e.name = name;
        e.type = "vector_selectbox";

        java.util.Map<String, Object> btnMap = (java.util.Map<String, Object>) map.get("button");
        if (btnMap != null) {
            e.button.color = parseColor((String) btnMap.get("color"), e.button.color);
            e.button.hoverColor = parseColor((String) btnMap.get("hoverColor"), e.button.hoverColor);
            e.button.openColor = parseColor((String) btnMap.get("openColor"), e.button.openColor);
            e.button.cornerRadius = toFloat(btnMap.get("cornerRadius"), e.button.cornerRadius);
        }

        java.util.Map<String, Object> textMap = (java.util.Map<String, Object>) map.get("text");
        if (textMap != null) {
            e.text.color = parseColor((String) textMap.get("color"), e.text.color);
            e.text.size = toFloat(textMap.get("size"), e.text.size);
        }

        java.util.Map<String, Object> listMap = (java.util.Map<String, Object>) map.get("list");
        if (listMap != null) {
            e.list.bgColor = parseColor((String) listMap.get("bgColor"), e.list.bgColor);
            e.list.itemColor = parseColor((String) listMap.get("itemColor"), e.list.itemColor);
            e.list.itemHoverColor = parseColor((String) listMap.get("itemHoverColor"), e.list.itemHoverColor);
            e.list.itemTextColor = parseColor((String) listMap.get("itemTextColor"), e.list.itemTextColor);
            e.list.cornerRadius = toFloat(listMap.get("cornerRadius"), e.list.cornerRadius);
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
