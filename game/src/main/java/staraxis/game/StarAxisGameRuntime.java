package staraxis.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import staraxis.game.astro.AstroData;
import staraxis.game.astro.AstroGenerator;
import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.astro.def.AstroAssetRepository;
import staraxis.game.command.ColonizePlanetCommand;
import staraxis.game.command.ColonizePlanetHandler;
import staraxis.game.command.CommandBus;
import staraxis.game.command.MoveShipCommand;
import staraxis.game.command.MoveShipHandler;
import staraxis.game.command.SetPlayerTimeStepCommand;
import staraxis.game.command.SetPlayerTimeStepHandler;
import staraxis.game.command.SetSystemTimeScaleCommand;
import staraxis.game.command.SetSystemTimeScaleHandler;
import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.ship.ShipBody;
import staraxis.game.util.ProgressCallback;
import staraxis.game.ship.ShipMovementSystem;
import staraxis.game.sim.SimulationTime;
import staraxis.game.sim.TimelineSystem;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.DailySettlementStateBuffer;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.RealTimeWorldStateBuffer;
import staraxis.game.state.WorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.space.SpacePosition;
import staraxis.game.space.galaxy.GalaxyConfig;
import staraxis.game.space.galaxy.GalaxyData;
import staraxis.game.space.galaxy.GalaxyGenerator;
import staraxis.game.space.galaxy.GalaxyGeneratorFactory;
import staraxis.game.space.galaxy.GalaxyType;
import staraxis.game.space.galaxy.StarPosition;
import staraxis.game.world.WorldGenConfig;
import staraxis.game.world.WorldGenerator;
import staraxis.game.world.WorldSector;
import staraxis.game.log.PerformanceMonitor;

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

    private final ShipMovementSystem shipMovementSystem = new ShipMovementSystem();

    private final staraxis.game.ship.FTLTravelSystem ftlTravelSystem = new staraxis.game.ship.FTLTravelSystem();

    public StarAxisGameRuntime(WorldState worldState) {
        this.worldState = worldState;

        commandBus.register(SetPlayerTimeStepCommand.class, new SetPlayerTimeStepHandler());
        commandBus.register(SetSystemTimeScaleCommand.class, new SetSystemTimeScaleHandler());
        commandBus.register(ColonizePlanetCommand.class, new ColonizePlanetHandler());
        commandBus.register(MoveShipCommand.class, new MoveShipHandler());
    }

    public CommandBus getCommandBusForSimOnly() {
        return commandBus;
    }

    /**
     * 提交命令到命令总线喵。
     *
     * @param command 要执行的命令
     */
    public void submitCommand(staraxis.game.command.Command command) {
        commandBus.submit(command);
    }

    public static StarAxisGameRuntime newGame(WorldGenConfig cfg) {
        return newGame(cfg, null);
    }

    public static StarAxisGameRuntime newGame(WorldGenConfig cfg, ProgressCallback progress) {
        if (progress != null)
            progress.onProgress(0.00f, "初始化时间系统");
        SimulationTime time = new SimulationTime();
        time.worldType = cfg == null || cfg.worldType == null ? staraxis.game.world.WorldType.SINGLE_PLAYER
                : cfg.worldType;

        if (progress != null)
            progress.onProgress(0.02f, "生成世界地图");
        var worldMap = WorldGenerator.generate(cfg);

        if (progress != null)
            progress.onProgress(0.03f, "加载天体资源");
        AstroAssetRepository astroAssets = new AstroAssetRepository(new ObjectMapper());
        astroAssets.loadAll();

        staraxis.game.planet.def.PlanetAssetRepository planetAssets = new staraxis.game.planet.def.PlanetAssetRepository(
                new ObjectMapper());
        planetAssets.loadAll();

        if (progress != null)
            progress.onProgress(0.05f, "加载配置");
        staraxis.game.config.GlobalConfigRegistry configRegistry = new staraxis.game.config.GlobalConfigRegistry(
                new ObjectMapper());
        configRegistry.loadAll();

        AstroGenerator astroGenerator = new AstroGenerator(astroAssets, planetAssets, cfg.worldSeed);

        // 使用星系生成器（策略模式）生成恒星位置，然后按位置生成恒星系喵
        int starCount = cfg.systemCount;
        GalaxyConfig galaxyCfg = GalaxyConfig.defaultSpiral();
        galaxyCfg.starCount = starCount;
        galaxyCfg.worldSeed = (cfg.worldSeed == null || cfg.worldSeed.isBlank()) ? 42L
                : (long) cfg.worldSeed.hashCode();
        if (progress != null)
            progress.onProgress(0.08f, "生成星系结构");
        GalaxyGenerator galaxyGen = GalaxyGeneratorFactory.create(GalaxyType.SPIRAL);
        GalaxyData galaxyData = galaxyGen.generate(galaxyCfg);

        List<StarSystem> systems = new java.util.ArrayList<>();
        int totalStars = galaxyData.stars.size();
        int i = 0;
        for (StarPosition sp : galaxyData.stars) {
            if (progress != null) {
                float p = 0.08f + 0.84f * i / totalStars;
                progress.onProgress(p, "生成恒星系 " + (i + 1) + "/" + totalStars);
            }
            SpacePosition pos = new SpacePosition(sp.galaxyX(), sp.galaxyY(), sp.galaxyZ());
            StarSystem sys = astroGenerator.generateSystemAtPosition(pos, sp.starId());
            systems.add(sys);
            i++;
        }

        if (progress != null)
            progress.onProgress(0.93f, "构建天体数据");
        AstroData astro = new AstroData(systems);
        if (progress != null)
            progress.onProgress(0.95f, "创建世界状态");
        WorldState ws = new WorldState(time, worldMap, astro);

        // 初始化情报系统并挂载到 WorldState 喵
        if (progress != null)
            progress.onProgress(0.97f, "初始化情报系统");
        ws.intelSystem = new staraxis.game.intel.IntelSystem(ws, configRegistry.intel());

        // 初始化玩家国家并绑定出生点喵
        if (progress != null)
            progress.onProgress(0.98f, "初始化玩家国家");
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
                // 预设系统已随旧版本 WorldMap 体系清理，回退到随机选择
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
                        // ws.nationAssetManager.assignEntityToNation(capital.entityId, nationId); //
                        // 禁用初始归属分配喵
                        ns.capitalPlanetEntityId = 0L; // 无首都行星，等待殖民喵
                        ns.spawnSystemEntityId = sys.systemId; // 记录出生星系喵
                    }

                    break;
                }
            }
        }

        if (progress != null)
            progress.onProgress(1.0f, "完成");
        return new StarAxisGameRuntime(ws);
    }

    @Override
    public void start() {
        publishRealTimeSnapshot();
        worldState.markRealtimeRevisionPublished();
        // 开局先发布一份低频基线快照：使用当前游戏总秒数作为时间戳喵
        publishBaselineSnapshot();
    }

    @Override
    public void update(float dtSeconds) {
        long tickStartTime = System.nanoTime();

        // 独立时间轴系统推进：唯一权威时间入口喵
        TimelineSystem.TickAdvance tickAdvance = TimelineSystem.advanceOneTick(worldState.time);
        double dtGameHours = tickAdvance.dtGameHours;

        // STAGE 1: 处理到期跨系统事件（到达事件：将实体恢复到目标星系）
        ftlTravelSystem.processArrivingEvents(worldState, worldState.time.simulationTick);

        // 处理 Command 队列并更新 WorldState喵。
        commandBus.executeCommands(worldState, dtGameHours);

        // 处理舰船移动喵（在途实体已被 entityIdsBySystem 排除，不会参与计算）
        shipMovementSystem.update(worldState, dtGameHours);

        // 更新所有国家的可见性状态（基于当前世界状态）
        worldState.visibilitySystem.updateAllNationsVisibility();

        // 检查是否需要推送低频基线快照（每分钟周期或事件触发/玩家操作触发脏标记）喵
        long currentGameSeconds = worldState.time.getTotalGameSeconds();
        boolean intervalReached = (currentGameSeconds - worldState.lastBaselinePublishGameSeconds) >= 60;
        if (intervalReached || worldState.baselineDirty) {
            publishBaselineSnapshot();
            worldState.lastBaselinePublishGameSeconds = currentGameSeconds;
            worldState.baselineDirty = false;
            worldState.markRealtimeDirty();
        }

        if (tickAdvance.dayChanged) {
            // 跨日逻辑可在此保留，用于未来的统计等，当前由定时/脏标记统一驱动低频快照发布喵
        }

        // STAGE 4/5: 发布实时快照（双缓冲）
        // 注意：阶段4合并新事件在单线程模式下由 FTLTravelSystem 直接写入事件表，无需合并
        // 阶段5由外部定时调用 publishRealtimeSnapshotIfNeeded() / publishRealtimeSnapshotForced()

        // 记录性能数据喵
        long tickEndTime = System.nanoTime();
        long tickTimeMs = (tickEndTime - tickStartTime) / 1_000_000L;

        // 获取实体数量和星区数量喵
        int entityCount = worldState.entitiesById.size();
        int sectorCount = worldState.worldMap.getSectorsByCoordView().size();
        int activePlayerCount = worldState.nationManager.getAllNationIds().size();
        long simulationTick = worldState.time.simulationTick;

        // 记录到性能监测器（快照生成时间在 SnapshotBroadcaster 中记录）喵
        PerformanceMonitor.getInstance().record(
                tickTimeMs, 0, entityCount, sectorCount, activePlayerCount, simulationTick);
    }

    @Override
    public void stop() {
        // 停止游戏运行时，清理资源喵。
        // 当前实现没有需要显式释放的资源，保留为空方法以供未来扩展喵。
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

    public boolean hasPendingRealtimeSnapshotChanges() {
        return worldState.hasUnpublishedRealtimeChanges();
    }

    public long getRealtimeStateRevision() {
        return worldState.getRealtimeStateRevision();
    }

    public void publishRealtimeSnapshotIfNeeded() {
        if (!worldState.hasUnpublishedRealtimeChanges()) {
            return;
        }

        publishRealTimeSnapshot();
        worldState.markRealtimeRevisionPublished();
    }

    /**
     * 强制发布一份实时快照喵。
     *
     * 用于需要按固定 Tick 节奏稳定广播的场景喵。
     * 即使当前没有新的 realtime dirty（实时脏标记）喵，也会把最新权威时间与实体状态写入活动缓冲喵。
     */
    public void publishRealtimeSnapshotForced() {
        publishRealTimeSnapshot();
        worldState.markRealtimeRevisionPublished();
    }

    /**
     * 设置当前世界级并集关注实体集合喵。
     */
    public void replaceFullRealtimeSimulationEntityIds(java.util.Set<Long> entityIds) {
        worldState.replaceFullRealtimeSimulationEntityIds(entityIds);
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
            StarBody firstStar = system.stars.isEmpty() ? null : system.stars.get(0);
            SpacePosition systemPos = firstStar != null ? firstStar.posWorldGU
                    : system.galaxyPos;
            sectorBaselines.add(new EntitySnapshot(
                    system.barycenterEntityId,
                    EntityType.SYSTEM_BARYCENTER,
                    system.systemId,
                    0,
                    system.sectorCoord,
                    systemPos,
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
                        star.posWorldGU != null ? star.posWorldGU : systemPos,
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
                        systemPos,
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
        s.totalGameSecondsExact = worldState.time.totalGameSecondsAcc;
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
            // 0. 写入恒星系坐标索引
            SpacePosition sysPos = (!system.stars.isEmpty() && system.stars.get(0).posWorldGU != null)
                    ? system.stars.get(0).posWorldGU
                    : system.galaxyPos;
            s.putSystemPosition(system.systemId, sysPos);

            // 1. 创建并注册重心实体
            SpacePosition systemPos3d = null;
            if (!system.stars.isEmpty() && system.stars.get(0).posWorldGU != null) {
                systemPos3d = system.stars.get(0).posWorldGU;
            }

            Entity barycenter = new Entity();
            barycenter.entityId = system.barycenterEntityId;
            barycenter.entityType = EntityType.SYSTEM_BARYCENTER;
            barycenter.systemId = system.systemId;
            barycenter.parentEntityId = 0;
            barycenter.sectorCoord = system.sectorCoord;
            barycenter.posWorldGU = systemPos3d != null ? systemPos3d
                    : system.galaxyPos;

            // 权威注册到 WorldState 喵
            worldState.registerEntity(barycenter);

            s.putEntity(barycenter);
            s.putEntitySystem(system.systemId, barycenter.entityId);
            // 高频快照下发重心实体快照喵
            // 系统重心：情报等级 0（基础天文数据，公开可见）喵
            s.putEntitySnapshot(new EntitySnapshot(
                    barycenter.entityId,
                    barycenter.entityType,
                    barycenter.systemId,
                    barycenter.parentEntityId,
                    barycenter.sectorCoord,
                    barycenter.posWorldGU,
                    barycenter.ownerNationId,
                    true,
                    new EntitySnapshot.SystemBarycenterDetails(),
                    0));

            // 2. 注册恒星实体
            for (StarBody star : system.stars) {
                star.systemId = system.systemId;
                star.parentEntityId = system.barycenterEntityId; // 单星系统也挂在重心下
                star.sectorCoord = system.sectorCoord;
                if (star.posWorldGU == null) {
                    star.posWorldGU = systemPos3d != null ? systemPos3d
                            : system.galaxyPos;
                }

                // 权威注册到 WorldState 喵
                worldState.registerEntity(star);

                s.putEntity(star);
                s.putEntitySystem(system.systemId, star.entityId);
                // 高频快照下发恒星实体快照喵
                // 恒星：情报等级 0（基础天文数据，公开可见）喵
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
                                star.surfaceTexturePath),
                        0));

            }

            // 3. 注册行星实体
            for (PlanetBody planet : system.planets) {
                // 修正：补全行星的 systemId/parentEntityId/sectorCoord/posWorldGU，确保能通过星区过滤并正确渲染喵
                planet.systemId = system.systemId;
                planet.parentEntityId = system.barycenterEntityId;
                planet.sectorCoord = system.sectorCoord;
                planet.posWorldGU = planet.posWorldGU != null ? planet.posWorldGU
                        : (systemPos3d != null ? systemPos3d
                                : system.galaxyPos);

                // 权威注册到 WorldState 喵
                worldState.registerEntity(planet);

                s.putEntity(planet);
                s.putEntitySystem(system.systemId, planet.entityId);
                // 高频快照下发行星实体快照喵
                boolean isCapital = false;
                // 根据 NationState.capitalPlanetEntityId 判定首都行星喵
                if (planet.ownerNationId != null) {
                    var ns = worldState.nationManager.getNationState(planet.ownerNationId);
                    if (ns != null && ns.capitalPlanetEntityId == planet.entityId) {
                        isCapital = true;
                    }
                }
                // 行星：情报等级 0（基础天文数据，公开可见）喵
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
                                planet.meanAnomalyDegAtEpoch),
                        0));

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
            if (entity.systemId > 0) {
                s.putEntitySystem(entity.systemId, entity.entityId);
            }
                continue;
            }

            // 保障动态实体有星区索引，避免前端按星区过滤时丢失喵
            if (entity.sectorCoord == null && entity.posWorldGU != null) {
                entity.sectorCoord = staraxis.game.world.WorldHexLayout.worldToSectorCoord(entity.posWorldGU);
            }

            s.putEntity(entity);
            // SHIP 为私有/情报数据，isPublic=false 喵
            java.util.Set<String> customFlags = java.util.Set.of();
            if (entity instanceof staraxis.game.ship.ShipBody shipBody && shipBody.customFlags != null) {
                customFlags = java.util.Set.copyOf(shipBody.customFlags);
            }

            // 朝向计算：优先由速度向量推导，静止时回退 0 度喵。
            double headingDeg = 0.0;
            if (entity.velWorldGU != null) {
                double vx = entity.velWorldGU.x();
                double vz = entity.velWorldGU.z();
                if (Math.abs(vx) > 1e-9 || Math.abs(vz) > 1e-9) {
                    headingDeg = Math.toDegrees(Math.atan2(vz, vx));
                }
            }

            // 获取舰船的移动状态和物理属性喵
            boolean isMoving = false;
            SpacePosition movementTarget = null;
            SpacePosition velocity = null;
            // 默认值与 ShipBody 一致，后续从 shipBody 读取实际值喵
            double maxSpeed = 20.0;
            double baseAcceleration = 5.0;
            double bowAccelerationBonus = 5.0;
            double turnRate = 45.0;
            double lateralSpeedPenalty = 0.6;
            double reverseSpeedPenalty = 0.3;
            if (entity instanceof staraxis.game.ship.ShipBody shipBody) {
                isMoving = shipBody.isMoving;
                movementTarget = shipBody.movementTarget;
                velocity = shipBody.velWorldGU;
                maxSpeed = shipBody.maxSpeed;
                baseAcceleration = shipBody.baseAcceleration;
                bowAccelerationBonus = shipBody.bowAccelerationBonus;
                turnRate = shipBody.turnRate;
                lateralSpeedPenalty = shipBody.lateralSpeedPenalty;
                reverseSpeedPenalty = shipBody.reverseSpeedPenalty;
                // 使用当前朝向作为headingDeg喵
                headingDeg = shipBody.currentHeadingDeg;
            }

            // 获取情报需求等级（从 IntelSystem 查询，默认 4 级）喵
            int intelRequiredLevel = worldState.intelSystem != null
                    ? worldState.intelSystem.getRequiredIntelLevel(entity.entityType)
                    : 4;

            // 调试日志：只在舰船正在移动时记录（降低日志频率）喵
            s.putEntitySnapshot(new EntitySnapshot(
                    entity.entityId,
                    entity.entityType,
                    entity.systemId,
                    entity.parentEntityId,
                    entity.sectorCoord,
                    entity.posWorldGU,
                    entity.ownerNationId,
                    false,
                    new EntitySnapshot.ShipDetails(customFlags, headingDeg, isMoving, movementTarget, velocity,
                            maxSpeed, baseAcceleration, bowAccelerationBonus, turnRate,
                            lateralSpeedPenalty, reverseSpeedPenalty),
                    intelRequiredLevel));
        }

        // 5. 对所有星区的实体快照按情报等级排序，供 Webnet 二分查找快速裁剪喵
        s.sortEntitySnapshotsByIntelLevel();

        realTimeBuffer.swapPublish();
    }


}
