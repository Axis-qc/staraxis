/*
 * ShipApi
 *
 * 文件作用：
 * - 舰船相关 API（模块纹理列表、模块挂载点编辑）。
 * - 前端开发模式读取纹理、编辑坐标后，将数据发送给后端，后端创建或更新模块 JSON。
 *
 * 提供的接口 API：
 * - GET /api/ship/textures：返回 assets/ship/textures/ 下的纹理文件列表（相对路径），并标注是否已被使用。
 * - PUT /api/ship/modules/{moduleId}/mount-points：更新模块的挂载点坐标（引擎、开火、炮塔中心）。
 *
 * 使用方式：
 * - 由 WebNetServer 在 /api/ship 前缀下注册。
 * - 前端开发模式调用这些接口进行纹理加载与坐标编辑。
 *
 * 注意事项：
 * - 所有文件读写属于阻塞 IO，必须在 worker 线程执行（exchange.dispatch）。
 * - 坐标系约定：模块纹理中心为 (0,0)，画布 1:1 缩放（屏幕像素 1px = 1 坐标单位）。
 * - 模块 JSON 按分类保存：assets/ship/modules/{category}.json（如 ENGINE.json、WEAPON.json 等）。
 * - 纹理使用判定：扫描所有分类 JSON，如果某个纹理路径已被模块引用，则标记为已使用。
 */

package staraxis.webnet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.PathTemplateMatch;
import staraxis.webnet.api.ship.ShipCommandApi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 舰船相关 API（模块纹理列表、模块挂载点编辑）。
 */
public class ShipApi {

    private static final String SHIP_TEXTURES_ROOT = "assets/ship";
    private static final String SHIP_MODULES_ROOT = "assets/ship/modules";

    private final ObjectMapper objectMapper;

    public ShipApi(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 创建 /api/ship 路由处理器（注册到 WebNetServer）。
     */
    public HttpHandler createHandler() {
        return exchange -> {
            String relativePath = exchange.getRelativePath();
            if (relativePath.startsWith("/textures")) {
                handleTexturesList(exchange);
            } else if (relativePath.equals("/move")) {
                // 处理舰船移动命令喵
                handleMoveShip(exchange);
            } else if (relativePath.equals("/move/complete")) {
                handleMoveShipCompletion(exchange);
            } else if (relativePath.startsWith("/modules/by-texture")) {
                handleFindModuleByTexture(exchange);
            } else if (relativePath.startsWith("/modules/") && relativePath.endsWith("/mount-points")) {
                handleUpdateMountPoints(exchange);
            } else {
                exchange.setStatusCode(404).endExchange();
            }
        };
    }

    /**
     * POST /api/ship/move
     * 处理舰船移动命令，转发到 ShipCommandApi 喵。
     */
    private void handleMoveShip(HttpServerExchange exchange) {
        if (!exchange.getRequestMethod().equalToString("POST")) {
            exchange.setStatusCode(405).endExchange();
            return;
        }

        exchange.dispatch(() -> {
            try {
                exchange.startBlocking();
                String body = new String(exchange.getInputStream().readAllBytes());
                @SuppressWarnings("unchecked")
                Map<String, Object> req = objectMapper.readValue(body, Map.class);

                // 从请求中提取 worldId（可以从查询参数或请求体中获取）喵
                String worldId = getQueryParam(exchange, "worldId");
                if (worldId == null && req.containsKey("worldId")) {
                    worldId = String.valueOf(req.get("worldId"));
                }

                if (worldId == null || worldId.isBlank()) {
                    exchange.setStatusCode(400);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", false,
                            "error", "worldId_required")));
                    return;
                }

                // 调用 ShipCommandApi 处理移动命令喵
                Map<String, Object> result = ShipCommandApi.handleMoveShip(objectMapper, worldId, req);

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(objectMapper.writeValueAsString(result));
            } catch (Exception e) {
                exchange.setStatusCode(500);
                try {
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", false,
                            "error", String.valueOf(e.getMessage()))));
                } catch (Exception ignored) {
                    exchange.endExchange();
                }
            }
        });
    }

    /**
     * POST /api/ship/move/complete
     * 处理前端本地模拟结束后的移动完成回报喵。
     */
    private void handleMoveShipCompletion(HttpServerExchange exchange) {
        if (!exchange.getRequestMethod().equalToString("POST")) {
            exchange.setStatusCode(405).endExchange();
            return;
        }

        exchange.dispatch(() -> {
            try {
                exchange.startBlocking();
                String body = new String(exchange.getInputStream().readAllBytes());
                @SuppressWarnings("unchecked")
                Map<String, Object> req = objectMapper.readValue(body, Map.class);

                String worldId = getQueryParam(exchange, "worldId");
                if (worldId == null && req.containsKey("worldId")) {
                    worldId = String.valueOf(req.get("worldId"));
                }

                if (worldId == null || worldId.isBlank()) {
                    exchange.setStatusCode(400);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", false,
                            "error", "worldId_required")));
                    return;
                }

                Map<String, Object> result = ShipCommandApi.handleMoveShipCompletion(objectMapper, worldId, req);

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(objectMapper.writeValueAsString(result));
            } catch (Exception e) {
                exchange.setStatusCode(500);
                try {
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", false,
                            "error", String.valueOf(e.getMessage()))));
                } catch (Exception ignored) {
                    exchange.endExchange();
                }
            }
        });
    }

    /**
     * 获取查询参数喵。
     */
    private String getQueryParam(HttpServerExchange exchange, String name) {
        var deque = exchange.getQueryParameters().get(name);
        return deque == null || deque.isEmpty() ? null : deque.getFirst();
    }

    /**
     * GET /api/ship/textures
     * 返回 assets/ship/textures/ 下的纹理文件列表（相对路径），并标注是否已被使用。
     */
    private void handleTexturesList(HttpServerExchange exchange) {
        if (!exchange.getRequestMethod().equalToString("GET")) {
            exchange.setStatusCode(405).endExchange();
            return;
        }

        exchange.dispatch(() -> {
            try {
                // 1) 扫描所有分类 JSON，收集已使用的纹理路径
                Set<String> usedTextures = collectUsedTextures();

                // 2) 扫描纹理目录
                List<Map<String, Object>> textures = listTexturesWithUsage(usedTextures);

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                        "ok", true,
                        "textures", textures)));
            } catch (Exception e) {
                exchange.setStatusCode(500);
                try {
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", false,
                            "error", String.valueOf(e.getMessage()))));
                } catch (Exception ignored) {
                    exchange.endExchange();
                }
            }
        });
    }

    /**
     * GET /api/ship/modules/by-texture?path=texturePath
     * 根据纹理路径查找对应的模块（如果已被使用）。
     * 返回模块完整数据（包含 category），用于前端回填。
     */
    private void handleFindModuleByTexture(HttpServerExchange exchange) {
        if (!exchange.getRequestMethod().equalToString("GET")) {
            exchange.setStatusCode(405).endExchange();
            return;
        }

        Deque<String> pathDeque = exchange.getQueryParameters().get("path");
        String path = pathDeque == null || pathDeque.isEmpty() ? null : pathDeque.getFirst();
        if (path == null || path.isBlank()) {
            exchange.setStatusCode(400);
            try {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                        "ok", false,
                        "error", "missing path parameter")));
            } catch (Exception ignored) {
                exchange.endExchange();
            }
            return;
        }

        exchange.dispatch(() -> {
            try {
                Map<String, Object> module = findModuleByTexture(path);
                if (module == null) {
                    exchange.setStatusCode(404);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", false,
                            "error", "module_not_found",
                            "texturePath", path)));
                    return;
                }

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                        "ok", true,
                        "module", module)));
            } catch (Exception e) {
                exchange.setStatusCode(500);
                try {
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", false,
                            "error", String.valueOf(e.getMessage()))));
                } catch (Exception ignored) {
                    exchange.endExchange();
                }
            }
        });
    }

    /**
     * 扫描所有分类 JSON，根据 texturePath 查找模块（返回完整模块数据）。
     */
    private Map<String, Object> findModuleByTexture(String texturePath) throws IOException {
        File modulesDir = new File(SHIP_MODULES_ROOT);
        if (!modulesDir.exists() || !modulesDir.isDirectory()) {
            return null;
        }

        File[] jsonFiles = modulesDir.listFiles(File::isFile);
        if (jsonFiles == null) {
            return null;
        }

        for (File f : jsonFiles) {
            if (!f.getName().toLowerCase().endsWith(".json")) {
                continue;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> modules = objectMapper.readValue(f, List.class);
            if (modules == null) {
                continue;
            }

            for (Map<String, Object> m : modules) {
                @SuppressWarnings("unchecked")
                String tp = (String) m.get("texturePath");
                if (texturePath.equals(tp)) {
                    return m;
                }
            }
        }

        return null;
    }

    /**
     * PUT /api/ship/modules/{moduleId}/mount-points
     * 更新模块的挂载点坐标（引擎、开火、炮塔中心）。
     * 请求体示例：
     * {
     * "category": "ENGINE", //
     * 必填：模块分类（ENGINE/WEAPON/ARMOR/ELECTRONIC/STRUCTURE/UTILITY）
     * "engineMount": {"x": 10, "y": -5},
     * "fireMount": {"x": 20, "y": 0},
     * "turretCenter": {"x": 0, "y": 0}
     * }
     *
     * 规则：
     * - 根据 category 将模块保存到对应的分类 JSON 文件（例如 ENGINE ->
     * assets/ship/modules/engine.json）。
     * - 如果 moduleId 已存在，更新挂载点；不存在则创建新条目。
     */
    private void handleUpdateMountPoints(HttpServerExchange exchange) {
        if (!exchange.getRequestMethod().equalToString("PUT")) {
            exchange.setStatusCode(405).endExchange();
            return;
        }

        PathTemplateMatch pathMatch = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
        String moduleId = pathMatch != null ? pathMatch.getParameters().get("moduleId") : null;
        if (moduleId == null || moduleId.isBlank()) {
            exchange.setStatusCode(400);
            try {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                        "ok", false,
                        "error", "missing moduleId")));
            } catch (Exception ignored) {
                exchange.endExchange();
            }
            return;
        }

        exchange.dispatch(() -> {
            try {
                exchange.startBlocking();
                String body = exchange.getInputStream().readAllBytes().toString();
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(body, Map.class);

                @SuppressWarnings("unchecked")
                String category = (String) payload.get("category");
                if (category == null || category.isBlank()) {
                    exchange.setStatusCode(400);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", false,
                            "error", "missing category")));
                    return;
                }

                // 写入分类 JSON
                String jsonPath = SHIP_MODULES_ROOT + "/" + category.toLowerCase() + ".json";
                File jsonFile = new File(jsonPath);
                List<Map<String, Object>> modules = new ArrayList<>();

                // 读取现有模块列表
                if (jsonFile.exists() && jsonFile.isFile()) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> existing = objectMapper.readValue(jsonFile, List.class);
                    if (existing != null) {
                        modules.addAll(existing);
                    }
                }

                // 查找或创建模块
                Map<String, Object> targetModule = null;
                for (Map<String, Object> m : modules) {
                    @SuppressWarnings("unchecked")
                    String id = (String) m.get("moduleId");
                    if (moduleId.equals(id)) {
                        targetModule = m;
                        break;
                    }
                }

                String texturePath = (String) payload.get("texturePath");
                if (texturePath == null || texturePath.isBlank()) {
                    exchange.setStatusCode(400);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", false,
                            "error", "missing texturePath")));
                    return;
                }

                // 判断纹理是否已被其它模块占用
                Set<String> usedTextures = collectUsedTextures();
                boolean textureUsed = usedTextures.contains(texturePath);

                if (targetModule == null) {
                    if (textureUsed) {
                        exchange.setStatusCode(409);
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", "texture_already_used",
                                "texturePath", texturePath)));
                        return;
                    }

                    // 创建新模块
                    targetModule = new LinkedHashMap<>();
                    targetModule.put("moduleId", moduleId);
                    targetModule.put("category", category);
                    targetModule.put("texturePath", texturePath);

                    // 允许前端传入元数据，否则使用默认值
                    targetModule.put("nameKey", payload.getOrDefault("nameKey", "ship.module." + moduleId));
                    targetModule.put("descriptionKey", payload.getOrDefault("descriptionKey", ""));
                    targetModule.put("slotType", payload.getOrDefault("slotType", category + "_SLOT"));
                    targetModule.put("size", payload.getOrDefault("size", 1));
                    targetModule.put("mass", payload.getOrDefault("mass", 100.0));
                    targetModule.put("cost", payload.getOrDefault("cost", List.of()));
                    targetModule.put("prerequisites", payload.getOrDefault("prerequisites", List.of()));
                    modules.add(targetModule);
                } else {
                    // 更新已有模块：允许修改纹理（如换图），但需要避免撞车
                    String oldTexture = (String) targetModule.get("texturePath");
                    boolean textureChanged = oldTexture == null ? true : !oldTexture.equals(texturePath);
                    if (textureChanged && textureUsed) {
                        exchange.setStatusCode(409);
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", "texture_already_used",
                                "texturePath", texturePath)));
                        return;
                    }

                    targetModule.put("texturePath", texturePath);
                    if (payload.containsKey("nameKey")) {
                        targetModule.put("nameKey", payload.get("nameKey"));
                    }
                    if (payload.containsKey("descriptionKey")) {
                        targetModule.put("descriptionKey", payload.get("descriptionKey"));
                    }
                    if (payload.containsKey("slotType")) {
                        targetModule.put("slotType", payload.get("slotType"));
                    }
                    if (payload.containsKey("size")) {
                        targetModule.put("size", payload.get("size"));
                    }
                    if (payload.containsKey("mass")) {
                        targetModule.put("mass", payload.get("mass"));
                    }
                }

                // 更新挂载点（仅写 mountPoints 子结构，避免把 payload 全塞进去）
                Map<String, Object> mountPoints = new LinkedHashMap<>();
                if (payload.get("engineMount") != null) {
                    mountPoints.put("engineMount", payload.get("engineMount"));
                }
                if (payload.get("fireMount") != null) {
                    mountPoints.put("fireMount", payload.get("fireMount"));
                }
                if (payload.get("turretCenter") != null) {
                    mountPoints.put("turretCenter", payload.get("turretCenter"));
                }
                targetModule.put("mountPoints", mountPoints);

                // 确保目录存在
                File dir = new File(SHIP_MODULES_ROOT);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                // 写回 JSON（pretty print）
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, modules);

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                        "ok", true,
                        "moduleId", moduleId,
                        "category", category,
                        "updated", payload)));
            } catch (Exception e) {
                exchange.setStatusCode(500);
                try {
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", false,
                            "error", String.valueOf(e.getMessage()))));
                } catch (Exception ignored) {
                    exchange.endExchange();
                }
            }
        });
    }

    /**
     * 扫描所有分类 JSON，收集已使用的纹理路径。
     */
    private Set<String> collectUsedTextures() throws IOException {
        Set<String> used = new HashSet<>();
        File modulesDir = new File(SHIP_MODULES_ROOT);
        if (!modulesDir.exists() || !modulesDir.isDirectory()) {
            return used;
        }

        File[] jsonFiles = modulesDir.listFiles(File::isFile);
        if (jsonFiles == null) {
            return used;
        }

        for (File f : jsonFiles) {
            if (!f.getName().toLowerCase().endsWith(".json")) {
                continue;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> modules = objectMapper.readValue(f, List.class);
            if (modules == null) {
                continue;
            }

            for (Map<String, Object> m : modules) {
                @SuppressWarnings("unchecked")
                String texturePath = (String) m.get("texturePath");
                if (texturePath != null && !texturePath.isBlank()) {
                    used.add(texturePath);
                }
            }
        }

        return used;
    }

    /**
     * 扫描 assets/ship/textures/ 目录，返回相对路径列表（递归），并标注是否已被使用。
     */
    private List<Map<String, Object>> listTexturesWithUsage(Set<String> usedTextures) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        File root = new File(SHIP_TEXTURES_ROOT);
        if (!root.exists() || !root.isDirectory()) {
            return out;
        }
        try (Stream<Path> s = Files.walk(root.toPath())) {
            s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                                || name.endsWith(".webp");
                    })
                    .forEach(p -> {
                        // 返回相对于 assets/ship/textures/ 的路径，例如 "engine/boost.png"
                        Path relative = root.toPath().relativize(p);
                        String path = relative.toString().replace('\\', '/');
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("path", path);
                        item.put("used", usedTextures.contains(path));
                        out.add(item);
                    });
        }
        return out;
    }
}
