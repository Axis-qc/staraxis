package com.staraxis.game.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.staraxis.game.server.http.HttpRoutes;
import com.sun.net.httpserver.HttpServer;

public class ServerMain {

    public static void main(String[] args) throws Exception {
        String bindAddress = readConfig("server.bindAddress", "STARAXIS_SERVER_BIND_ADDRESS", "0.0.0.0");
        int port = readIntConfig("server.port", "STARAXIS_SERVER_PORT", 8080);

        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(bindAddress), port);
        HttpServer server = HttpServer.create(address, 0);
        server.setExecutor(Executors.newCachedThreadPool());

        HttpRoutes.register(server);

        System.out.println("StarAxis server starting...");
        System.out.println("bindAddress=" + bindAddress + ", port=" + port);
        System.out.println("NOTE: 未启用认证，仅开发/测试用途");

        server.start();
        System.out.println("StarAxis server started.");
    }

    private static String readConfig(String systemProperty, String envVar, String defaultValue) {
        String fromProp = System.getProperty(systemProperty);
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp.trim();
        }
        String fromEnv = System.getenv(envVar);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return defaultValue;
    }

    private static int readIntConfig(String systemProperty, String envVar, int defaultValue) {
        String v = readConfig(systemProperty, envVar, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
