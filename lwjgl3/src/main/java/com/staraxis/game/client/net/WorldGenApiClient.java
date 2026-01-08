package com.staraxis.game.client.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staraxis.game.shared.net.worldgen.SchemaVersions;
import com.staraxis.game.shared.net.worldgen.StartNewGameRequest;
import com.staraxis.game.shared.net.worldgen.StartNewGameResponse;

public class WorldGenApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String serverBaseUrl;

    public WorldGenApiClient(String serverBaseUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.serverBaseUrl = serverBaseUrl;
    }

    public StartNewGameResponse startNewGame(StartNewGameRequest request) throws Exception {
        String url = serverBaseUrl + "/api/worldgen/start-new-game";

        String json = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        StartNewGameResponse parsed = objectMapper.readValue(response.body(), StartNewGameResponse.class);

        // 客户端侧版本门禁：不匹配直接视为不可用
        // 客户端侧版本门禁：不匹配直接视为不可用
        if (parsed == null || parsed.getSchemaVersion() == null || !SchemaVersions.WORLDGEN_V2.equals(parsed.getSchemaVersion())) {
            StartNewGameResponse mismatch = new StartNewGameResponse();
            mismatch.setSchemaVersion(SchemaVersions.WORLDGEN_V2);
            mismatch.setError(new com.staraxis.game.shared.net.worldgen.ErrorEnvelope(
                    "SCHEMA_MISMATCH",
                    "worldgen.schema_mismatch",
                    "Client requires schema v2, but server sent: " + (parsed != null ? parsed.getSchemaVersion() : "null")));
            return mismatch;
        }

        return parsed;
    }
}
