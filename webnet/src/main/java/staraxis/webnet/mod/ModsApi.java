package staraxis.webnet.mod;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import staraxis.webnet.core.GameLog;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * ModsApi
 *
 * 作用：
 * - 提供 Mod 管理相关 HTTP API（列出 mods、保存加载顺序与禁用列表）。
 * - 对应路由前缀：/api/mods
 *
 * 提供的接口：
 * - GET / : 返回扫描到的 mods 列表 + 当前 order/disabled
 * - POST /order : 保存 mods 顺序与禁用列表（回写 gamedata/mods/mod-order.json，保留未知字段）
 *
 * 设计说明：
 * - 依赖 ModOrderRepository（配置文件读写）、ModManager（mods
 * 发现/排序口径）、ObjectMapper（JSON），并使用 GameLog 记录关键行为。
 * - 线程模型：涉及阻塞 IO（文件读写、JSON 解析），使用 exchange.dispatch 切换到 worker 线程。
 */
public class ModsApi {

    private final ObjectMapper objectMapper;
    private final ModOrderRepository modOrderRepository;
    private final ModManager modManager;

    public ModsApi(ObjectMapper objectMapper, ModOrderRepository modOrderRepository, ModManager modManager) {
        this.objectMapper = objectMapper;
        this.modOrderRepository = modOrderRepository;
        this.modManager = modManager;
    }

    public HttpHandler createHandler() {
        return Handlers.path()
                .addExactPath("/", this::handleList)
                .addExactPath("/order", this::handleSaveOrder);
    }

    private void handleList(HttpServerExchange exchange) {
        exchange.dispatch(() -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            try {
                ModOrder conf = modOrderRepository.load();
                List<String> discovered = modManager.listAllModIdsDiscovered();
                List<String> order = conf != null && conf.order != null ? conf.order : List.of();
                Set<String> disabledSet = conf != null && conf.disabled != null ? conf.disabled : Set.of();

                LinkedHashSet<String> merged = new LinkedHashSet<>();
                for (String id : order) {
                    if (id != null && !id.isBlank()) {
                        merged.add(id.trim());
                    }
                }
                for (String id : discovered) {
                    if (id != null && !id.isBlank()) {
                        merged.add(id.trim());
                    }
                }
                ArrayList<String> mergedList = new ArrayList<>(merged);

                ArrayList<Map<String, Object>> mods = new ArrayList<>();
                for (int i = 0; i < mergedList.size(); i++) {
                    String id = mergedList.get(i);
                    boolean enabled = !disabledSet.contains(id);

                    ModMetadata meta = new ModMetadata();
                    File metaFile = new File("gamedata/mods/" + id + "/mod.json");
                    if (metaFile.exists() && metaFile.isFile()) {
                        try {
                            meta = objectMapper.readValue(metaFile, ModMetadata.class);
                        } catch (Exception ignored) {
                        }
                    }

                    Map<String, Object> modData = new TreeMap<>();
                    modData.put("id", id);
                    modData.put("enabled", enabled);
                    modData.put("orderIndex", i);
                    modData.put("name", meta.name);
                    modData.put("description", meta.description);
                    modData.put("version", meta.version);
                    modData.put("compatibleGameVersion", meta.compatibleGameVersion);
                    modData.put("author", meta.author);
                    mods.add(modData);
                }

                exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                        "ok", true,
                        "mods", mods,
                        "order", mergedList,
                        "disabled", new ArrayList<>(disabledSet))));
            } catch (Exception e) {
                GameLog.log("mods_list_failed: " + String.valueOf(e));
                exchange.setStatusCode(500);
                try {
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", false,
                            "error", String.valueOf(e.getMessage()))));
                } catch (Exception ignored) {
                    exchange.endExchange();
                }
            }
        });
    }

    private void handleSaveOrder(HttpServerExchange exchange) {
        exchange.dispatch(() -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                exchange.setStatusCode(405);
                try {
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", false,
                            "error", "method_not_allowed")));
                } catch (Exception ignored) {
                    exchange.endExchange();
                }
                return;
            }
            try {
                exchange.startBlocking();
                String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (body.isBlank()) {
                    body = "{}";
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> req = objectMapper.readValue(body, Map.class);

                Object orderObj = req.get("order");
                Object disabledObj = req.get("disabled");

                ArrayList<String> newOrder = new ArrayList<>();
                if (orderObj instanceof List) {
                    for (Object o : (List<?>) orderObj) {
                        if (o == null) {
                            continue;
                        }
                        String s = String.valueOf(o).trim();
                        if (!s.isBlank()) {
                            newOrder.add(s);
                        }
                    }
                }

                Set<String> newDisabled = new LinkedHashSet<>();
                if (disabledObj instanceof List) {
                    for (Object o : (List<?>) disabledObj) {
                        if (o == null) {
                            continue;
                        }
                        String s = String.valueOf(o).trim();
                        if (!s.isBlank()) {
                            newDisabled.add(s);
                        }
                    }
                }

                File f = modOrderRepository.file();
                Map<String, Object> root = new TreeMap<>();
                if (f.exists() && f.isFile()) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> old = objectMapper.readValue(f, Map.class);
                        if (old != null) {
                            root.putAll(old);
                        }
                    } catch (Exception ignored) {
                    }
                }

                root.put("schemaVersion", 1);
                root.put("order", newOrder);
                root.put("disabled", new ArrayList<>(newDisabled));

                if (f.getParentFile() != null) {
                    f.getParentFile().mkdirs();
                }
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(f, root);

                GameLog.log("mods_order_saved orderSize=" + newOrder.size() + " disabledSize=" + newDisabled.size());

                exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                        "ok", true)));
            } catch (Exception e) {
                GameLog.log("mods_order_save_failed: " + String.valueOf(e));
                exchange.setStatusCode(500);
                try {
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", false,
                            "error", String.valueOf(e.getMessage()))));
                } catch (Exception ignored) {
                    exchange.endExchange();
                }
            }
        });
    }
}
