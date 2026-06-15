package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public abstract class EffectDef {
    public String name;
    public String type;

    protected static Color parseColor(String hex) {
        if (hex == null || hex.isBlank()) return Color.WHITE;
        return Color.valueOf(hex.startsWith("#") ? hex : "#" + hex);
    }

    protected static Color parseColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        try {
            return Color.valueOf(hex.startsWith("#") ? hex : "#" + hex);
        } catch (Exception e) {
            return fallback;
        }
    }
}
