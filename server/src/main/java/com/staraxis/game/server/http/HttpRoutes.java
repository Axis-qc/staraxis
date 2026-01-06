package com.staraxis.game.server.http;

import com.sun.net.httpserver.HttpServer;

public final class HttpRoutes {

    private HttpRoutes() {
    }

    public static void register(HttpServer server) {
        server.createContext("/api/worldgen/start-new-game", new StartNewGameHandler());
    }
}
