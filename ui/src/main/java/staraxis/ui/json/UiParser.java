package staraxis.ui.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class UiParser {
    private static final Logger log = LoggerFactory.getLogger(UiParser.class);
    private final JsonSchema schema;

    public UiParser() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        try (InputStream is = UiParser.class.getResourceAsStream("/ui/component.schema.json")) {
            if (is == null)
                throw new IllegalStateException("component.schema.json not found on classpath");
            JsonNode node = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(is);
            this.schema = factory.getSchema(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load component.schema.json", e);
        }
    }

    public ComponentNode parseInternal(String internalPath) {
        FileHandle fh = Gdx.files.internal(internalPath);
        if (!fh.exists()) {
            log.error("UI json not found: {}", internalPath);
            return null;
        }
        return parseString(internalPath, fh.readString(StandardCharsets.UTF_8.name()));
    }

    public ComponentNode parseString(String source, String json) {
        try {
            JsonNode jsonNode = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(json);
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            if (!errors.isEmpty()) {
                for (ValidationMessage err : errors) {
                    log.error("{} | {}", source, err.getMessage());
                }
                return null;
            }
            JsonValue root = new JsonReader().parse(json);
            return toNode(root);
        } catch (Exception e) {
            log.error("Failed to parse UI json {}", source, e);
            return null;
        }
    }

    private ComponentNode toNode(JsonValue value) {
        ComponentNode node = new ComponentNode(value.getString("type", null));
        node.name = value.getString("name", null);

        JsonValue props = value.get("properties");
        if (props != null)
            node.properties = toMap(props);

        JsonValue children = value.get("children");
        if (children != null) {
            children.iterator().forEachRemaining(c -> node.children.add(toNode(c)));
        }
        return node;
    }

    private Map<String, Object> toMap(JsonValue obj) {
        Map<String, Object> map = new LinkedHashMap<>();
        obj.iterator().forEachRemaining(v -> map.put(v.name, toJava(v)));
        return map;
    }

    private Object toJava(JsonValue v) {
        if (v.isBoolean())
            return v.asBoolean();
        if (v.isNumber())
            return v.asFloat();
        if (v.isString())
            return v.asString();
        if (v.isObject())
            return toMap(v);
        return null;
    }
}
