package staraxis.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import staraxis.game.astro.AstroData;
import staraxis.game.astro.AstroGenerator;
import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.astro.def.AstroAssetRepository;
import staraxis.game.command.ColonizePlanetCommand;
import staraxis.game.command.ColonizePlanetHandler;
import staraxis.game.command.Command;
import staraxis.game.command.CommandBus;
import staraxis.game.command.JoinGameCommand;
import staraxis.game.command.JoinGameHandler;
import staraxis.game.command.LoadWorldCommand;
import staraxis.game.command.LoadWorldHandler;
import staraxis.game.command.MoveShipCommand;
import staraxis.game.command.MoveShipHandler;
import staraxis.game.command.SetPlayerTimeStepCommand;
import staraxis.game.command.SetPlayerTimeStepHandler;
import staraxis.game.command.SetSystemTimeScaleCommand;
import staraxis.game.command.SetSystemTimeScaleHandler;
import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.log.PerformanceMonitor;
import staraxis.game.log.TickProfiler;
import staraxis.game.ship.ShipBody;
import staraxis.game.ship.ShipMovementSystem;
import staraxis.game.sim.SimulationTime;
import staraxis.game.sim.TimelineSystem;
import staraxis.game.space.SpacePosition;
import staraxis.game.space.galaxy.GalaxyConfig;
import staraxis.game.space.galaxy.GalaxyData;
import staraxis.game.space.galaxy.GalaxyGenerator;
import staraxis.game.space.galaxy.GalaxyGeneratorFactory;
import staraxis.game.space.galaxy.GalaxyType;
import staraxis.game.space.galaxy.StarPosition;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.DailySettlementStateBuffer;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.RealTimeWorldStateBuffer;
import staraxis.game.state.WorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.util.ProgressCallback;
import staraxis.game.world.WorldGenConfig;

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

    /** 玩家当前查看的恒星系ID（=0 表示不在任何星系内）。 */
    private long activeSystemId;

    /**
     * 设置玩家当前查看的恒星系ID。
     * 客户端进入 System View 时调用，game 层据此优先计算该星系舰船。
     */
    public void setActiveSystemId(long systemId) {
        this.activeSystemId = systemId;
    }

    /** 获取玩家当前查看的恒星系ID。 */
    public long getActiveSystemId() {
        return activeSystemId;
    }

    public StarAxisGameRuntime(WorldState worldState) {
        this.worldState = worldState;

        commandBus.register(SetPlayerTimeStepCommand.class, new SetPlayerTimeStepHandler());
        commandBus.register(SetSystemTimeScaleCommand.class, new SetSystemTimeScaleHandler());
        commandBus.register(ColonizePlanetCommand.class, new ColonizePlanetHandler());
        commandBus.register(MoveShipCommand.class, new MoveShipHandler());
        commandBus.register(LoadWorldCommand.class, new LoadWorldHandler());
        commandBus.register(JoinGameCommand.class, new JoinGameHandler());
    }

    public CommandBus getCommandBusForSimOnly() {
        return commandBus;
    }

    /**
     * 提交命令到命令总线喵。
     *
     * @param command 要执行的命令
     */
    public void submitCommand(Command command) {
        commandBus.submit(command);
    }

    /**
     * 立即同步执行命令（不入队列），用于启动阶段需要同步结果的场景喵。
     *
     * @param command 要执行的命令
     */
    public void executeCommandImmediately(Command command) {
        commandBus.executeImmediately(command, worldState, 0);
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
        WorldState ws = new WorldState(time, cfg.systemCount, astro);

        // 初始化情报系统并挂载到 WorldState 喵
        if (progress != null)
            progress.onProgress(0.97f, "初始化情报系统");
        ws.intelSystem = new staraxis.game.intel.IntelSystem(ws, configRegistry.intel());

        // 开局清空：不注册玩家、不注册国家、不分配任何归属喵

        // ── 测试舰船：每个星系生成一艘，静止在恒星外安全位置 ──
        for (StarSystem sys : systems) {
            if (sys == null) continue;

            double offset = sys.gravityWellRadiusGU * 0.04;
            if (offset < 3000) offset = 3000;

            long id = ws.generateEntityId();
            ShipBody ship = new ShipBody();
            ship.entityId = id;
            ship.entityType = staraxis.game.entity.EntityType.SHIP;
            ship.posWorldGU = new SpacePosition(
                sys.galaxyPos.x() + offset,
                sys.galaxyPos.y(),
                sys.galaxyPos.z() + offset);
            ship.velWorldGU = SpacePosition.ORIGIN;
            ship.systemId = sys.systemId;
            ship.hpHull = 1.0;
            ship.fuelMass = 100.0;
            ws.registerEntity(ship);
        }

        if (progress != null)
            progress.onProgress(1.0f, "完成");
        return new StarAxisGameRuntime(ws);
    }

    @Override
    public void start() {
        TickProfiler.init();
        publishRealTimeSnapshot();
        worldState.markRealtimeRevisionPublished();
        // 开局先发布一份低频基线快照：使用当前游戏总秒数作为时间戳喵
        publishBaselineSnapshot();
    }

    @Override
    public void update(float dtSeconds) {
        long tickStartTime = System.nanoTime();
        TickProfiler.tickStart();

        // 独立时间轴系统推进：唯一权威时间入口喵
        TickProfiler.begin(TickProfiler.Phase.TIMELINE);
        TimelineSystem.TickAdvance tickAdvance = TimelineSystem.advanceOneTick(worldState.time);
        double dtGameHours = tickAdvance.dtGameHours;
        TickProfiler.end();

        // STAGE 1: 处理到期跨系统事件（到达事件：将实体恢复到目标星系）
        TickProfiler.begin(TickProfiler.Phase.ARRIVALS);
        worldState.tickDispatcher.stage1Arrivals(worldState, worldState.time.simulationTick);
        TickProfiler.end();

        // STAGE 1.5: 重建星系八叉树空间索引（每 tick，只读查询）
        TickProfiler.begin(TickProfiler.Phase.OCTREE);
        worldState.tickDispatcher.stage1halfRebuildOctree(worldState);
        TickProfiler.end();

        // STAGE 2: LPT 负载分配
        TickProfiler.begin(TickProfiler.Phase.LOAD_BALANCE);
        worldState.tickDispatcher.stage2LoadBalance(worldState, worldState.time.simulationTick);
        TickProfiler.end();

        // STAGE 3: 处理 Command 队列并更新 WorldState喵。
        TickProfiler.begin(TickProfiler.Phase.COMMAND);
        commandBus.executeCommands(worldState, dtGameHours);
        TickProfiler.end();

        // 处理舰船移动喵（在途实体已被 entityIdsBySystem 排除，不会参与计算）
        TickProfiler.begin(TickProfiler.Phase.MOVEMENT);
        shipMovementSystem.update(worldState, dtGameHours, activeSystemId);
        TickProfiler.end();

        // 检查是否需要推送低频基线快照（每 20 tick / 约现实 1 秒）
        TickProfiler.begin(TickProfiler.Phase.SNAPSHOT);
        if (worldState.time.simulationTick % 20 == 0 || worldState.baselineDirty) {
            publishBaselineSnapshot();
            worldState.baselineDirty = false;
            worldState.markRealtimeDirty();
        }

        if (tickAdvance.dayChanged) {
            // 跨日逻辑可在此保留，用于未来的统计等，当前由定时/脏标记统一驱动低频快照发布喵
        }

        // STAGE 4/5: TickDispatcher 合并 + 发布钩子
        worldState.tickDispatcher.stage4Merge();
        worldState.tickDispatcher.stage5Publish();
        TickProfiler.end();

        // 记录性能数据喵
        TickProfiler.tickEnd(worldState.entitiesById.size());
        long tickEndTime = System.nanoTime();
        long tickTimeMs = (tickEndTime - tickStartTime) / 1_000_000L;

        // 获取实体数量和星区数量喵
        int entityCount = worldState.entitiesById.size();
        int sectorCount = worldState.astro.getSystemsView().size();
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

    /**
     * 获取 WorldState 引用（仅限 game 模块内部使用）喵。
     *
     * @deprecated game 模块内部过渡用，外部模块（webnet / client）禁止调用，禁止依赖此方法存在。
     *     外部模块应使用 Command 或快照访问游戏状态喵。
     */
    @Deprecated
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
     * 发布低频基线快照（每 20 tick / 约现实 1 秒）。
     * 使用双缓冲：beginFillInactive() + swapPublish()。
     * 包含：天体基线 + 国家资产 + 行星地表 + 玩家→国家映射 + 可见星系 + 情报等级。
     */
    private void publishBaselineSnapshot() {
        DailySettlementState next = dailySettlementBuffer.beginFillInactive();
        next.baselineTick = worldState.time.simulationTick;
        next.settledAtGameSeconds = worldState.time.getTotalGameSeconds();
        next.settledDay = worldState.time.gameDatetimeDay; // 兼容保留
        next.sectorCount = worldState.astro.getSystemsView().size();

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

        // 2b. 玩家→国家映射（供 webnet 查询，无需读 WorldState）
        HashMap<String, String> playerToNation = new HashMap<>();
        HashMap<String, List<String>> nationToPlayers = new HashMap<>();
        for (staraxis.game.nation.NationState ns : worldState.nationManager.getAllNationStates()) {
            ArrayList<String> playerList = new ArrayList<>(ns.playerIds);
            nationToPlayers.put(ns.nationId, playerList);
            for (String pid : ns.playerIds) {
                playerToNation.put(pid, ns.nationId);
            }
        }
        next.playerToNationMap = playerToNation;
        next.nationToPlayerIdsMap = nationToPlayers;

        // 2c. 预计算各国家可见星系（替代每 tick 实时 octree 查询）
        if (worldState.visibilitySystem != null) {
            HashMap<String, Set<Long>> visibleMap = new HashMap<>();
            for (String nationId : worldState.nationManager.getAllNationIds()) {
                visibleMap.put(nationId, worldState.visibilitySystem.computeIntelVisibleSystems3D(nationId));
            }
            next.visibleSystemIdsByNationId = visibleMap;
        }

        // 2d. 预计算各星系对各国家探测等级（替代每 tick 实时 octree 查询）
        if (worldState.intelSystem != null) {
            HashMap<String, Map<Long, Integer>> detectorMap = new HashMap<>();
            for (String nationId : worldState.nationManager.getAllNationIds()) {
                HashMap<Long, Integer> systemLevels = new HashMap<>();
                for (StarSystem system : worldState.astro.getSystemsView()) {
                    SpacePosition sysPos = (!system.stars.isEmpty() && system.stars.get(0).posWorldGU != null)
                            ? system.stars.get(0).posWorldGU
                            : system.galaxyPos;
                    int level = worldState.intelSystem.getEffectiveDetectorLevel3D(nationId, sysPos);
                    if (level >= 0) {
                        systemLevels.put(system.systemId, level);
                    }
                }
                detectorMap.put(nationId, systemLevels);
            }
            next.detectorLevelByNationAndSystem = detectorMap;
        }

        // 3. 填充公开实体基线快照喵
        Map<String, List<EntitySnapshot>> baselineMap = new HashMap<>();
        for (StarSystem system : worldState.astro.getSystemsView()) {
            String sectorKey = String.valueOf(system.systemId);
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
                    systemPos,
                    null,
                    null,
                    true,
                    new EntitySnapshot.SystemBarycenterDetails()));

            // 3.2 恒星喵
            for (StarBody star : system.stars) {
                sectorBaselines.add(new EntitySnapshot(
                        star.entityId,
                        star.entityType,
                        star.systemId,
                        system.barycenterEntityId,
                        star.posWorldGU != null ? star.posWorldGU : systemPos,
                        star.ownerNationId,
                        star.ownerPlayerId,
                        true,
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
                        systemPos,
                        planet.ownerNationId,
                        planet.ownerPlayerId,
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

            // 3.4 小行星喵
            for (PlanetBody asteroid : system.asteroids) {
                sectorBaselines.add(new EntitySnapshot(
                        asteroid.entityId, asteroid.entityType,
                        asteroid.systemId, system.barycenterEntityId,
                        systemPos,
                        asteroid.ownerNationId, asteroid.ownerPlayerId,
                        true,
                        new EntitySnapshot.PlanetDetails(
                                asteroid.planetTypeId, asteroid.radiusGU,
                                asteroid.rotationPeriodHours, asteroid.surfaceTexturePath,
                                false,
                                asteroid.orbitCenterEntityId,
                                asteroid.semiMajorAxisGU, asteroid.eccentricity,
                                asteroid.inclinationDeg, asteroid.periapsisArgDeg,
                                asteroid.orbitalPeriodDays, asteroid.meanAnomalyDegAtEpoch)));
            }

            // 3.5 卫星喵
            for (PlanetBody moon : system.moons) {
                sectorBaselines.add(new EntitySnapshot(
                        moon.entityId, moon.entityType,
                        moon.systemId, system.barycenterEntityId,
                        systemPos,
                        moon.ownerNationId, moon.ownerPlayerId,
                        true,
                        new EntitySnapshot.PlanetDetails(
                                moon.planetTypeId, moon.radiusGU,
                                moon.rotationPeriodHours, moon.surfaceTexturePath,
                                false,
                                moon.orbitCenterEntityId,
                                moon.semiMajorAxisGU, moon.eccentricity,
                                moon.inclinationDeg, moon.periapsisArgDeg,
                                moon.orbitalPeriodDays, moon.meanAnomalyDegAtEpoch)));
            }
        }
        next.publicEntityBaselinesBySectorKey = baselineMap;

        dailySettlementBuffer.swapPublish();
    }

    private void publishRealTimeSnapshot() {
        // 重要：RealTimeWorldState 同时包含 entitiesById（实体权威表）与 entitySnapshots（对外下发快照）喵。
        // 改造后：STAR/PLANET/SYSTEM_BARYCENTER/MOON/ASTEROID 的 EntitySnapshot
        // 已由 DailySettlementState（每 20 tick）承载，高频不再重复构造喵。
        // registerEntity/putEntity 保留（WorldState 索引必需）喵。
        RealTimeWorldState s = realTimeBuffer.beginFillInactive();

        s.simulationTick = worldState.time.simulationTick;
        s.totalGameSeconds = worldState.time.getTotalGameSeconds();
        s.totalGameSecondsExact = worldState.time.totalGameSecondsAcc;
        s.deltaGameSeconds = worldState.time.lastDeltaGameSeconds;
        s.worldRadius = worldState.worldRadius;
        s.worldType = worldState.time.worldType;
        s.gameSecondsPerRealSecond = worldState.time.gameSecondsPerRealSecond;
        s.timeScale = worldState.time.timeScale;
        s.year = worldState.time.getGameDatetimeYear();
        s.month = worldState.time.getGameDatetimeMonth();
        s.day = worldState.time.getDayOfMonth();
        s.hour = worldState.time.getHour();
        s.minute = worldState.time.getMinute();
        s.second = worldState.time.getSecond();

        // 删除旧 WorldSector 遍历（hex 地图停止开发）

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
            barycenter.posWorldGU = systemPos3d != null ? systemPos3d
                    : system.galaxyPos;

            // 权威注册到 WorldState 喵
            worldState.registerEntity(barycenter);

            s.putEntity(barycenter);
            s.putEntitySystem(system.systemId, barycenter.entityId);
            // 重心 EntitySnapshot 由低频 DailySettlementState 承载，高频跳过

            // 2. 注册恒星实体
            for (StarBody star : system.stars) {
                star.systemId = system.systemId;
                star.parentEntityId = system.barycenterEntityId; // 单星系统也挂在重心下

                if (star.posWorldGU == null) {
                    star.posWorldGU = systemPos3d != null ? systemPos3d
                            : system.galaxyPos;
                }

                // 权威注册到 WorldState 喵
                worldState.registerEntity(star);

                s.putEntity(star);
                s.putEntitySystem(system.systemId, star.entityId);
                // 恒星 EntitySnapshot 由低频 DailySettlementState 承载，高频跳过

            }

            // 3. 注册行星实体
            for (PlanetBody planet : system.planets) {
                // 修正：补全行星的 systemId/parentEntityId/posWorldGU
                planet.systemId = system.systemId;
                planet.parentEntityId = system.barycenterEntityId;
                planet.posWorldGU = planet.posWorldGU != null ? planet.posWorldGU
                        : (systemPos3d != null ? systemPos3d
                                : system.galaxyPos);

                // 权威注册到 WorldState 喵
                worldState.registerEntity(planet);

                s.putEntity(planet);
                s.putEntitySystem(system.systemId, planet.entityId);
                // 行星 EntitySnapshot 由低频 DailySettlementState 承载，高频跳过

            }
        }

        // 3b. 小行星实体（绕恒星小天体），注册索引但不构造高频快照（低频已承载）
        for (StarSystem system : worldState.astro.getSystemsView()) {
            SpacePosition sysPos = system.galaxyPos;
            for (PlanetBody asteroid : system.asteroids) {
                asteroid.systemId = system.systemId;
                asteroid.parentEntityId = system.barycenterEntityId;
                asteroid.posWorldGU = asteroid.posWorldGU != null ? asteroid.posWorldGU : sysPos;
                worldState.registerEntity(asteroid);
                s.putEntity(asteroid);
                s.putEntitySystem(system.systemId, asteroid.entityId);
            }
        }

        // 3c. 卫星实体（绕行星小天体），注册索引但不构造高频快照（低频已承载）
        for (StarSystem system : worldState.astro.getSystemsView()) {
            SpacePosition sysPos = system.galaxyPos;
            for (PlanetBody moon : system.moons) {
                moon.systemId = system.systemId;
                moon.parentEntityId = system.barycenterEntityId;
                moon.posWorldGU = moon.posWorldGU != null ? moon.posWorldGU : sysPos;
                worldState.registerEntity(moon);
                s.putEntity(moon);
                s.putEntitySystem(system.systemId, moon.entityId);
            }
        }

        // 4. 追加动态实体（仅 SHIP）的实时快照下发
        // 说明：恒星与行星已在步骤 2-3 完成快照，此处只处理 ShipBody
        for (Entity entity : worldState.entitiesById.values()) {
            if (entity == null || entity.entityType != EntityType.SHIP) {
                continue;
            }

            s.putEntity(entity);
            if (entity.systemId > 0) {
                s.putEntitySystem(entity.systemId, entity.entityId);
            }

            // SHIP 为私有/情报数据，isPublic=false
            java.util.Set<String> customFlags = java.util.Set.of();
            if (entity instanceof staraxis.game.ship.ShipBody shipBody && shipBody.customFlags != null
                    && !shipBody.customFlags.isEmpty()) {
                customFlags = java.util.Set.copyOf(shipBody.customFlags);
            }

            // 朝向计算：优先使用 ShipBody.currentHeadingDeg
            double headingDeg = 0.0;
            boolean isMoving = false;
            SpacePosition movementTarget = null;
            SpacePosition velocity = null;
            if (entity instanceof staraxis.game.ship.ShipBody shipBody) {
                isMoving = shipBody.isMoving;
                movementTarget = shipBody.movementTarget;
                velocity = shipBody.velWorldGU;
                headingDeg = shipBody.currentHeadingDeg;
            }

            int intelRequiredLevel = worldState.intelSystem != null
                    ? worldState.intelSystem.getRequiredIntelLevel(entity.entityType)
                    : 4;

            s.putEntitySnapshot(new EntitySnapshot(
                    entity.entityId,
                    entity.entityType,
                    entity.systemId,
                    entity.parentEntityId,
                    entity.posWorldGU,
                    entity.ownerNationId,
                    entity.ownerPlayerId,
                    false,
                    new EntitySnapshot.ShipDetails(customFlags, headingDeg, isMoving, movementTarget, velocity),
                    intelRequiredLevel));
        }

        // 5. 对所有实体快照按情报等级排序，供 Webnet 二分查找快速裁剪喵
        s.sortEntitySnapshotsByIntelLevel();

        realTimeBuffer.swapPublish();
    }


}
