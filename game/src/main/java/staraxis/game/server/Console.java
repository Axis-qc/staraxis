package staraxis.game.server;

import java.util.Map;
import java.util.Scanner;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.ship.ShipBody;

/**
 * Console（开发者控制台）。
 *
 * 命令行交互式开发调试工具，用于在无客户端的情况下：
 * - 查询实体状态、星系结构、行星分布
 * - 监控性能（Tick 分解、内存、实体量）
 * - 触发逻辑验证（手动 tick、查询验证）
 * - 扩展新模块时即时测试
 *
 * 运行在独立线程，不阻塞主 Tick 循环。
 */
public class Console {

    private final StarAxisGameRuntime runtime;
    private final TickLoop tickLoop;
    private final ServerConfig config;
    private volatile boolean running = true;

    public Console(StarAxisGameRuntime runtime, TickLoop tickLoop, ServerConfig config) {
        this.runtime = runtime;
        this.tickLoop = tickLoop;
        this.config = config;
    }

    /**
     * 启动控制台（阻塞读取 stdin）。
     */
    public void start() {
        System.out.println("[Console] \u5f00\u53d1\u8005\u63a7\u5236\u53f0\u5df2\u542f\u52a8\u3002\u8f93\u5165 help \u67e5\u770b\u547d\u4ee4\u5217\u8868");
        try (Scanner scanner = new Scanner(System.in)) {
            while (running && scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;
                dispatch(line);
            }
        }
        System.out.println("[Console] \u5df2\u505c\u6b62");
    }

    // ========================================================================
    //  主分发
    // ========================================================================

    private void dispatch(String line) {
        try {
            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase();
            String[] args = new String[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, args.length);
            execute(cmd, args);
        } catch (Exception e) {
            System.out.println("[Console] \u6267\u884c\u5931\u8d25: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void execute(String cmd, String[] args) {
        switch (cmd) {
            case "help" -> printHelp();
            case "stop", "quit", "exit" -> stop();
            case "pause" -> tickLoop.setPaused(true);
            case "resume", "continue" -> tickLoop.setPaused(false);
            case "info" -> printInfo();
            case "seed" -> printSeed();
            case "entity", "e" -> printEntity(args);
            case "ships" -> listShips();
            case "system", "sys" -> printSystem(args);
            case "systems" -> printSystemsSummary();
            case "planet", "p" -> printPlanet(args);
            case "planets" -> printPlanetSample();
            case "perf" -> printPerf();
            case "memory", "mem" -> printMemory();
            case "tick" -> runTicks(args);
            case "query" -> queryEntities(args);
            case "types" -> listEntityTypes();
            default -> System.out.println("[Console] \u672a\u77e5\u547d\u4ee4: " + cmd + "\u3002\u8f93\u5165 help \u67e5\u770b\u5217\u8868");
        }
    }

    // ========================================================================
    //  帮助
    // ========================================================================

    private void printHelp() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("  StarAxis \u5f00\u53d1\u8005\u63a7\u5236\u53f0");
        System.out.println("========================================");
        System.out.println();
        System.out.println("-- \u5e2e\u52a9 --");
        System.out.println("  help                          \u663e\u793a\u6b64\u5e2e\u52a9");
        System.out.println();
        System.out.println("-- \u751f\u547d\u5468\u671f --");
        System.out.println("  stop / quit / exit            \u505c\u6b62\u670d\u52a1\u7aef");
        System.out.println("  pause                         \u6682\u505c Tick \u5faa\u73af");
        System.out.println("  resume                        \u7ee7\u7eed Tick \u5faa\u73af");
        System.out.println("  tick [n]                      \u624b\u52a8\u6267\u884c n \u4e2a Tick\uff08\u9ed8\u8ba4 1\uff09");
        System.out.println();
        System.out.println("-- \u4e16\u754c\u72b6\u6001 --");
        System.out.println("  info                          \u4e16\u754c\u6982\u8981\uff08Tick/\u5b9e\u4f53/\u7cfb\u7edf\uff09");
        System.out.println("  seed                          \u663e\u793a\u4e16\u754c\u79cd\u5b50");
        System.out.println();
        System.out.println("-- \u5b9e\u4f53\u67e5\u8be2 --");
        System.out.println("  entity <id> / e <id>          \u67e5\u770b\u4efb\u610f\u5b9e\u4f53\u8be6\u60c5\uff08\u5168\u5b57\u6bb5\uff09");
        System.out.println("  query [type]                  \u6309\u7c7b\u578b\u67e5\u8be2\u5b9e\u4f53\uff08star/planet/ship\uff09");
        System.out.println("  types                         \u5217\u51fa\u6240\u6709\u5b9e\u4f53\u7c7b\u578b\u53ca\u6570\u91cf");
        System.out.println("  ships                         \u5217\u51fa\u6240\u6709\u8230\u8239");
        System.out.println();
        System.out.println("-- \u661f\u7cfb\u67e5\u8be2 --");
        System.out.println("  system <id> / sys <id>        \u67e5\u770b\u6052\u661f\u7cfb\u8be6\u60c5\uff08\u6052\u661f+\u884c\u661f\u5217\u8868\uff09");
        System.out.println("  systems                       \u6240\u6709\u6052\u661f\u7cfb\u7edf\u8ba1\uff08\u884c\u661f\u5206\u5e03\uff09");
        System.out.println("  planet <id> / p <id>          \u67e5\u770b\u884c\u661f\u8be6\u60c5\uff08\u8f68\u9053/\u534a\u5f84/\u5730\u8868\uff09");
        System.out.println("  planets                       \u663e\u793a\u7b2c\u4e00\u4e2a\u6052\u661f\u7cfb\u7684\u884c\u661f\u5206\u5e03");
        System.out.println();
        System.out.println("-- \u6027\u80fd --");
        System.out.println("  perf                          \u67e5\u770b Tick \u6027\u80fd\u65e5\u5fd7\u4f4d\u7f6e");
        System.out.println("  memory / mem                  \u5185\u5b58\u4f7f\u7528\u72b6\u6001");
        System.out.println();
    }

    // ========================================================================
    //  生命周期
    // ========================================================================

    private void stop() {
        System.out.println("[Console] \u6b63\u5728\u505c\u6b62...");
        running = false;
        tickLoop.stop();
    }

    // ========================================================================
    //  概要
    // ========================================================================

    private void printInfo() {
        var ws = runtime.getWorldStateForSimOnly();
        if (ws == null) {
            System.out.println("[Info] \u4e16\u754c\u5c1a\u672a\u521d\u59cb\u5316");
            return;
        }
        int systemCount = ws.astro.getSystemsView().size();
        int entities = ws.entitiesById.size();
        long tick = ws.time.simulationTick;
        double seconds = ws.time.getTotalGameSeconds();
        int ships = 0, planets = 0, stars = 0, asteroids = 0, moons = 0;
        for (Entity e : ws.entitiesById.values()) {
            switch (e.entityType) {
                case STAR -> stars++;
                case PLANET -> planets++;
                case SHIP -> ships++;
                case ASTEROID -> asteroids++;
                case MOON -> moons++;
            }
        }
        Runtime rt = Runtime.getRuntime();
        long memMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        System.out.printf("[Info] Tick=%d \u2502 \u6e38\u620f\u65f6\u95f4=%.0fs \u2502 \u5b9e\u4f53=%d%n", tick, seconds, entities);
        System.out.printf("      \u6052\u661f=%d \u2502 \u884c\u661f=%d \u2502 \u5c0f\u884c\u661f=%d \u2502 \u536b\u661f=%d \u2502 \u8230\u8239=%d%n", stars, planets, asteroids, moons, ships);
        System.out.printf("      \u6052\u661f\u7cfb=%d \u2502 \u5185\u5b58=%dMB \u2502 TPS=%d \u2502 \u79cd\u5b50=%s \u2502 %s%n",
            systemCount, memMB, config.ticksPerSecond,
            config.worldSeed != null ? config.worldSeed : "(hashCode)",
            tickLoop.isPaused() ? "[\u2585\u6682\u505c]" : "[\u25b6\u8fd0\u884c\u4e2d]");
    }

    private void printSeed() {
        System.out.println("[Seed] \u4e16\u754c\u79cd\u5b50: " + (config.worldSeed != null ? config.worldSeed : "(hashCode)"));
    }

    // ========================================================================
    //  实体
    // ========================================================================

    private void printEntity(String[] args) {
        if (args.length < 1) {
            System.out.println("[Entity] \u7528\u6cd5: entity <id>");
            return;
        }
        long id = Long.parseLong(args[0]);
        var ws = runtime.getWorldStateForSimOnly();
        if (ws == null) { System.out.println("[Entity] \u4e16\u754c\u672a\u521d\u59cb\u5316"); return; }
        Entity e = ws.entitiesById.get(id);
        if (e == null) { System.out.println("[Entity] \u5b9e\u4f53 " + id + " \u4e0d\u5b58\u5728"); return; }

        System.out.println("===== \u5b9e\u4f53 " + id + " =====");
        System.out.println("  \u7c7b\u578b: " + e.entityType);
        System.out.println("  systemId: " + e.systemId);
        System.out.println("  parentEntityId: " + e.parentEntityId);
        System.out.println("  posWorldGU: (" + fmtD(e.posWorldGU.x()) + ", " + fmtD(e.posWorldGU.y()) + ", " + fmtD(e.posWorldGU.z()) + ")");
        System.out.println("  ownerNationId: " + e.ownerNationId);
        System.out.println("  ownerPlayerId: " + e.ownerPlayerId);

        if (e instanceof ShipBody s) {
            System.out.println("  -- \u8230\u8239\u8be6\u60c5 --");
            System.out.println("  designId: " + s.designId);
            System.out.println("  components: " + s.components.size());
            System.out.println("  hpHull: " + fmtD(s.hpHull));
        } else if (e instanceof PlanetBody p) {
            System.out.println("  -- \u884c\u661f\u8be6\u60c5 --");
            System.out.println("  planetTypeId: " + p.planetTypeId);
            System.out.println("  radiusGU: " + fmtD(p.radiusGU));
            System.out.println("  semiMajorAxisGU: " + fmtD(p.semiMajorAxisGU));
            System.out.println("  orbitalPeriodDays: " + fmtD(p.orbitalPeriodDays));
            System.out.println("  eccentricity: " + fmtD(p.eccentricity));
            System.out.println("  inclinationDeg: " + fmtD(p.inclinationDeg));
        } else if (e instanceof StarBody st) {
            System.out.println("  -- \u6052\u661f\u8be6\u60c5 --");
            System.out.println("  starTypeId: " + st.starTypeId);
            System.out.println("  temperatureK: " + st.temperatureK + "K");
            System.out.println("  massSolar: " + fmtD(st.massSolar));
            System.out.println("  radiusGU: " + fmtD(st.radiusGU));
        }
    }

    private void listShips() {
        var ws = runtime.getWorldStateForSimOnly();
        if (ws == null) return;
        var ships = ws.entitiesById.values().stream()
            .filter(e -> e instanceof ShipBody).map(e -> (ShipBody) e).toList();
        if (ships.isEmpty()) { System.out.println("[Ships] \u65e0\u8230\u8239"); return; }
        System.out.println("[Ships] \u5171 " + ships.size() + " \u8258:");
        for (ShipBody s : ships) {
            System.out.printf("  [%d] %s \u2502 pos=(%.0f,%.0f,%.0f) \u2502 hp=%.2f%n",
                s.entityId, s.designId,
                s.posWorldGU.x(), s.posWorldGU.y(), s.posWorldGU.z(), s.hpHull);
        }
    }

    // ========================================================================
    //  星系
    // ========================================================================

    private void printSystem(String[] args) {
        if (args.length < 1) { System.out.println("[System] \u7528\u6cd5: system <id>"); return; }
        long id = Long.parseLong(args[0]);
        var ws = runtime.getWorldStateForSimOnly();
        if (ws == null) return;
        StarSystem sys = null;
        for (StarSystem s : ws.astro.getSystemsView()) {
            if (s.systemId == id) { sys = s; break; }
        }
        if (sys == null) { System.out.println("[System] \u6052\u661f\u7cfb " + id + " \u4e0d\u5b58\u5728"); return; }
        System.out.println("===== \u6052\u661f\u7cfb " + id + " =====");
        for (StarBody star : sys.stars) {
            System.out.printf("  \u6052\u661f [%d] %s \u2502 T=%dK \u2502 R=%.0fGU \u2502 M=%.2fMsol%n",
                star.entityId, star.starTypeId, star.temperatureK, star.radiusGU, star.massSolar);
        }
        System.out.println("  \u884c\u661f " + sys.planets.size() + " \u9917:");
        for (int i = 0; i < sys.planets.size(); i++) {
            PlanetBody p = sys.planets.get(i);
            // 统计该行星的卫星数
            int moonCount = 0;
            for (PlanetBody m : sys.moons) {
                if (m.orbitCenterEntityId == p.entityId) moonCount++;
            }
            System.out.printf("  [%d] %-14s \u2502 orbit=%7.0fGU \u2502 R=%.0fGU \u2502 e=%.3f \u2502 moons=%d%n",
                i + 1, p.planetTypeId, p.semiMajorAxisGU, p.radiusGU, p.eccentricity, moonCount);
        }
        if (!sys.asteroids.isEmpty()) {
            System.out.println("  \u5c0f\u884c\u661f: " + sys.asteroids.size() + " \u9897");
        }
        if (!sys.moons.isEmpty()) {
            System.out.println("  \u536b\u661f: " + sys.moons.size() + " \u9897");
        }
    }

    private void printSystemsSummary() {
        var ws = runtime.getWorldStateForSimOnly();
        if (ws == null || ws.astro.getSystemsView().isEmpty()) {
            System.out.println("[Systems] \u65e0\u6052\u661f\u7cfb\u6570\u636e"); return;
        }
        var systems = ws.astro.getSystemsView();
        int totalPlanets = 0, minPlanets = Integer.MAX_VALUE, maxPlanets = 0;
        int totalAsteroids = 0, totalMoons = 0;
        java.util.Map<String, Integer> typeCount = new java.util.HashMap<>();

        System.out.println("[Systems] \u884c\u661f\u5206\u5e03\uff08\u524d5\u4e2a\u7cfb\u7edf\uff09:");
        for (int si = 0; si < Math.min(5, systems.size()); si++) {
            StarSystem s = systems.get(si);
            StringBuilder sb = new StringBuilder("  sys#").append(s.systemId).append(":");
            for (PlanetBody p : s.planets) sb.append(" [").append(p.planetTypeId).append("]");
            if (!s.asteroids.isEmpty()) sb.append(" +ast(").append(s.asteroids.size()).append(")");
            if (!s.moons.isEmpty()) sb.append(" +moon(").append(s.moons.size()).append(")");
            System.out.println(sb);
        }
        for (StarSystem s : systems) {
            int c = s.planets.size();
            totalPlanets += c;
            minPlanets = Math.min(minPlanets, c);
            maxPlanets = Math.max(maxPlanets, c);
            totalAsteroids += s.asteroids.size();
            totalMoons += s.moons.size();
            for (PlanetBody p : s.planets) typeCount.merge(p.planetTypeId, 1, Integer::sum);
        }
        int sysCount = systems.size();
        double avg = (double) totalPlanets / sysCount;
        int totalFinal = totalPlanets;
        System.out.printf("[Systems] \u6052\u661f\u7cfb=%d \u2502 \u884c\u661f=%d \u2502 \u5c0f\u884c\u661f=%d \u2502 \u536b\u661f=%d \u2502 \u5747\u503c=%.2f \u2502 \u8303\u56f4=%d~%d%n",
            sysCount, totalPlanets, totalAsteroids, totalMoons, avg, minPlanets, maxPlanets);
        System.out.println("[Systems] \u884c\u661f\u7c7b\u578b\u5206\u5e03:");
        typeCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(e -> System.out.printf("  %s: %d (%.1f%%)%n", e.getKey(), e.getValue(), 100.0 * e.getValue() / totalFinal));
    }

    private void printPlanet(String[] args) {
        if (args.length < 1) { System.out.println("[Planet] \u7528\u6cd5: planet <id>"); return; }
        long id = Long.parseLong(args[0]);
        var ws = runtime.getWorldStateForSimOnly();
        if (ws == null) return;
        Entity e = ws.entitiesById.get(id);
        if (!(e instanceof PlanetBody p)) { System.out.println("[Planet] " + id + " \u4e0d\u662f\u884c\u661f"); return; }
        double fraction = 0;
        for (StarSystem sys : ws.astro.getSystemsView()) {
            if (sys.systemId == p.systemId && !sys.stars.isEmpty()) {
                fraction = p.semiMajorAxisGU / sys.stars.get(0).radiusGU;
                break;
            }
        }
        System.out.println("===== \u884c\u661f " + id + " =====");
        System.out.println("  \u7c7b\u578b: " + p.planetTypeId);
        System.out.println("  \u534a\u5f84: " + fmtD(p.radiusGU) + " GU");
        System.out.println("  \u8f68\u9053: " + fmtD(p.semiMajorAxisGU) + " GU \u2502 \u8f68\u9053\u6bd4\u4f8b=" + String.format("%.1f", fraction * 100) + "%");
        System.out.println("  \u516c\u8f6c\u5468\u671f: " + fmtD(p.orbitalPeriodDays) + " \u5929 \u2502 \u504f\u5fc3\u7387=" + String.format("%.4f", p.eccentricity));
        System.out.println("  \u7eb9\u7406: " + p.surfaceTexturePath);
        System.out.println("  \u5f52\u5c5e: nation=" + p.ownerNationId + " player=" + p.ownerPlayerId);
    }

    private void printPlanetSample() {
        var ws = runtime.getWorldStateForSimOnly();
        if (ws == null || ws.astro.getSystemsView().isEmpty()) {
            System.out.println("[Planets] \u65e0\u6052\u661f\u7cfb\u6570\u636e"); return;
        }
        StarSystem sys = ws.astro.getSystemsView().get(0);
        StarBody star = sys.stars.isEmpty() ? null : sys.stars.get(0);
        System.out.println("[Planets] \u6052\u661f\u7cfb #" + sys.systemId);
        if (star != null) System.out.printf("  \u6052\u661f: [%d] %s \u2502 T=%dK%n", star.entityId, star.starTypeId, star.temperatureK);
        System.out.println("  \u884c\u661f " + sys.planets.size() + " \u9917:");
        for (int i = 0; i < sys.planets.size(); i++) {
            PlanetBody p = sys.planets.get(i);
            double f = star != null ? p.semiMajorAxisGU / star.radiusGU : 0;
            String zone = f < 0.12 ? "\u5185\u5c42" : f < 0.35 ? "\u5b9c\u5c45" : f < 0.65 ? "\u5916\u5c42" : "\u8fb9\u7f18";
            int moonCount = 0;
            for (PlanetBody m : sys.moons) { if (m.orbitCenterEntityId == p.entityId) moonCount++; }
            String moonTag = moonCount > 0 ? " \u2502 " + moonCount + "\u9897\u536b\u661f" : "";
            System.out.printf("  [%d] %-14s \u2502 orbit=%7.0fGU \u2502 R=%.0fGU \u2502 %s%s%n",
                i + 1, p.planetTypeId, p.semiMajorAxisGU, p.radiusGU, zone, moonTag);
        }
        if (!sys.asteroids.isEmpty()) {
            System.out.println("  \u5c0f\u884c\u661f " + sys.asteroids.size() + " \u9897 (\u663e\u793a\u524d5):");
            for (int i = 0; i < Math.min(5, sys.asteroids.size()); i++) {
                PlanetBody a = sys.asteroids.get(i);
                System.out.printf("    %-14s \u2502 orbit=%7.0fGU \u2502 R=%.0fGU%n",
                    a.planetTypeId, a.semiMajorAxisGU, a.radiusGU);
            }
        }
    }

    // ========================================================================
    //  性能
    // ========================================================================

    private void printPerf() {
        System.out.println("[Perf] Tick \u6027\u80fd\u65e5\u5fd7: gamedata/logs/perf_tick.log");
        System.out.println("  \u6bcf Tick \u8bb0\u5f55\u5404\u9636\u6bb5\u8017\u65f6\uff08timeline/arrivals/octree/command/movement/snapshot\uff09");
    }

    private void printMemory() {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory() / 1024 / 1024;
        long free = rt.freeMemory() / 1024 / 1024;
        long max = rt.maxMemory() / 1024 / 1024;
        System.out.printf("[Memory] \u4f7f\u7528=%dMB \u2502 \u5206\u914d=%dMB \u2502 \u6700\u5927=%dMB \u2502 \u5360\u7528=%d%%%n",
            total - free, total, max, (int)((double)(total - free) / max * 100));
    }

    // ========================================================================
    //  世界操作
    // ========================================================================

    private void runTicks(String[] args) {
        int count = 1;
        if (args.length >= 1) try { count = Integer.parseInt(args[0]); } catch (NumberFormatException ignore) {}
        float dt = 1.0f / config.ticksPerSecond;
        System.out.println("[Tick] \u624b\u52a8\u6267\u884c " + count + " \u4e2a Tick...");
        for (int i = 0; i < count; i++) runtime.update(dt);
        var ws = runtime.getWorldStateForSimOnly();
        long tick = ws != null ? ws.time.simulationTick : 0;
        int entities = ws != null ? ws.entitiesById.size() : 0;
        System.out.println("[Tick] \u5b8c\u6210 \u2502 Tick=" + tick + " \u2502 \u5b9e\u4f53=" + entities);
    }

    // ========================================================================
    //  数据查询
    // ========================================================================

    private void queryEntities(String[] args) {
        var ws = runtime.getWorldStateForSimOnly();
        if (ws == null) return;
        EntityType filter = null;
        if (args.length >= 1) {
            try { filter = EntityType.valueOf(args[0].toUpperCase()); } catch (IllegalArgumentException ignore) {}
        }
        var entities = ws.entitiesById.values();
        java.util.Map<EntityType, Long> counts = new java.util.HashMap<>();
        for (Entity e : entities) counts.merge(e.entityType, 1L, Long::sum);

        if (filter != null) {
            long c = counts.getOrDefault(filter, 0L);
            int shown = 0;
            System.out.println("[Query] " + filter + ": " + c + " \u4e2a");
            for (Entity e : entities) {
                if (e.entityType != filter) continue;
                if (shown >= 20) { System.out.println("  ... \u8fd8\u6709 " + (c - 20) + " \u4e2a"); break; }
                shown++;
                String desc = switch (e) {
                    case ShipBody s -> "design=" + s.designId;
                    case PlanetBody p -> p.planetTypeId;
                    case StarBody st -> st.starTypeId;
                    default -> "";
                };
                System.out.println("  [" + e.entityId + "] " + desc);
            }
        } else {
            System.out.println("[Query] \u5b9e\u4f53\u7edf\u8ba1\uff08\u5171 " + ws.entitiesById.size() + " \u4e2a\uff09:");
            counts.entrySet().stream()
                .sorted(Map.Entry.<EntityType, Long>comparingByValue().reversed())
                .forEach(e2 -> System.out.println("  " + e2.getKey() + ": " + e2.getValue()));
        }
    }

    private void listEntityTypes() { queryEntities(new String[0]); }

    // ========================================================================
    //  工具
    // ========================================================================

    private static String fmtD(double v) {
        if (v == (long) v) return String.valueOf((long) v);
        return String.format("%.2f", v);
    }
}
