package com.staraxis.game.server.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.staraxis.game.core.worldgen.StartNewGameUseCase;
import com.staraxis.game.shared.net.worldgen.ErrorEnvelope;
import com.staraxis.game.shared.net.worldgen.SchemaVersions;
import com.staraxis.game.shared.net.worldgen.StartNewGameRequest;
import com.staraxis.game.shared.net.worldgen.StartNewGameResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class StartNewGameHandler implements HttpHandler {

    private final StartNewGameUseCase useCase;

    public StartNewGameHandler() {
        this.useCase = new StartNewGameUseCase();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long start = System.currentTimeMillis();

        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                StartNewGameResponse response = new StartNewGameResponse();
                response.setSchemaVersion(SchemaVersions.WORLDGEN_V1);
                response.setError(new ErrorEnvelope("INVALID_JSON", "worldgen.invalid_json", "Only POST is supported"));
                writeJson(exchange, 400, response);
                return;
            }

            byte[] requestBytes = readAllBytes(exchange.getRequestBody());

            StartNewGameRequest request;
            try {
                request = JsonCodec.read(requestBytes, StartNewGameRequest.class);
            } catch (Exception e) {
                StartNewGameResponse response = new StartNewGameResponse();
                response.setSchemaVersion(SchemaVersions.WORLDGEN_V1);
                response.setError(new ErrorEnvelope("INVALID_JSON", "worldgen.invalid_json", e.getMessage()));
                writeJson(exchange, 400, response);
                return;
            }

            StartNewGameResponse response = useCase.execute(request);

            int status = 200;
            if (response.getError() != null) {
                String errorCode = response.getError().getErrorCode();
                if ("INVALID_MAP_PRESET".equals(errorCode) || "INVALID_JSON".equals(errorCode)) {
                    status = 400;
                } else {
                    status = 500;
                }
            }

            long seedValue = response.getWorld() != null ? response.getWorld().getSeedValue() : -1L;
            int bytes = writeJson(exchange, status, response);

            long durationMs = System.currentTimeMillis() - start;
            System.out.println("WorldGen: status=" + status + ", durationMs=" + durationMs + ", responseBytes=" + bytes + ", seedValue=" + seedValue);
        } catch (Exception e) {
            StartNewGameResponse response = new StartNewGameResponse();
            response.setSchemaVersion(SchemaVersions.WORLDGEN_V1);
            response.setError(new ErrorEnvelope("INTERNAL_ERROR", "worldgen.internal_error", e.toString()));

            try {
                int bytes = writeJson(exchange, 500, response);
                long durationMs = System.currentTimeMillis() - start;
                System.err.println("WorldGen: status=500, durationMs=" + durationMs + ", responseBytes=" + bytes + ", error=" + e);
            } catch (JsonProcessingException jsonException) {
                exchange.sendResponseHeaders(500, 0);
            }
        } finally {
            exchange.close();
        }
    }

    private int writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] body = JsonCodec.write(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
        return body.length;
    }

    private byte[] readAllBytes(InputStream in) throws IOException {
        return in.readAllBytes();
    }
}
