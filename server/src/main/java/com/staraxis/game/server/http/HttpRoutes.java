package com.staraxis.game.server.http;

import com.sun.net.httpserver.HttpServer;

public final class HttpRoutes {

    private HttpRoutes() {
    }

    public static void register(HttpServer server) {
        server.createContext("/api/worldgen/start-new-game", new StartNewGameHandler());
        server.createContext("/world/generate", new WorldGenDebugHandler("world-generate"));
        server.createContext("/star-system/", new WorldGenDebugHandler("system-orbit-paths"));
        server.createContext("/planet/", new WorldGenDebugHandler("planet-surface-mesh"));
    }
}
