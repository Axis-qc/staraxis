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

/**
 * UI 解析器：负责将 UI 定义（.json）文件转换为 {@link ComponentNode} 数据树。
 *
 * 设计要点：
 * 1. **Schema 优先**：在解析任何 JSON 前，必须先通过 `component.schema.json`
 * 进行校验，确保数据格式的正确性与一致性。
 * 2. **Gdx-First IO**：所有文件读取操作通过 `Gdx.files.internal` 完成，以规避 `Path`/`File`
 * 带来的工作目录（working directory）问题。
 * 3. **错误处理**：解析或校验失败时，不抛出异常中断游戏，而是记录错误日志并返回 `null`，由调用方决定如何处理（例如显示
 * ErrorActor）。
 */
public class UiParser {
    private static final Logger log = LoggerFactory.getLogger(UiParser.class);
    private final JsonSchema schema;

    public UiParser() {
        // NOTE: schema 文件作为 resource 打包，确保在任何环境下都能从 classpath 加载。
        // 这是保证解析器稳定运行的基础。
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        try (InputStream is = UiParser.class.getResourceAsStream("/ui/component.schema.json")) {
            if (is == null)
                throw new IllegalStateException("component.schema.json not found on classpath");
            // NOTE: 使用 Jackson 读取 schema，因为 networknt-validator 基于 Jackson 的 JsonNode。
            JsonNode node = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(is);
            this.schema = factory.getSchema(node);
        } catch (Exception e) {
            // HACK: 如果 schema 加载失败，UI 系统完全无法工作，属于致命错误，直接抛出 RuntimeException 中断启动。
            throw new RuntimeException("Failed to load component.schema.json", e);
        }
    }

    /**
     * 从 libGDX internal 路径加载并解析 UI JSON。
     * 这是推荐的 UI 加载入口，因为它能正确处理 assets 路径。
     *
     * @param internalPath assets 根目录下的相对路径，例如 "ui/gameui/main-menu.json"
     * @return 解析成功返回 ComponentNode 根节点，失败返回 null
     */
    public ComponentNode parseInternal(String internalPath) {
        FileHandle fh = Gdx.files.internal(internalPath);
        if (!fh.exists()) {
            log.error("UI json not found: {}", internalPath);
            return null;
        }
        // NOTE: fh.readString() 默认使用 UTF-8，但显式指定更稳妥。
        return parseString(internalPath, fh.readString(StandardCharsets.UTF_8.name()));
    }

    /**
     * 解析给定的 JSON 字符串。
     * 主要用于热重载或控制台直接传入字符串进行测试。
     *
     * @param source 来源标识（例如文件名），用于日志输出
     * @param json   JSON 字符串内容
     * @return 解析成功返回 ComponentNode 根节点，失败返回 null
     */
    public ComponentNode parseString(String source, String json) {
        try {
            // 1. 使用 Jackson + networknt-validator 做 schema 校验
            JsonNode jsonNode = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(json);
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            if (!errors.isEmpty()) {
                for (ValidationMessage err : errors) {
                    log.error("{} | {}", source, err.getMessage());
                }
                return null;
            }
            // 2. 使用 libGDX 的 JsonReader 将字符串转为 JsonValue 树，便于后续递归
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
        node.include = value.getString("include", null);
        // params 可以是对象
        JsonValue paramsVal = value.get("params");
        if (paramsVal != null && paramsVal.isObject()) {
            node.params = toMap(paramsVal);
        }

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
        // NOTE: 使用 LinkedHashMap 是为了保持 JSON 中属性的原始顺序，便于调试。
        Map<String, Object> map = new LinkedHashMap<>();
        obj.iterator().forEachRemaining(v -> map.put(v.name, toJava(v)));
        return map;
    }

    private Object toJava(JsonValue v) {
        if (v.isBoolean())
            return v.asBoolean();
        if (v.isNumber()) // NOTE: 统一转为 float，符合 Scene2D 大部分数值 API (width/height/pad 等)
            return v.asFloat();
        if (v.isString())
            return v.asString();
        if (v.isObject())
            return toMap(v);
        return null;
    }
}
