package staraxis.webnet.api.nation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.util.Headers;
import staraxis.game.nation.NationDef;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NationPresetsApi
 *
 * 作用：
 * - 提供“预设国家（Presets）”的读取 API。
 *
 * 数据来源：
 * - gamedata/nations/presets/*.json
 *
 * 注意：
 * - 文件读写属于阻塞 IO：调用方必须在 Undertow worker 线程执行（exchange.dispatch(...)）。
 */
public final class NationPresetsApi {

    private NationPresetsApi() {
    }

    /**
     * 扫描并加载所有预设国家定义。
     *
     * @param objectMapper Jackson
     * @return NationDef 列表
     */
    public static List<NationDef> loadAllPresetNations(ObjectMapper objectMapper) {
        ArrayList<NationDef> out = new ArrayList<>();

        File dir = new File("gamedata/nations/presets");
        if (!dir.exists() || !dir.isDirectory()) {
            return out;
        }

        File[] files = dir.listFiles((d, name) -> name != null && name.endsWith(".json"));
        if (files == null) {
            return out;
        }

        for (File f : files) {
            if (f == null || !f.isFile()) {
                continue;
            }
            try {
                // 文件结构：{ schemaVersion: 1, nation: { ...NationDef... } }
                @SuppressWarnings("unchecked")
                Map<String, Object> root = objectMapper.readValue(f, Map.class);
                Object nationObj = root.get("nation");
                if (!(nationObj instanceof Map)) {
                    continue;
                }
                // 重新序列化一遍以复用 NationDef 映射（避免手写字段拷贝）
                byte[] bytes = objectMapper.writeValueAsBytes(nationObj);
                NationDef def = objectMapper.readValue(bytes, NationDef.class);
                if (def != null && def.id != null && !def.id.isBlank()) {
                    out.add(def);
                }
            } catch (Exception ignored) {
                // 预设文件坏了不应导致整个服务崩溃：先跳过。
            }
        }

        return out;
    }

    /**
     * 生成 nations API 的标准响应结构。
     *
     * @param nations NationDef 列表
     * @return JSON-serializable map
     */
    public static Map<String, Object> toResponse(List<NationDef> nations) {
        ArrayList<Map<String, Object>> arr = new ArrayList<>();
        if (nations != null) {
            for (NationDef n : nations) {
                if (n == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", n.id);
                item.put("name", n.name == null ? "" : n.name);
                item.put("description", n.description == null ? "" : n.description);
                item.put("governmentId", n.governmentId == null ? "" : n.governmentId);
                item.put("speciesIds", n.speciesIds == null ? List.of() : n.speciesIds);
                item.put("startingTechIds", n.startingTechIds == null ? List.of() : n.startingTechIds);
                arr.add(item);
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("nations", arr);
        return resp;
    }

    /**
     * nations API 的 Content-Type。
     */
    public static String jsonContentType() {
        return "application/json; charset=" + StandardCharsets.UTF_8.name().toLowerCase();
    }

    /**
     * 为 Undertow 设置 JSON Content-Type。
     */
    public static void setJsonContentType(io.undertow.server.HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, jsonContentType());
    }
}
