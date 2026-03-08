package staraxis.webnet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Headers;
import staraxis.webnet.ai.AiChatApi;
import staraxis.webnet.ai.AiConfigApi;
import staraxis.webnet.ai.AiUsageApi;
import staraxis.webnet.auth.AuthApi;
import staraxis.webnet.auth.AuthStore;
import staraxis.webnet.core.WsConnectionManager;
import staraxis.webnet.mod.ModManager;
import staraxis.webnet.mod.ModOrderRepository;
import staraxis.webnet.mod.ModsApi;

import java.util.List;
import java.util.Map;

/**
 * ApiRoutes（API 路由聚合挂载器）喵。
 *
 * 作用喵：
 * - 统一挂载 webnet 下 /api 前缀的各业务域路由喵。
 * - 将 WebNetServer 中剩余的 API 挂载逻辑下沉，避免入口类承担具体路由组装喵。
 */
public final class ApiRoutes {

    private ApiRoutes() {
    }

    /**
     * 注册所有 /api 下的路由喵。
     */
    public static PathHandler createApiHandler(ObjectMapper objectMapper, AuthStore authStore, WsConnectionManager connMgr,
            staraxis.webnet.core.WebNetServerConfig config, java.util.concurrent.atomic.AtomicInteger playerCountRef,
            java.util.concurrent.atomic.AtomicInteger aiCountRef, java.util.concurrent.atomic.AtomicLong lastDisconnectAtMsRef,
            staraxis.webnet.core.AdminApi.AdminActions actions,
            java.util.function.LongSupplier tickCostMsSupplier) {

        PathHandler apiHandler = Handlers.path();

        // --- 已下沉业务路由 ---
        staraxis.webnet.api.nation.NationRoutes.register(apiHandler, objectMapper);
        staraxis.webnet.api.joingame.NewGameRoutes.register(apiHandler, objectMapper, authStore);
        staraxis.webnet.api.snapshot.SnapshotRoutes.register(apiHandler, objectMapper, authStore, connMgr,
                tickCostMsSupplier);

        // --- Auth ---
        AuthApi authApi = new AuthApi(authStore, objectMapper);
        apiHandler.addPrefixPath("/auth", authApi.createHandler());

        // --- Mods ---
        ModOrderRepository modOrderRepository = new ModOrderRepository();
        ModManager modManager = new ModManager(modOrderRepository);
        ModsApi modsApi = new ModsApi(objectMapper, modOrderRepository, modManager);
        apiHandler.addPrefixPath("/mods", modsApi.createHandler());

        // --- Admin ---
        staraxis.webnet.core.AdminApi adminApi = new staraxis.webnet.core.AdminApi(
                config,
                authStore,
                objectMapper,
                playerCountRef,
                aiCountRef,
                lastDisconnectAtMsRef,
                actions);
        apiHandler.addPrefixPath("/", adminApi.createHandler());

        // --- I18n ---
        PathHandler i18nHandler = Handlers.path();
        i18nHandler.addExactPath("/languages", exchange -> {
            List<String> languages = I18nApi.listAvailableLanguages();
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(objectMapper.writeValueAsString(languages));
        });
        i18nHandler.addPrefixPath("/", exchange -> {
            String lang = exchange.getRelativePath().substring(1);
            if (lang.contains("/") || lang.contains(".")) {
                exchange.setStatusCode(404).endExchange();
                return;
            }
            Map<String, String> strings = I18nApi.loadMergedStrings(lang);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(objectMapper.writeValueAsString(strings));
        });
        apiHandler.addPrefixPath("/i18n", i18nHandler);

        // --- Ship ---
        ShipApi shipApi = new ShipApi(objectMapper);
        apiHandler.addPrefixPath("/ship", shipApi.createHandler());

        // --- AI Proxy ---
        AiConfigApi aiConfigApi = new AiConfigApi(objectMapper);
        apiHandler.addPrefixPath("/ai/config", aiConfigApi.createHandler());

        AiChatApi aiChatApi = new AiChatApi(objectMapper, authStore);
        apiHandler.addPrefixPath("/ai/chat", aiChatApi.createHandler());
        apiHandler.addPrefixPath("/ai/history", aiChatApi.createHistoryHandler());
        apiHandler.addPrefixPath("/ai/history/clear", aiChatApi.createClearHistoryHandler());

        AiUsageApi aiUsageApi = new AiUsageApi(objectMapper);
        apiHandler.addPrefixPath("/ai/usage", aiUsageApi.createHandler());

        // 用于保持与旧结构一致的包装：访问 /api/** 时自动触发 AI 预启动与活动上报喵
        HttpHandler apiWrapped = exchange -> {
            try {
                String rp = exchange.getRequestPath();
                if (rp != null && rp.startsWith("/api/") && !"/api/status".equals(rp)) {
                    staraxis.webnet.ai.WebAiAutoStarter.ensureAiStartedIfNeeded();
                    staraxis.webnet.ai.WebAiAutoStarter.reportActivity();
                }
            } catch (Exception ignored) {
            }
            apiHandler.handleRequest(exchange);
        };

        PathHandler wrapped = Handlers.path();
        wrapped.addPrefixPath("/", apiWrapped);
        return wrapped;
    }
}
