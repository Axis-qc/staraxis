package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public class VectorImageEffect extends EffectDef {

    public static class BackgroundDef {
        public Color color = new Color(1f, 1f, 1f, 1f);
    }

    public BackgroundDef background = new BackgroundDef();
    public BorderEffect border = new BorderEffect();

    @SuppressWarnings("unchecked")
    public static VectorImageEffect fromMap(String name, java.util.Map<String, Object> map) {
        VectorImageEffect e = new VectorImageEffect();
        e.name = name;
        e.type = "vector_image";

        java.util.Map<String, Object> bgMap = (java.util.Map<String, Object>) map.get("background");
        if (bgMap != null) {
            e.background.color = parseColor((String) bgMap.get("color"), e.background.color);
        }

        e.border = BorderEffect.fromMap((java.util.Map<String, Object>) map.get("border"));

        return e;
    }
}
