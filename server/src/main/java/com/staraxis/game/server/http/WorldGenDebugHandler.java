package com.staraxis.game.server.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.staraxis.game.core.world.DefaultWorldGenerator;
import com.staraxis.game.core.world.WorldGenerator;
import com.staraxis.game.core.world.stellar.orbit.OrbitPathService;
import com.staraxis.game.core.world.stellar.surface.PlanetSurfaceMeshGenerator;
import com.staraxis.game.shared.model.Vector2;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldGenDefinitions;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.stellar.Planet;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;
import com.staraxis.game.shared.world.stellar.WorldGenStats;
import com.staraxis.game.shared.world.stellar.orbit.OrbitPath;
import com.staraxis.game.shared.world.stellar.orbit.OrbitPrecisionLevel;
import com.staraxis.game.shared.world.stellar.surface.MeshResolutionLevel;
import com.staraxis.game.shared.world.stellar.surface.PlanetSurfaceMesh;
import com.staraxis.game.shared.world.stellar.surface.SurfaceTile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class WorldGenDebugHandler implements HttpHandler {

    private static volatile WorldMap lastWorld;
    private static volatile Map<String, StarSystem> systemsById = Map.of();
    private static volatile Map<String, Planet> planetsById = Map.of();

    private final String routeKind;
    private final WorldGenerator worldGenerator;
    private final OrbitPathService orbitPathService;
    private final PlanetSurfaceMeshGenerator meshGenerator;

    public WorldGenDebugHandler(String routeKind) {
        this(routeKind, new DefaultWorldGenerator(), new OrbitPathService(), new PlanetSurfaceMeshGenerator());
    }

    WorldGenDebugHandler(String routeKind, WorldGenerator worldGenerator, OrbitPathService orbitPathService,
            PlanetSurfaceMeshGenerator meshGenerator) {
        this.routeKind = routeKind;
        this.worldGenerator = worldGenerator;
        this.orbitPathService = orbitPathService;
        this.meshGenerator = meshGenerator;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if ("world-generate".equals(routeKind)) {
                handleWorldGenerate(exchange);
                return;
            }
            if ("system-orbit-paths".equals(routeKind)) {
                handleSystemOrbitPaths(exchange);
                return;
            }
            if ("planet-surface-mesh".equals(routeKind)) {
                handlePlanetSurfaceMesh(exchange);
                return;
            }
            writeJson(exchange, 404, Map.of("error", "not_found"));
        } finally {
            exchange.close();
        }
    }

    private void handleWorldGenerate(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }

        byte[] body = readAllBytes(exchange.getRequestBody());
        WorldGenRequest request;
        try {
            request = JsonCodec.read(body, WorldGenRequest.class);
        } catch (Exception ex) {
            writeJson(exchange, 400, Map.of("error", "invalid_json", "message", String.valueOf(ex.getMessage())));
            return;
        }

        if (request == null || request.mapSizePresetId == null || request.mapSizePresetId.isBlank()) {
            writeJson(exchange, 400, Map.of("error", "invalid_request", "message", "mapSizePresetId is required"));
            return;
        }

        if (!WorldGenDefinitions.getMapPresets().containsKey(request.mapSizePresetId)) {
            writeJson(exchange, 400,
                    Map.of("error", "invalid_map_preset", "message", "Unknown mapSizePresetId: " + request.mapSizePresetId));
            return;
        }

        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId(request.mapSizePresetId);
        config.setSeedValue(request.seedValue);

        if (request.habitableRatio != null) {
            config.setHabitableRatio(request.habitableRatio);
        }
        if (request.starDensity != null) {
            config.setStarDensity(request.starDensity);
        }
        if (request.nebulaRatio != null) {
            config.setNebulaRatio(request.nebulaRatio);
        }
        if (request.planetComplexity != null) {
            config.setPlanetComplexity(request.planetComplexity);
        }

        WorldMap world = worldGenerator.generate(config);
        storeWorld(world);

        WorldGenResponse response = new WorldGenResponse();
        response.stats = toStatsEnvelope(world.getStats());
        response.systemIds = new ArrayList<>(systemsById.keySet());
        writeJson(exchange, 200, response);
    }

    private void handleSystemOrbitPaths(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }

        String systemId = extractPathParam(exchange.getRequestURI(), "star-system", "orbit-paths");
        if (systemId == null) {
            writeJson(exchange, 400, Map.of("error", "invalid_path"));
            return;
        }

        StarSystem system = systemsById.get(systemId);
        if (system == null) {
            writeJson(exchange, 404, Map.of("error", "not_found", "message", "Unknown systemId: " + systemId));
            return;
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI());
        String precisionRaw = query.get("precision");
        if (precisionRaw == null) {
            writeJson(exchange, 400, Map.of("error", "invalid_request", "message", "precision is required"));
            return;
        }

        OrbitPrecisionLevel precision;
        try {
            precision = parsePrecision(precisionRaw);
        } catch (RuntimeException ex) {
            writeJson(exchange, 400, Map.of("error", "invalid_request", "message", ex.getMessage()));
            return;
        }

        List<OrbitPath> paths = orbitPathService.generateOrbitPaths(system, precision);
        List<OrbitPathEnvelope> response = new ArrayList<>(paths.size());
        for (OrbitPath p : paths) {
            response.add(toOrbitPathEnvelope(p));
        }

        writeJson(exchange, 200, response);
    }

    private void handlePlanetSurfaceMesh(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }

        String planetId = extractPathParam(exchange.getRequestURI(), "planet", "surface-mesh");
        if (planetId == null) {
            writeJson(exchange, 400, Map.of("error", "invalid_path"));
            return;
        }

        Planet planet = planetsById.get(planetId);
        if (planet == null) {
            writeJson(exchange, 404, Map.of("error", "not_found", "message", "Unknown planetId: " + planetId));
            return;
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI());
        String resolutionRaw = query.get("resolution");
        if (resolutionRaw == null) {
            writeJson(exchange, 400, Map.of("error", "invalid_request", "message", "resolution is required"));
            return;
        }

        MeshResolutionLevel resolution;
        try {
            resolution = parseResolution(resolutionRaw);
        } catch (RuntimeException ex) {
            writeJson(exchange, 400, Map.of("error", "invalid_request", "message", ex.getMessage()));
            return;
        }

        PlanetSurfaceMesh mesh = meshGenerator.generate(resolution);
        writeJson(exchange, 200, toSurfaceMeshEnvelope(mesh));
    }

    private static void storeWorld(WorldMap world) {
        lastWorld = world;

        Map<String, StarSystem> sys = new HashMap<>();
        Map<String, Planet> pls = new HashMap<>();

        if (world != null && world.getTiles() != null) {
            for (HexTile t : world.getTiles().values()) {
                if (t == null || t.getStarSystem() == null) {
                    continue;
                }
                StarSystem s = t.getStarSystem();
                if (s.getId() != null) {
                    sys.put(s.getId(), s);
                }
                for (Star star : s.getStars()) {
                    for (Planet p : star.getPlanets()) {
                        if (p.getId() != null) {
                            pls.put(p.getId(), p);
                        }
                    }
                }
            }
        }

        systemsById = Map.copyOf(sys);
        planetsById = Map.copyOf(pls);
    }

    private static String extractPathParam(URI uri, String prefix, String suffix) {
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        if (path == null) {
            return null;
        }
        String normalized = path;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String[] parts = normalized.split("/");
        if (parts.length < 3) {
            return null;
        }
        if (!prefix.equals(parts[0]) || !suffix.equals(parts[2])) {
            return null;
        }
        return parts[1];
    }

    private static Map<String, String> parseQuery(URI uri) {
        if (uri == null) {
            return Map.of();
        }
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        Map<String, String> m = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String k = pair.substring(0, idx);
            String v = pair.substring(idx + 1);
            if (!k.isBlank()) {
                m.put(k, v);
            }
        }
        return m;
    }

    private static OrbitPrecisionLevel parsePrecision(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "low" ->
                OrbitPrecisionLevel.LOW;
            case "medium" ->
                OrbitPrecisionLevel.MEDIUM;
            case "high" ->
                OrbitPrecisionLevel.HIGH;
            default ->
                throw new IllegalArgumentException("Unsupported precision: " + raw);
        };
    }

    private static MeshResolutionLevel parseResolution(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "low" ->
                MeshResolutionLevel.LOW;
            case "medium" ->
                MeshResolutionLevel.MEDIUM;
            case "high" ->
                MeshResolutionLevel.HIGH;
            default ->
                throw new IllegalArgumentException("Unsupported resolution: " + raw);
        };
    }

    private static OrbitPathEnvelope toOrbitPathEnvelope(OrbitPath path) {
        OrbitPathEnvelope env = new OrbitPathEnvelope();
        env.orbitId = path.getOrbitId();
        env.precisionLevel = path.getPrecisionLevel().name().toLowerCase(Locale.ROOT);
        env.samples = new ArrayList<>();
        for (Vector2 s : path.getSamples()) {
            Vec2 v = new Vec2();
            v.x = s.x;
            v.y = s.y;
            env.samples.add(v);
        }
        return env;
    }

    private static SurfaceMeshEnvelope toSurfaceMeshEnvelope(PlanetSurfaceMesh mesh) {
        SurfaceMeshEnvelope env = new SurfaceMeshEnvelope();
        env.resolutionLevel = mesh.getResolutionLevel().name().toLowerCase(Locale.ROOT);
        env.tiles = new ArrayList<>();
        for (SurfaceTile t : mesh.getTiles()) {
            SurfaceTileEnvelope te = new SurfaceTileEnvelope();
            te.tileId = t.getTileId();
            te.tileType = t.isPentagon() ? "pent" : "hex";
            te.neighbors = new ArrayList<>(t.getNeighborTileIds());
            env.tiles.add(te);
        }
        return env;
    }

    private static StatsEnvelope toStatsEnvelope(WorldGenStats stats) {
        StatsEnvelope s = new StatsEnvelope();
        if (stats == null) {
            return s;
        }
        s.tileCount = stats.getTileCount();
        s.galaxyTileCount = stats.getGalaxyTileCount();
        s.starCount = stats.getStarCount();
        s.planetCount = stats.getPlanetCount();
        return s;
    }

    private int writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] out = JsonCodec.write(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
        return out.length;
    }

    private byte[] readAllBytes(InputStream in) throws IOException {
        return in.readAllBytes();
    }

    static final class WorldGenRequest {

        public String mapSizePresetId;
        public long seedValue;
        public Float habitableRatio;
        public Float starDensity;
        public Float nebulaRatio;
        public Float planetComplexity;
    }

    static final class WorldGenResponse {

        public StatsEnvelope stats;
        public List<String> systemIds;
    }

    static final class StatsEnvelope {

        public int tileCount;
        public int galaxyTileCount;
        public int starCount;
        public int planetCount;
    }

    static final class OrbitPathEnvelope {

        public String orbitId;
        public String precisionLevel;
        public List<Vec2> samples;
    }

    static final class Vec2 {

        public float x;
        public float y;
    }

    static final class SurfaceMeshEnvelope {

        public String resolutionLevel;
        public List<SurfaceTileEnvelope> tiles;
    }

    static final class SurfaceTileEnvelope {

        public String tileId;
        public String tileType;
        public List<String> neighbors;
    }
}
