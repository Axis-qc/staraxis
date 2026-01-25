package staraxis.webnet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.Headers;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WebNetServer {

    private final WebNetServerConfig config;

    private Undertow undertow;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Set<WebSocketChannel> channels = ConcurrentHashMap.newKeySet();

    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private final AtomicLong lastDisconnectAtMs = new AtomicLong(0);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "webnet-auto-exit");
        t.setDaemon(true);
        return t;
    });

    public WebNetServer(WebNetServerConfig config) {
        this.config = config;
    }

    public void start() {
        PathHandler routes = Handlers.path();

        routes.addPrefixPath("/ws", Handlers.websocket((WebSocketHttpExchange exchange, WebSocketChannel channel) -> {
            channels.add(channel);
            connectionCount.incrementAndGet();

            System.out.println("WS connect: " + channel.getSourceAddress() + " connections=" + connectionCount.get());

            channel.getReceiveSetter().set(new AbstractReceiveListener() {
                @Override
                protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                    String text = message.getData();
                    System.out.println("WS recv: " + text);
                    WebSockets.sendText(text, channel, null);
                }

                @Override
                protected void onClose(WebSocketChannel webSocketChannel,
                        io.undertow.websockets.core.StreamSourceFrameChannel frameChannel) {
                    channels.remove(webSocketChannel);
                    int left = connectionCount.decrementAndGet();
                    System.out.println("WS close: connections=" + left);
                    if (left <= 0) {
                        lastDisconnectAtMs.set(System.currentTimeMillis());
                    }
                }
            });

            channel.resumeReceives();
            WebSockets.sendText("{\"type\":\"hello\",\"server\":\"webnet\"}", channel, null);
        }));

        // --- API Routes ---
        PathHandler apiHandler = Handlers.path();
        apiHandler.addExactPath("/status", exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            int c = connectionCount.get();
            long last = lastDisconnectAtMs.get();
            long idleMs = last > 0 ? (System.currentTimeMillis() - last) : 0;
            String json = "{" +
                    "\"host\":\"" + config.host + "\"," +
                    "\"port\":" + config.port + "," +
                    "\"connections\":" + c + "," +
                    "\"autoExitSeconds\":" + config.autoExitSeconds + "," +
                    "\"idleSeconds\":" + (idleMs / 1000) +
                    "}";
            exchange.getResponseSender().send(json);
        });

        apiHandler.addExactPath("/quit", exchange -> {
            System.out.println("HTTP quit requested: " + exchange.getRequestMethod() + " " + exchange.getRequestPath());
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send("{\"ok\":true}");
            exchange.endExchange();
            Thread t = new Thread(() -> shutdownAndExit(0), "webnet-quit");
            t.setDaemon(false);
            t.start();
        });

        // --- i18n API ---
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

        routes.addPrefixPath("/api", apiHandler);

        // --- Static Content ---
        routes.addPrefixPath("/", createStaticHandler());

        undertow = Undertow.builder().addHttpListener(config.port, config.host).setHandler(routes).build();
        undertow.start();

        System.out.println("WebNet HTTP listening on http://" + config.host + ":" + config.port);
        System.out.println("WebNet WS listening on ws://" + config.host + ":" + config.port + "/ws");
        System.out.println("WebNet status: http://" + config.host + ":" + config.port + "/api/status");

        startAutoExitWatcher();
    }

    private void startAutoExitWatcher() {
        int seconds = config.autoExitSeconds;
        if (seconds <= 0) {
            return;
        }
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (connectionCount.get() > 0) {
                    return;
                }
                long last = lastDisconnectAtMs.get();
                if (last <= 0) {
                    return;
                }
                long idleMs = System.currentTimeMillis() - last;
                if (idleMs >= seconds * 1000L) {
                    System.out.println("WebNet auto-exit: no connections for " + seconds + "s, shutting down.");
                    shutdownAndExit(0);
                }
            } catch (Exception ignored) {
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private HttpHandler createStaticHandler() {
        File webDist = new File("../web/dist");
        if (!webDist.exists() || !webDist.isDirectory()) {
            return exchange -> {
                exchange.setStatusCode(500);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=utf-8");
                exchange.getResponseSender().send(
                        "Web 前端未构建：未找到目录 ../web/dist\n" +
                                "请先构建前端产物到 web/dist（例如执行前端构建命令），或检查工作目录/路径配置。\n");
            };
        }
        ResourceHandler rh = new ResourceHandler(new FileResourceManager(webDist, 1024 * 1024));
        rh.setWelcomeFiles("index.html");
        return rh;
    }

    public void stop() {
        if (undertow != null) {
            undertow.stop();
            undertow = null;
        }
    }

    private void shutdownAndExit(int code) {
        System.out.println("WebNet shutting down... closing ws channels=" + channels.size());
        for (WebSocketChannel ch : channels) {
            try {
                WebSockets.sendClose(1000, "bye", ch, null);
            } catch (Exception ignored) {
            }
            try {
                ch.close();
            } catch (Exception ignored) {
            }
        }
        channels.clear();
        try {
            stop();
        } catch (Exception ignored) {
        }
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {
        }
        System.out.println("WebNet exit(" + code + ")");
        System.exit(code);
    }
}
