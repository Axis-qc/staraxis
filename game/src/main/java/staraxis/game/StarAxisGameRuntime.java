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
import staraxis.game.sim.SimulationTime;
import staraxis.game.sim.TimelineSystem;
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
import staraxis.game.command.ColonizePlanetCommand;
import staraxis.game.command.ColonizePlanetHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StarAxisGameRuntime
 *
 * 最小可用权威运行时：
 * - simulationTick 固定脉冲
 * - 游戏时间推进 dtGameHours
 * - tick 结束发布 RealTimeWorldState（双缓冲，高频快照）
 * - 周期/事件驱动发布 DailySettlementState（低频基线快照）喵
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
        commandBus.register(ColonizePlanetCommand.class, new ColonizePlanetHandler());
    }

    public CommandBus getCommandBusForSimOnly() {
        return commandBus;
    }

    public static StarAxisGameRuntime newGame(WorldGenConfig cfg) {
        SimulationTime time = new SimulationTime();
        time.worldType = cfg == null || cfg.worldType == null ? staraxis.game.world.WorldType.SINGLE_PLAYER
                : cfg.worldType;

        var worldMap = WorldGenerator.generate(cfg);

        AstroAssetRepository astroAssets = new AstroAssetRepository(new ObjectMapper());
        astroAssets.loadAll();

        staraxis.game.planet.def.PlanetAssetRepository planetAssets = new staraxis.game.planet.def.PlanetAssetRepository(
                new ObjectMapper());
        planetAssets.loadAll();

        // 初始化全局配置注册中心并加载所有配置（含 Intel 等）喵
        staraxis.game.config.GlobalConfigRegistry configRegistry = new staraxis.game.config.GlobalConfigRegistry(
                new ObjectMapper());
        configRegistry.loadAll();

        AstroGenerator astroGenerator = new AstroGenerator(astroAssets, planetAssets, cfg.worldSeed);
        List<StarSystem> systems = astroGenerator.generateSystemsForMap(worldMap, cfg);

        AstroData astro = new AstroData(systems);
        WorldState ws = new WorldState(time, worldMap, astro);

        // 初始化情报系统并挂载到 WorldState 喵
        ws.intelSystem = new staraxis.game.intel.IntelSystem(ws, configRegistry.intel());

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

                    // 使用世界种子 + 国家ID 作为确定性随机源，从该星系中随机选择一颗行星作为首都行星喵
                    PlanetBody capital = null;
                    if (!sys.planets.isEmpty()) {
                        long mixed = astroGenerator.getWorldSeedHash() ^ (long) nationId.hashCode();
                        java.util.Random rr = new java.util.Random(mixed);
                        capital = sys.planets.get(rr.nextInt(sys.planets.size()));
                    }

                    // 世界生成时不分配任何归属，等待玩家选择位置生成殖民舰喵
                    // 仅记录出生星系，但星系内天体保持无主状态喵
                    if (ns != null && capital != null) {
                        // ws.nationAssetManager.assignEntityToNation(capital.entityId, nationId); // 禁用初始归属分配喵
                        ns.capitalPlanetEntityId = 0L; // 无首都行星，等待殖民喵
                        ns.spawnSystemEntityId = sys.systemId; // 记录出生星系喵
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
        // 开局先发布一份低频基线快照：使用当前游戏总秒数作为时间戳喵
        publishBaselineSnapshot();
    }

    @Override
    public void update(float dtSeconds) {
        // 独立时间轴系统推进：唯一权威时间入口喵
        TimelineSystem.TickAdvance tickAdvance = TimelineSystem.advanceOneTick(worldState.time);
        double dtGameHours = tickAdvance.dtGameHours;

        // 处理 Command 队列并更新 WorldState喵。
        commandBus.executeCommands(worldState, dtGameHours);

        // 更新所有国家的可见性状态（基于当前世界状态）
        worldState.visibilitySystem.updateAllNationsVisibility();

        // 检查是否需要推送低频基线快照（每分钟周期或事件触发/玩家操作触发脏标记）喵
        long currentGameSeconds = worldState.time.getTotalGameSeconds();
        boolean intervalReached = (currentGameSeconds - worldState.lastBaselinePublishGameSeconds) >= 60;
        if (intervalReached || worldState.baselineDirty) {
            publishBaselineSnapshot();
            worldState.lastBaselinePublishGameSeconds = currentGameSeconds;
            worldState.baselineDirty = false;
        }

        if (tickAdvance.dayChanged) {
            // 跨日逻辑可在此保留，用于未来的统计等，当前由定时/脏标记统一驱动低频快照发布喵
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

    /**
     * 发布低频基线快照（原 publishDailySettlementForDay）。
     * 作用：同步国家资产表、行星地表等低频/大体量数据，降低高频同步压力喵。
     */
    private void publishBaselineSnapshot() {
        DailySettlementState next = new DailySettlementState();
        next.settledAtGameSeconds = worldState.time.getTotalGameSeconds();
        next.settledDay = worldState.time.gameDatetimeDay; // 兼容性保留
        next.sectorCount = worldState.worldMap.getSectorsByCoordView().size();

        // 1. 填充行星地表快照（低频/静态）喵
        HashMap<Long, DailySettlementState.PlanetSurfaceDailySnapshot> planetMap = new HashMap<>();
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

                planetMap.put(planet.entityId,
                        new DailySettlementState.PlanetSurfaceDailySnapshot(planet.entityId, List.copyOf(regions)));
            }
        }
        next.planetSurfacesByPlanetId = planetMap;

        // 2. 填充国家资产全量快照（低频基线）喵
        HashMap<String, Map<EntityType, List<Long>>> assetMap = new HashMap<>();
        for (staraxis.game.nation.NationState ns : worldState.nationManager.getAllNationStates()) {
            Map<EntityType, List<Long>> nationAssets = new HashMap<>();
            for (Map.Entry<EntityType, java.util.Set<Long>> entry : ns.ownedEntityIdsByType.entrySet()) {
                nationAssets.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            assetMap.put(ns.nationId, nationAssets);
        }
        next.nationAssetsByNationId = assetMap;

        // 3. 填充公开实体基线快照（按星区聚合）喵
        // 说明：此处全量生成全世界所有星区的公开实体（STAR/PLANET/BARYCENTER）快照喵。
        Map<String, List<EntitySnapshot>> baselineMap = new HashMap<>();
        for (StarSystem system : worldState.astro.getSystemsView()) {
            String sectorKey = "q:" + system.sectorCoord.q() + ",r:" + system.sectorCoord.r();
            List<EntitySnapshot> sectorBaselines = baselineMap.computeIfAbsent(sectorKey, k -> new ArrayList<>());

            // 3.1 系统重心喵
            sectorBaselines.add(new EntitySnapshot(
                    system.barycenterEntityId,
                    EntityType.SYSTEM_BARYCENTER,
                    system.systemId,
                    0,
                    system.sectorCoord,
                    system.centerWorldGU,
                    null, // 重心初始通常无主
                    true, // 公开可见
                    new EntitySnapshot.SystemBarycenterDetails()));

            // 3.2 恒星喵
            for (StarBody star : system.stars) {
                sectorBaselines.add(new EntitySnapshot(
                        star.entityId,
                        star.entityType,
                        star.systemId,
                        system.barycenterEntityId,
                        system.sectorCoord,
                        system.centerWorldGU,
                        star.ownerNationId,
                        true, // 公开可见
                        new EntitySnapshot.StarDetails(star.starTypeId, star.radiusGU, star.massSolar,
                                star.temperatureK, star.description, star.surfaceTexturePath)));
            }

            // 3.3 行星喵
            for (PlanetBody planet : system.planets) {
                boolean isCapital = false;
                if (planet.ownerNationId != null) {
                    var ns = worldState.nationManager.getNationState(planet.ownerNationId);
                    if (ns != null && ns.capitalPlanetEntityId == planet.entityId) {
                        isCapital = true;
                    }
                }

                sectorBaselines.add(new EntitySnapshot(
                        planet.entityId,
                        planet.entityType,
                        planet.systemId,
                        system.barycenterEntityId,
                        system.sectorCoord,
                        system.centerWorldGU,
                        planet.ownerNationId,
                        true, // 公开可见
                        new EntitySnapshot.PlanetDetails(
                                planet.planetTypeId,
                                planet.radiusGU,
                                planet.rotationPeriodHours,
                                planet.surfaceTexturePath,
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
        next.publicEntityBaselinesBySectorKey = baselineMap;

        dailySettlementBuffer.publish(next);
    }

    private void publishRealTimeSnapshot() {
        // 重要：RealTimeWorldState 同时包含 entitiesById（实体权威表）与 entitySnapshots（对外下发快照）喵。
        // 历史问题：仅调用 putEntity() 会导致 getEntitySnapshotsView() 为空，webnet 下发
        // entities=0，前端表现为“拿不到实体/星区内容”喵。
        // 因此这里必须为需要前端展示/选择/聚焦的实体构造并写入 EntitySnapshot（putEntitySnapshot）喵。
        // 后续若将恒星/行星等转为低频基线下发，也需要保留至少“壳快照”（id/type/pos/sector）或同步调整前端数据源喵。
        RealTimeWorldState s = realTimeBuffer.beginFillInactive();

        s.simulationTick = worldState.time.simulationTick;
        s.totalGameSeconds = worldState.time.getTotalGameSeconds();
        s.deltaGameSeconds = worldState.time.lastDeltaGameSeconds;
        s.worldRadius = worldState.worldMap.radius;
        s.worldType = worldState.time.worldType;
        s.gameSecondsPerRealSecond = worldState.time.gameSecondsPerRealSecond;
        s.timeScale = worldState.time.timeScale;
        s.year = worldState.time.getGameDatetimeYear();
        s.month = worldState.time.getGameDatetimeMonth();
        s.day = worldState.time.getDayOfMonth();
        s.hour = worldState.time.getHour();
        s.minute = worldState.time.getMinute();
        s.second = worldState.time.getSecond();

        for (WorldSector sector : worldState.worldMap.getSectorsView()) {
            s.putSectorCenter(sector.coord, sector.centerWorldGU);
            s.putSectorOwnerNationId(sector.coord, sector.ownerNationId);
        }

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
            // 高频快照下发重心实体快照喵
            s.putEntitySnapshot(new EntitySnapshot(
                    barycenter.entityId,
                    barycenter.entityType,
                    barycenter.systemId,
                    barycenter.parentEntityId,
                    barycenter.sectorCoord,
                    barycenter.posWorldGU,
                    barycenter.ownerNationId,
                    true,
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
                // 高频快照下发恒星实体快照喵
                s.putEntitySnapshot(new EntitySnapshot(
                        star.entityId,
                        star.entityType,
                        star.systemId,
                        star.parentEntityId,
                        star.sectorCoord,
                        star.posWorldGU,
                        star.ownerNationId,
                        true,
                        new EntitySnapshot.StarDetails(
                                star.starTypeId,
                                star.radiusGU,
                                star.massSolar,
                                star.temperatureK,
                                star.description,
                                star.surfaceTexturePath)));

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
                // 高频快照下发行星实体快照喵
                boolean isCapital = false;
                // 根据 NationState.capitalPlanetEntityId 判定首都行星喵
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
                        planet.ownerNationId,
                        true,
                        new EntitySnapshot.PlanetDetails(
                                planet.planetTypeId,
                                planet.radiusGU,
                                planet.rotationPeriodHours,
                                planet.surfaceTexturePath,
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

        // 4. 追加私有/动态实体（当前主要是 SHIP）的实时快照下发喵
        // 说明：该分支只处理非天体动态实体，避免与上方天体循环重复写入喵
        java.util.ArrayList<Entity> dynamicEntities = new java.util.ArrayList<>(worldState.entitiesById.values());
        for (Entity entity : dynamicEntities) {
            if (entity == null || entity.entityType == null) {
                continue;
            }
            if (entity.entityType != EntityType.SHIP) {
                continue;
            }

            // 保障动态实体有星区索引，避免前端按星区过滤时丢失喵
            if (entity.sectorCoord == null && entity.posWorldGU != null) {
                entity.sectorCoord = staraxis.game.world.WorldHexLayout.worldToSectorCoord(entity.posWorldGU);
            }

            s.putEntity(entity);
            // SHIP 为私有/情报数据，isPublic=false 喵
            s.putEntitySnapshot(new EntitySnapshot(
                    entity.entityId,
                    entity.entityType,
                    entity.systemId,
                    entity.parentEntityId,
                    entity.sectorCoord,
                    entity.posWorldGU,
                    entity.ownerNationId,
                    false,
                    new EntitySnapshot.ShipDetails()));
        }

        realTimeBuffer.swapPublish();
    }
}
