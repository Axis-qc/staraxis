package staraxis.webnet.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * AiConfigApi
 *
 * 作用：
 * - 处理 AI 系统的配置读写请求喵。
 * - 支持按厂商分类持久化到 ai_system/config/config.yaml 喵。
 */
public class AiConfigApi {
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final File configFile = new File("ai_system/config/config.yaml");

    public AiConfigApi(ObjectMapper objectMapper) {
        this.jsonMapper = objectMapper;
    }

    public HttpHandler createHandler() {
        return exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                handleSaveConfig(exchange);
            } else if ("GET".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                handleGetConfig(exchange);
            } else {
                exchange.setStatusCode(405).endExchange();
            }
        };
    }

    private void handleGetConfig(HttpServerExchange exchange) {
        exchange.dispatch(() -> {
            try {
                if (!configFile.exists()) {
                    exchange.setStatusCode(404).endExchange();
                    return;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> config = yamlMapper.readValue(configFile, Map.class);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                exchange.getResponseSender().send(jsonMapper.writeValueAsString(config));
            } catch (Exception e) {
                exchange.setStatusCode(500).endExchange();
            }
        });
    }

    private void handleSaveConfig(HttpServerExchange exchange) {
        exchange.dispatch(() -> {
            try {
                exchange.startBlocking();
                String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> req = jsonMapper.readValue(body, Map.class);

                // 读取现有配置进行合并喵
                Map<String, Object> currentConfig = new HashMap<>();
                if (configFile.exists()) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> existing = yamlMapper.readValue(configFile, Map.class);
                        currentConfig.putAll(existing);
                    } catch (Exception ignored) {
                    }
                }

                // 1. 更新 server 部分喵
                @SuppressWarnings("unchecked")
                Map<String, Object> server = (Map<String, Object>) currentConfig.computeIfAbsent("server",
                        k -> new HashMap<String, Object>());
                if (req.containsKey("enabled")) {
                    server.put("auto_start", req.get("enabled"));
                }

                // 2. 更新 ai 部分喵
                @SuppressWarnings("unchecked")
                Map<String, Object> ai = (Map<String, Object>) currentConfig.computeIfAbsent("ai",
                        k -> new HashMap<String, Object>());
                String providerId = (String) req.get("provider");
                if (providerId != null) {
                    ai.put("active_provider", providerId);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> providers = (Map<String, Object>) ai.computeIfAbsent("providers",
                            k -> new HashMap<String, Object>());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pConfig = (Map<String, Object>) providers.computeIfAbsent(providerId,
                            k -> new HashMap<String, Object>());

                    if (req.containsKey("base_url"))
                        pConfig.put("base_url", req.get("base_url"));
                    if (req.containsKey("api_key"))
                        pConfig.put("api_key", req.get("api_key"));
                    if (req.containsKey("model"))
                        pConfig.put("model", req.get("model"));
                }

                // 写回 YAML 喵
                yamlMapper.writeValue(configFile, currentConfig);

                staraxis.webnet.core.WebNetLog
                        .log("AI configuration updated (provider=" + providerId + ") and saved to config.yaml");

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                exchange.getResponseSender().send("{\"ok\":true}");
            } catch (Exception e) {
                exchange.setStatusCode(500);
                try {
                    exchange.getResponseSender().send("{\"ok\":false,\"error\":\"" + e.getMessage() + "\"}");
                } catch (Exception ignored) {
                }
            }
        });
    }
}
