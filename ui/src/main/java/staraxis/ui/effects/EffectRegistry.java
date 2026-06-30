package staraxis.ui.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.HashMap;
import java.util.Map;

public class EffectRegistry {

    private final Map<String, EffectDef> effects = new HashMap<>();

    public void clear() {
        effects.clear();
    }

    public void load(String internalPath) {
        FileHandle fh = Gdx.files.internal(internalPath);
        if (!fh.exists()) {
            Gdx.app.error("EffectRegistry", "Effects file not found: " + internalPath);
            return;
        }
        try {
            String json = fh.readString("UTF-8");
            JsonValue root = new JsonReader().parse(json);
            for (JsonValue entry : root) {
                String name = entry.name;
                Map<String, Object> map = jsonValueToMap(entry);
                String type = (String) map.get("type");
                if (type == null) {
                    Gdx.app.error("EffectRegistry", "Effect '" + name + "' missing 'type' field");
                    continue;
                }
                EffectDef def;
                switch (type) {
                    case "menu_entry":
                        def = MenuEntryEffect.fromMap(name, map);
                        break;
                    case "vector_button":
                        def = VectorButtonEffect.fromMap(name, map);
                        break;
                    case "vector_label":
                        def = VectorLabelEffect.fromMap(name, map);
                        break;
                    case "container":
                        def = ContainerEffect.fromMap(name, map);
                        break;
                    case "vector_image":
                        def = VectorImageEffect.fromMap(name, map);
                        break;
                    case "vector_checkbox":
                        def = VectorCheckBoxEffect.fromMap(name, map);
                        break;
                    case "vector_slider":
                        def = VectorSliderEffect.fromMap(name, map);
                        break;
                    case "vector_progressbar":
                        def = VectorProgressBarEffect.fromMap(name, map);
                        break;
                    case "vector_textfield":
                        def = VectorTextFieldEffect.fromMap(name, map);
                        break;
                    case "vector_selectbox":
                        def = VectorSelectBoxEffect.fromMap(name, map);
                        break;
                    case "vector_scrollpane":
                        def = VectorScrollPaneEffect.fromMap(name, map);
                        break;
                    case "vector_window":
                        def = VectorWindowEffect.fromMap(name, map);
                        break;
                    default:
                        Gdx.app.error("EffectRegistry", "Unknown effect type: " + type);
                        continue;
                }
                effects.put(name, def);
            }
            Gdx.app.log("EffectRegistry", "Loaded " + effects.size() + " effects from " + internalPath);
        } catch (Exception e) {
            Gdx.app.error("EffectRegistry", "Failed to load effects from " + internalPath, e);
        }
    }

    public <T extends EffectDef> T get(String name, Class<T> type) {
        EffectDef def = effects.get(name);
        if (def == null) return null;
        return (T) def;
    }

    public EffectDef get(String name) {
        return effects.get(name);
    }

    public int size() {
        return effects.size();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonValueToMap(JsonValue v) {
        Map<String, Object> map = new HashMap<>();
        for (JsonValue child = v.child; child != null; child = child.next) {
            map.put(child.name, jsonValueToObject(child));
        }
        return map;
    }

    private static Object jsonValueToObject(JsonValue v) {
        if (v.isObject()) return jsonValueToMap(v);
        if (v.isArray()) {
            java.util.List<Object> list = new java.util.ArrayList<>();
            for (JsonValue item = v.child; item != null; item = item.next) {
                list.add(jsonValueToObject(item));
            }
            return list;
        }
        if (v.isBoolean()) return v.asBoolean();
        if (v.isNumber()) return v.asFloat();
        if (v.isString()) return v.asString();
        return null;
    }
}
