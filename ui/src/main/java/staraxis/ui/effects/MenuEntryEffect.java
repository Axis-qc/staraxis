package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public class MenuEntryEffect extends EffectDef {

    public static class BulletDef {
        public Color color = new Color(0.53f, 0.53f, 0.53f, 1f);
        public Color hoverColor = new Color(0.3f, 0.65f, 0.95f, 1f);
        public float size = 8f;
        public boolean glow = true;
        public float glowRadius = 12f;
        public float glowAlpha = 0.3f;
    }

    public static class TextDef {
        public Color color = new Color(0.53f, 0.53f, 0.53f, 1f);
        public Color hoverColor = new Color(1f, 1f, 1f, 1f);
    }

    public static class HoverDef {
        public float shiftX = 10f;
        public float speed = 8f;
    }

    public static class TagDef {
        public Color bgColor = new Color(0.3f, 0.65f, 0.95f, 0.24f);
        public Color textColor = new Color(1f, 1f, 1f, 1f);
    }

    public BulletDef bullet = new BulletDef();
    public TextDef text = new TextDef();
    public HoverDef hover = new HoverDef();
    public TagDef tag = new TagDef();

    @SuppressWarnings("unchecked")
    public static MenuEntryEffect fromMap(String name, java.util.Map<String, Object> map) {
        MenuEntryEffect e = new MenuEntryEffect();
        e.name = name;
        e.type = "menu_entry";

        java.util.Map<String, Object> bulletMap = (java.util.Map<String, Object>) map.get("bullet");
        if (bulletMap != null) {
            e.bullet.color = parseColor((String) bulletMap.get("color"), e.bullet.color);
            e.bullet.hoverColor = parseColor((String) bulletMap.get("hoverColor"), e.bullet.hoverColor);
            e.bullet.size = toFloat(bulletMap.get("size"), e.bullet.size);
            java.util.Map<String, Object> glowMap = (java.util.Map<String, Object>) bulletMap.get("glow");
            if (glowMap != null) {
                e.bullet.glow = !Boolean.FALSE.equals(glowMap.get("enabled"));
                e.bullet.glowRadius = toFloat(glowMap.get("radius"), e.bullet.glowRadius);
                e.bullet.glowAlpha = toFloat(glowMap.get("alpha"), e.bullet.glowAlpha);
            }
        }

        java.util.Map<String, Object> textMap = (java.util.Map<String, Object>) map.get("text");
        if (textMap != null) {
            e.text.color = parseColor((String) textMap.get("color"), e.text.color);
            e.text.hoverColor = parseColor((String) textMap.get("hoverColor"), e.text.hoverColor);
        }

        java.util.Map<String, Object> hoverMap = (java.util.Map<String, Object>) map.get("hover");
        if (hoverMap != null) {
            e.hover.shiftX = toFloat(hoverMap.get("shiftX"), e.hover.shiftX);
            e.hover.speed = toFloat(hoverMap.get("speed"), e.hover.speed);
        }

        java.util.Map<String, Object> tagMap = (java.util.Map<String, Object>) map.get("tag");
        if (tagMap != null) {
            e.tag.bgColor = parseColor((String) tagMap.get("bgColor"), e.tag.bgColor);
            e.tag.textColor = parseColor((String) tagMap.get("textColor"), e.tag.textColor);
        }

        return e;
    }

    private static float toFloat(Object v, float def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).floatValue();
        try { return Float.parseFloat(v.toString()); } catch (Exception ex) { return def; }
    }
}
