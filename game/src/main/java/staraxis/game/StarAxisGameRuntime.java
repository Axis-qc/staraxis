package staraxis.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.astro.AstroData;
import staraxis.game.astro.AstroGenerator;
import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.astro.def.AstroAssetRepository;
import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.sim.SimulationClock;
import staraxis.game.sim.SimulationTime;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.DailySettlementStateBuffer;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.RealTimeWorldStateBuffer;
import staraxis.game.state.WorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.world.WorldGenConfig;
import staraxis.game.world.WorldGenerator;
import staraxis.game.world.WorldSector;
import staraxis.game.command.CommandBus;
import staraxis.game.command.SetPlayerTimeStepCommand;
import staraxis.game.command.SetPlayerTimeStepHandler;
import staraxis.game.command.SetSystemTimeScaleCommand;
import staraxis.game.command.SetSystemTimeScaleHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * StarAxisGameRuntime
 *
 * 最小可用权威运行时：
 * - simulationTick 固定脉冲
 * - 游戏时间推进 dtGameHours
 * - tick 结束发布 RealTimeWorldState（双缓冲）
 * - 跨日发布 DailySettlementState
 */
public class StarAxisGameRuntime implements GameRuntime {

    private final WorldState worldState;

    private final RealTimeWorldStateBuffer realTimeBuffer = new RealTimeWorldStateBuffer();

    private final DailySettlementStateBuffer dailySettlementBuffer = new DailySettlementStateBuffer();

    private final CommandBus commandBus = new CommandBus();

    public StarAxisGameRuntime(WorldState worldState) {
        this.worldState = worldState;

        commandBus.register(SetPlayerTimeStepCommand.class, new SetPlayerTimeStepHandler());
        commandBus.register(SetSystemTimeScaleCommand.class, new SetSystemTimeScaleHandler());
    }

    public CommandBus getCommandBusForSimOnly() {
        return commandBus;
    }

    public static StarAxisGameRuntime newGame(WorldGenConfig cfg) {
        SimulationTime time = new SimulationTime();

        var worldMap = WorldGenerator.generate(cfg);

        AstroAssetRepository astroAssets = new AstroAssetRepository(new ObjectMapper());
        astroAssets.loadAll();

        staraxis.game.planet.def.PlanetAssetRepository planetAssets = new staraxis.game.planet.def.PlanetAssetRepository(
                new ObjectMapper());
        planetAssets.loadAll();

        AstroGenerator astroGenerator = new AstroGenerator(astroAssets, planetAssets, cfg.worldSeed);
        List<StarSystem> systems = astroGenerator.generateSystemsForMap(worldMap, cfg);

        AstroData astro = new AstroData(systems);
        WorldState ws = new WorldState(time, worldMap, astro);

        // 初始化玩家国家并绑定出生点喵
        if (cfg.playerNationDef != null && cfg.playerNationDef.id != null && !cfg.playerNationDef.id.isBlank()) {
            String nationId = cfg.playerNationDef.id;
            if (!ws.nationManager.hasNation(nationId)) {
                ws.nationManager.registerNation(nationId);
            }
            var ns = ws.nationManager.getNationState(nationId);
            if (ns != null) {
                ns.name = cfg.playerNationDef.name;
                ns.governmentId = cfg.playerNationDef.governmentId;
            }

            long spawnSystemId = 0;
            String mode = cfg.playerNationDef.spawnStrategy == null ? null : cfg.playerNationDef.spawnStrategy.mode;
            if (mode == null || mode.isBlank()) {
                mode = staraxis.game.nation.NationDef.SpawnStrategy.MODE_RANDOM;
            }

            if (staraxis.game.nation.NationDef.SpawnStrategy.MODE_PRESET.equals(mode)) {
                String presetId = cfg.playerNationDef.spawnStrategy == null ? null
                        : cfg.playerNationDef.spawnStrategy.presetSystemId;
                if (presetId != null && !presetId.isBlank()) {
                    Long sid = astroGenerator.getPresetToSystemIdMap().get(presetId);
                    if (sid != null) {
                        spawnSystemId = sid;
                    }
                }
            }

            if (spawnSystemId == 0) {
                // random：从未归属星系中确定性选择一个喵
                java.util.ArrayList<StarSystem> candidates = new java.util.ArrayList<>();
                for (StarSystem sys : systems) {
                    if (sys == null) {
                        continue;
                    }
                    boolean owned = false;
                    for (StarBody star : sys.stars) {
                        if (star != null && star.ownerNationId != null && !star.ownerNationId.isBlank()) {
                            owned = true;
                            break;
                        }
                    }
                    if (!owned) {
                        for (PlanetBody planet : sys.planets) {
                            if (planet != null && planet.ownerNationId != null && !planet.ownerNationId.isBlank()) {
                                owned = true;
                                break;
                            }
                        }
                    }
                    if (!owned) {
                        candidates.add(sys);
                    }
                }

                if (!candidates.isEmpty()) {
                    long mixed = astroGenerator.getWorldSeedHash() ^ (long) nationId.hashCode();
                    java.util.Random rr = new java.util.Random(mixed);
                    spawnSystemId = candidates.get(rr.nextInt(candidates.size())).systemId;
                }
            }

            if (ns != null) {
                ns.spawnSystemEntityId = spawnSystemId;
            }

            // 将出生星系内的天体归属到该国，并确定性选择首都行星喵
            if (spawnSystemId != 0) {
                for (StarSystem sys : systems) {
                    if (sys == null || sys.systemId != spawnSystemId) {
                        continue;
                    }
                    sys.assignOwnership(nationId);

                    // 确定性选择首都行星：选择星系内 entityId 最小的行星喵
                    if (ns != null && !sys.planets.isEmpty()) {
                        PlanetBody capital = null;
                        for (PlanetBody p : sys.planets) {
                            if (capital == null || p.entityId < capital.entityId) {
                                capital = p;
                            }
                        }
                        if (capital != null) {
                            ns.capitalPlanetEntityId = capital.entityId;
                        }
                    }
                    break;
                }
            }
        }

        return new StarAxisGameRuntime(ws);
    }

    @Override
    public void start() {
        publishRealTimeSnapshot();
        // 开局先发布一份“上一日结算”（占位）：settledDay=当前日-1（下限 0）
        publishDailySettlementForDay(Math.max(0, worldState.time.gameDatetimeDay - 1));
    }

    @Override
    public void update(float dtSeconds) {
        // PrepareTick
        double dtGameHours = SimulationClock.prepareTick(worldState.time);

        // 处理 Command 队列并更新 WorldState
        commandBus.executeCommands(worldState, dtGameHours);
        // TODO: 各系统使用 dtGameHours 推进

        // 更新所有国家的可见性状态（基于当前世界状态）
        worldState.visibilitySystem.updateAllNationsVisibility();

        // Commit
        boolean dayChanged = SimulationClock.commitTick(worldState.time);
        if (dayChanged) {
            // 跨日结算：发布“上一日已落账结果”
            int settledDay = Math.max(0, worldState.time.gameDatetimeDay - 1);
            publishDailySettlementForDay(settledDay);
        }

        publishRealTimeSnapshot();
    }

    @Override
    public void stop() {
    }

    public WorldState getWorldStateForSimOnly() {
        return worldState;
    }

    public RealTimeWorldState getRealTimeWorldStateReadonly() {
        return realTimeBuffer.getActive();
    }

    public DailySettlementStateBuffer getDailySettlementStateBufferForReadonly() {
        return dailySettlementBuffer;
    }

    private void publishDailySettlementForDay(int settledDay) {
        DailySettlementState next = new DailySettlementState();
        next.settledDay = settledDay;
        next.sectorCount = worldState.worldMap.getSectorsByCoordView().size();

        // 选项 A：全量每日报送低频/静态数据喵
        HashMap<Long, DailySettlementState.PlanetSurfaceDailySnapshot> map = new HashMap<>();
        for (StarSystem system : worldState.astro.getSystemsView()) {
            for (PlanetBody planet : system.planets) {
                if (planet.surface == null || planet.surface.surfaceRegions == null
                        || planet.surface.surfaceRegions.isEmpty()) {
                    continue;
                }

                ArrayList<DailySettlementState.SurfaceRegionDailySnapshot> regions = new ArrayList<>(
                        planet.surface.surfaceRegions.size());
                for (staraxis.game.planet.surface.SurfaceRegion r : planet.surface.surfaceRegions) {
                    regions.add(new DailySettlementState.SurfaceRegionDailySnapshot(
                            r.regionId,
                            r.regionType,
                            r.name,
                            r.surfacePercentage,
                            r.developableSpaceRatio));
                }

                map.put(planet.entityId,
                        new DailySettlementState.PlanetSurfaceDailySnapshot(planet.entityId, List.copyOf(regions)));
            }
        }
        next.planetSurfacesByPlanetId = map;

        dailySettlementBuffer.publish(next);
    }

    private void publishRealTimeSnapshot() {
        RealTimeWorldState s = realTimeBuffer.beginFillInactive();

        s.simulationTick = worldState.time.simulationTick;
        s.gameDatetimeDay = worldState.time.gameDatetimeDay;
        s.accGameHoursInDay = worldState.time.accGameHoursInDay;
        s.worldRadius = worldState.worldMap.radius;

        int sectorCount = 0;
        for (WorldSector sector : worldState.worldMap.getSectorsView()) {
            s.putSectorCenter(sector.coord, sector.centerWorldGU);
            s.putSectorOwnerNationId(sector.coord, sector.ownerNationId);
            sectorCount++;
        }
        // 调试日志：记录添加到快照的星区数量喵
        // System.out.println("[StarAxisGameRuntime] Added " + sectorCount + " sectors
        // to realtime snapshot");

        for (StarSystem system : worldState.astro.getSystemsView()) {
            // 1. 创建并注册重心实体
            Entity barycenter = new Entity();
            barycenter.entityId = system.barycenterEntityId;
            barycenter.entityType = EntityType.SYSTEM_BARYCENTER;
            barycenter.systemId = system.systemId;
            barycenter.parentEntityId = 0;
            barycenter.sectorCoord = system.sectorCoord;
            barycenter.posWorldGU = system.centerWorldGU;

            // 权威注册到 WorldState 喵
            worldState.registerEntity(barycenter);

            s.putEntity(barycenter);
            s.putEntitySnapshot(new EntitySnapshot(
                    barycenter.entityId,
                    barycenter.entityType,
                    barycenter.systemId,
                    barycenter.parentEntityId,
                    barycenter.sectorCoord,
                    barycenter.posWorldGU,
                    new EntitySnapshot.SystemBarycenterDetails()));

            // 2. 注册恒星实体
            for (StarBody star : system.stars) {
                star.systemId = system.systemId;
                star.parentEntityId = system.barycenterEntityId; // 单星系统也挂在重心下
                star.sectorCoord = system.sectorCoord;
                star.posWorldGU = system.centerWorldGU; // 单星系统：恒星位置=重心位置

                // 权威注册到 WorldState 喵
                worldState.registerEntity(star);

                s.putEntity(star);
                s.putEntitySnapshot(new EntitySnapshot(
                        star.entityId,
                        star.entityType,
                        star.systemId,
                        star.parentEntityId,
                        star.sectorCoord,
                        star.posWorldGU,
                        new EntitySnapshot.StarDetails(star.starTypeId, star.radiusGU, star.massSolar,
                                star.temperatureK, star.description, star.surfaceTexturePath, star.ownerNationId)));
            }

            // 3. 注册行星实体
            for (PlanetBody planet : system.planets) {
                // 修正：补全行星的 systemId/parentEntityId/sectorCoord/posWorldGU，确保能通过星区过滤并正确渲染喵
                planet.systemId = system.systemId;
                planet.parentEntityId = system.barycenterEntityId;
                planet.sectorCoord = system.sectorCoord;
                planet.posWorldGU = system.centerWorldGU;

                // 权威注册到 WorldState 喵
                worldState.registerEntity(planet);

                s.putEntity(planet);

                // 确定性判断是否为首都喵
                boolean isCapital = false;
                if (planet.ownerNationId != null) {
                    var ns = worldState.nationManager.getNationState(planet.ownerNationId);
                    if (ns != null && ns.capitalPlanetEntityId == planet.entityId) {
                        isCapital = true;
                    }
                }

                s.putEntitySnapshot(new EntitySnapshot(
                        planet.entityId,
                        planet.entityType,
                        planet.systemId,
                        planet.parentEntityId,
                        planet.sectorCoord,
                        planet.posWorldGU,
                        new EntitySnapshot.PlanetDetails(
                                planet.planetTypeId,
                                planet.radiusGU,
                                planet.rotationPeriodHours,
                                planet.surfaceTexturePath, planet.ownerNationId,
                                isCapital,
                                planet.orbitCenterEntityId,
                                planet.semiMajorAxisGU,
                                planet.eccentricity,
                                planet.inclinationDeg,
                                planet.periapsisArgDeg,
                                planet.orbitalPeriodDays,
                                planet.meanAnomalyDegAtEpoch)));
            }
        }

        realTimeBuffer.swapPublish();
    }
}
