package staraxis.ui.effects;

import com.badlogic.gdx.graphics.Color;

public class VectorSliderEffect extends EffectDef {

    public static class TrackDef {
        public Color color = new Color(0.15f, 0.15f, 0.15f, 0.6f);
        public Color fillColor = new Color(0.3f, 0.65f, 0.95f, 0.6f);
        public float height = 6f;
        public float cornerRadius = 3f;
    }

    public static class KnobDef {
        public Color color = new Color(0.8f, 0.8f, 0.8f, 1f);
        public Color hoverColor = new Color(1f, 1f, 1f, 1f);
        public float size = 16f;
        public float cornerRadius = 8f;
    }

    public TrackDef track = new TrackDef();
    public KnobDef knob = new KnobDef();

    @SuppressWarnings("unchecked")
    public static VectorSliderEffect fromMap(String name, java.util.Map<String, Object> map) {
        VectorSliderEffect e = new VectorSliderEffect();
        e.name = name;
        e.type = "vector_slider";

        java.util.Map<String, Object> trackMap = (java.util.Map<String, Object>) map.get("track");
        if (trackMap != null) {
            e.track.color = parseColor((String) trackMap.get("color"), e.track.color);
            e.track.fillColor = parseColor((String) trackMap.get("fillColor"), e.track.fillColor);
            e.track.height = toFloat(trackMap.get("height"), e.track.height);
            e.track.cornerRadius = toFloat(trackMap.get("cornerRadius"), e.track.cornerRadius);
        }

        java.util.Map<String, Object> knobMap = (java.util.Map<String, Object>) map.get("knob");
        if (knobMap != null) {
            e.knob.color = parseColor((String) knobMap.get("color"), e.knob.color);
            e.knob.hoverColor = parseColor((String) knobMap.get("hoverColor"), e.knob.hoverColor);
            e.knob.size = toFloat(knobMap.get("size"), e.knob.size);
            e.knob.cornerRadius = toFloat(knobMap.get("cornerRadius"), e.knob.cornerRadius);
        }

        return e;
    }

    private static float toFloat(Object v, float def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.floatValue();
        try { return Float.parseFloat(v.toString()); } catch (NumberFormatException ex) { return def; }
    }
}
