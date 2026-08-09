package staraxis.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import staraxis.game.command.SetupPlayerHomeCommand;
import staraxis.game.command.SetupPlayerHomeHandler;
import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.log.PerformanceMonitor;
import staraxis.game.log.TickProfiler;
import staraxis.game.ship.ShipMovementSystem;
import staraxis.game.sim.SimulationClock;
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

    /**
     * 配方仓库（工业系统数据源，G2 元素化生产）。
     */
    private final staraxis.game.industry.RecipeRepository recipeRepository;

    /**
     * 工业日结算服务（权威推进库存/设施产能/配方产出/运输抵达）。
     */
    private final staraxis.game.industry.ProductionSettlementService productionSettlementService;

    /**
     * 最近一次工业日结算报告（SettlementReport）。
     *
     * 说明（G2.7）：
     * - dayChanged 时由 settleDay 返回并保存在此，供同一 tick 的 publishBaselineSnapshot 读取，
     *   保证结算报告不会在 settleDay 返回后丢失。
     * - 每次结算覆盖前值；从未结算过为 null。
     */
    private staraxis.game.industry.SettlementReport lastSettlementReport;

    // ── 世界生成阶段定义（加载进度条用） ──
    /** 世界生成各阶段的进度比例与中文标签。 */
    private enum GenPhase {
        INIT_TIME(0.00f, "初始化时间系统"),
        LOAD_ASTRO(0.03f, "加载天体资源"),
        LOAD_CONFIG(0.05f, "加载配置"),
        GEN_GALAXY(0.08f, "生成星系结构"),
        // 恒星系生成循环占 0.08 ~ 0.92 区间
        BUILD_ASTRO(0.93f, "构建天体数据"),
        CREATE_WORLD(0.95f, "创建世界状态"),
        INIT_INTEL(0.97f, "初始化情报系统"),
        COMPLETE(1.0f, "完成");

        final float progress;
        final String label;

        GenPhase(float p, String l) {
            this.progress = p;
            this.label = l;
        }
    }

    /** 恒星系生成循环占用的进度条区间宽度。 */
    private static final float STAR_GEN_RANGE = 0.84f;

    // ── 世界生成常量 ──
    /** 玩家未指定种子时的兜底默认种子。 */
    private static final long DEFAULT_WORLD_SEED = 42L;

    // ── 测试舰船常量（TODO: Phase 5 造船系统上线后移除） ──
    /** 测试舰船生成偏移系数：引力井半径的 4% 作为安全停泊距离。 */
    private static final double TEST_SHIP_OFFSET_FACTOR = 0.04;
    /** 测试舰船最小偏移距离（GU），防止极小恒星下舰船贴脸。 */
    private static final double TEST_SHIP_MIN_OFFSET_GU = 3000.0;
    /** 测试舰船初始船体结构值。 */
    private static final double TEST_SHIP_HP_HULL = 1.0;
    /** 测试舰船初始燃料量（满油），与 ShipStatsCalculator.DEFAULT_MAX_FUEL 保持一致。 */
    private static final double TEST_SHIP_FUEL_MASS = 100.0;

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
        this(worldState, null);
    }

    /**
     * 构造运行时（可注入配方仓库，供工业系统数据源使用）。
     *
     * @param worldState       权威世界状态
     * @param recipeRepository 配方仓库（为空时创建并按默认路径加载）
     */
    public StarAxisGameRuntime(WorldState worldState, staraxis.game.industry.RecipeRepository recipeRepository) {
        this.worldState = worldState;

        if (recipeRepository == null) {
            recipeRepository = new staraxis.game.industry.RecipeRepository(new ObjectMapper());
            recipeRepository.loadAll();
        }
        this.recipeRepository = recipeRepository;
        this.productionSettlementService = new staraxis.game.industry.ProductionSettlementService(recipeRepository);

        commandBus.register(SetPlayerTimeStepCommand.class, new SetPlayerTimeStepHandler());
        commandBus.register(SetSystemTimeScaleCommand.class, new SetSystemTimeScaleHandler());
        commandBus.register(ColonizePlanetCommand.class, new ColonizePlanetHandler());
        commandBus.register(MoveShipCommand.class, new MoveShipHandler());
        commandBus.register(LoadWorldCommand.class, new LoadWorldHandler());
        commandBus.register(JoinGameCommand.class, new JoinGameHandler());
        commandBus.register(SetupPlayerHomeCommand.class, new SetupPlayerHomeHandler());
    }

    public CommandBus getCommandBusForSimOnly() {
        return commandBus;
    }

    /**
     * 轮询命令执行结果（供 UI 只读消费，不暴露内部 CommandBus）喵。
     *
     * @return 自上次调用以来新增的命令结果列表（消费式，读后清空）
     */
    public java.util.List<staraxis.game.command.CommandResult> pollCommandResults() {
        return commandBus.pollResults();
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

    /**
     * 玩家选择母星系后，同步执行开局设置喵。
     *
     * 委托 SetupPlayerHomeCommand 执行：注册国家、星系归属、初始舰队生成。
     * 在 game tick 开始前同步调用，确保结果立即可用。
     *
     * @param nationId 玩家所选国家ID（必须非空，如 "nation_terran"）
     * @param systemId 玩家所选星系ID（必须 > 0 且在 AstroData 中存在）
     * @return SetupPlayerHomeCommand 包含 shipIds 和 fleetCenterPos 的执行结果
     */
    public SetupPlayerHomeCommand setupPlayerHome(String nationId, long systemId) {
        SetupPlayerHomeCommand cmd = new SetupPlayerHomeCommand(nationId, systemId);
        commandBus.executeImmediately(cmd, worldState, 0);
        return cmd;
    }

    public static StarAxisGameRuntime newGame(WorldGenConfig cfg) {
        return newGame(cfg, null);
    }

    public static StarAxisGameRuntime newGame(WorldGenConfig cfg, ProgressCallback progress) {
        if (progress != null)
            progress.onProgress(GenPhase.INIT_TIME.progress, GenPhase.INIT_TIME.label);
        SimulationTime time = new SimulationTime();
        time.worldType = cfg == null || cfg.worldType == null ? staraxis.game.world.WorldType.SINGLE_PLAYER
                : cfg.worldType;

        if (progress != null)

            progress.onProgress(GenPhase.LOAD_ASTRO.progress, GenPhase.LOAD_ASTRO.label);
        AstroAssetRepository astroAssets = new AstroAssetRepository(new ObjectMapper());
        astroAssets.loadAll();

        staraxis.game.planet.def.PlanetAssetRepository planetAssets = new staraxis.game.planet.def.PlanetAssetRepository(
                new ObjectMapper());
        planetAssets.loadAll();

        if (progress != null)
            progress.onProgress(GenPhase.LOAD_CONFIG.progress, GenPhase.LOAD_CONFIG.label);
        staraxis.game.config.GlobalConfigRegistry configRegistry = new staraxis.game.config.GlobalConfigRegistry(
                new ObjectMapper());
        configRegistry.loadAll();

        AstroGenerator astroGenerator = new AstroGenerator(astroAssets, planetAssets, cfg.worldSeed);

        // 使用星系生成器（策略模式）生成恒星位置，然后按位置生成恒星系喵
        int starCount = cfg.systemCount;
        GalaxyConfig galaxyCfg = GalaxyConfig.defaultSpiral();
        galaxyCfg.starCount = starCount;
        galaxyCfg.worldSeed = (cfg.worldSeed == null || cfg.worldSeed.isBlank()) ? DEFAULT_WORLD_SEED
                : (long) cfg.worldSeed.hashCode();
        if (progress != null)
            progress.onProgress(GenPhase.GEN_GALAXY.progress, GenPhase.GEN_GALAXY.label);
        GalaxyGenerator galaxyGen = GalaxyGeneratorFactory.create(GalaxyType.SPIRAL);
        GalaxyData galaxyData = galaxyGen.generate(galaxyCfg);

        List<StarSystem> systems = new java.util.ArrayList<>();
        int totalStars = galaxyData.stars.size();
        int i = 0;
        for (StarPosition sp : galaxyData.stars) {
            if (progress != null) {
                float p = GenPhase.GEN_GALAXY.progress + STAR_GEN_RANGE * i / totalStars;
                progress.onProgress(p, "生成恒星系 " + (i + 1) + "/" + totalStars);
            }
            SpacePosition pos = new SpacePosition(sp.galaxyX(), sp.galaxyY(), sp.galaxyZ());
            StarSystem sys = astroGenerator.generateSystemAtPosition(pos, sp.starId());
            systems.add(sys);
            i++;
        }

        if (progress != null)
            progress.onProgress(GenPhase.BUILD_ASTRO.progress, GenPhase.BUILD_ASTRO.label);
        AstroData astro = new AstroData(systems);
        if (progress != null)
            progress.onProgress(GenPhase.CREATE_WORLD.progress, GenPhase.CREATE_WORLD.label);
        WorldState ws = new WorldState(time, cfg.systemCount, astro);

        // 初始化情报系统并挂载到 WorldState 喵
        if (progress != null)
            progress.onProgress(GenPhase.INIT_INTEL.progress, GenPhase.INIT_INTEL.label);
        ws.intelSystem = new staraxis.game.intel.IntelSystem(ws, configRegistry.intel());

        // 开局清空：不注册玩家、不注册国家、不分配任何归属喵
        // 玩家母星系和初始舰队在 client 选择星系后通过 setupPlayerHome() 设置

        if (progress != null)
            progress.onProgress(GenPhase.COMPLETE.progress, GenPhase.COMPLETE.label);
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

        // 跨日逻辑：G2 元素化生产日结算，权威推进库存/设施产能/配方产出/运输抵达喵
        // 必须在本 tick 的 publishBaselineSnapshot 之前执行（G2.7 修复）：
        // 使同一 tick 的 baseline 同时看到结算后库存与 SettlementReport，避免额外下一 tick 发布喵
        if (tickAdvance.dayChanged) {
            // 结算报告保存在 lastSettlementReport，供本 tick 的 baseline 发布读取（G2.7）喵
            lastSettlementReport = productionSettlementService.settleDay(
                    worldState.industryRegistry, worldState.time.simulationTick);
            // 工业系统有数据时才标记低频快照脏，避免无谓发布喵
            if (!worldState.industryRegistry.isEmpty()) {
                worldState.baselineDirty = true;
            }
        }

        // 检查是否需要推送低频基线快照（每 20 tick / 约现实 1 秒）
        TickProfiler.begin(TickProfiler.Phase.SNAPSHOT);
        if (worldState.time.simulationTick % SimulationClock.TICKS_PER_SECOND == 0 || worldState.baselineDirty) {
            publishBaselineSnapshot();
            worldState.baselineDirty = false;
            worldState.markRealtimeDirty();
        }

        // STAGE 4/5: TickDispatcher 合并 + 发布钩子
        worldState.tickDispatcher.stage4Merge();
        worldState.tickDispatcher.stage5Publish();
        TickProfiler.end();

        // 记录性能数据喵
        TickProfiler.tickEnd(worldState.entitiesById.size());
        long tickEndTime = System.nanoTime();
        long tickTimeMs = TimeUnit.NANOSECONDS.toMillis(tickEndTime - tickStartTime);

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
     *             外部模块应使用 Command 或快照访问游戏状态喵。
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
                if (planet.surface == null) {
                    continue;
                }

                ArrayList<DailySettlementState.SurfaceRegionDailySnapshot> regions = new ArrayList<>();
                if (planet.surface.surfaceRegions != null) {
                    for (staraxis.game.planet.surface.SurfaceRegion r : planet.surface.surfaceRegions) {
                        regions.add(new DailySettlementState.SurfaceRegionDailySnapshot(
                                r.regionId,
                                r.regionType,
                                r.name,
                                r.surfacePercentage,
                                r.developableSpaceRatio));
                    }
                }

                // 1b. 填充城市/殖民地快照（G0.3：殖民结果对外可读）喵
                ArrayList<DailySettlementState.CityDailySnapshot> cities = new ArrayList<>();
                if (planet.surface.cities != null) {
                    for (staraxis.game.planet.city.City c : planet.surface.cities) {
                        cities.add(new DailySettlementState.CityDailySnapshot(
                                c.cityId,
                                c.name,
                                c.cityStage,
                                c.cityScale,
                                c.population,
                                c.isPlanetaryCapital));
                    }
                }

                // 1c. 填充行星工业只读快照（G2.7）：本地库存 + 采集/加工设施 + 在途运输 + 最近结算结果喵
                // 归属口径：以行星本地库存（ownerEntityId == planet.entityId）为准；
                // 设施通过 inventoryId 归属，在途运输通过 source/targetInventoryId 归属喵
                ArrayList<DailySettlementState.InventoryDailySnapshot> inventories = new ArrayList<>();
                long planetInventoryId = -1L;
                staraxis.game.industry.LocalInventory planetInventory =
                        worldState.industryRegistry.getInventoryByOwner(planet.entityId);
                if (planetInventory != null) {
                    planetInventoryId = planetInventory.inventoryId;
                    inventories.add(toInventorySnapshot(planetInventory));
                }

                ArrayList<DailySettlementState.ExtractionFacilityDailySnapshot> extractionFacilities = new ArrayList<>();
                ArrayList<DailySettlementState.ProcessingFacilityDailySnapshot> processingFacilities =
                        new ArrayList<>();
                ArrayList<DailySettlementState.TransferDailySnapshot> inTransitTransfers = new ArrayList<>();
                if (planetInventoryId > 0) {
                    // 采集/加工设施：筛选所属库存为本行星库存，保持注册（ID 分配）顺序喵
                    for (staraxis.game.industry.ResourceExtractionFacility f
                            : worldState.industryRegistry.extractionFacilitiesById.values()) {
                        if (f.inventoryId == planetInventoryId) {
                            extractionFacilities.add(toExtractionFacilitySnapshot(f));
                        }
                    }
                    for (staraxis.game.industry.ProcessingFacility f
                            : worldState.industryRegistry.facilitiesById.values()) {
                        if (f.inventoryId == planetInventoryId) {
                            processingFacilities.add(toProcessingFacilitySnapshot(f));
                        }
                    }
                    // 在途运输：筛选源或目标库存为本行星库存，保持运输记录 ID 顺序喵
                    for (staraxis.game.industry.CargoTransfer t
                            : worldState.industryRegistry.getInTransitTransfers()) {
                        if (t.sourceInventoryId == planetInventoryId || t.targetInventoryId == planetInventoryId) {
                            inTransitTransfers.add(toTransferSnapshot(t));
                        }
                    }
                }

                // 最近结算结果：仅当本行星有库存且最近一次结算已产生时携带；
                // toSettlementReportSnapshot 在本行星无相关条目（新殖民/空结算）时返回 null（明确约定）喵
                DailySettlementState.SettlementReportDailySnapshot settlementReportSnapshot =
                        lastSettlementReport != null && planetInventoryId > 0
                                ? toSettlementReportSnapshot(planetInventoryId)
                                : null;

                planetMap.put(planet.entityId,
                        new DailySettlementState.PlanetSurfaceDailySnapshot(
                                planet.entityId, List.copyOf(regions), List.copyOf(cities),
                                inventories, extractionFacilities, processingFacilities,
                                inTransitTransfers, settlementReportSnapshot));
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
                                star.temperatureK, star.description, star.surfaceTexturePath,
                                star.systemPos.x(), star.systemPos.y(), star.systemPos.z(),
                                star.orbitCenterEntityId)));
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
                                planet.computeHabitability(),
                                planet.hasSurfaceComponent() ? planet.surface.getContinentCount() : 0,
                                planet.hasSurfaceComponent() ? planet.surface.getDiscoveredResourceTypeCount() : 0,
                                planet.radiusGU,
                                planet.rotationPeriodHours,
                                planet.surfaceTexturePath,
                                isCapital,
                                planet.orbitCenterEntityId,
                                planet.semiMajorAxisGU,
                                planet.eccentricity,
                                planet.inclinationDeg,
                                planet.longitudeOfAscendingNodeDeg,
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
                                asteroid.planetTypeId, asteroid.computeHabitability(),
                                asteroid.hasSurfaceComponent() ? asteroid.surface.getContinentCount() : 0,
                                asteroid.hasSurfaceComponent() ? asteroid.surface.getDiscoveredResourceTypeCount() : 0,
                                asteroid.radiusGU,
                                asteroid.rotationPeriodHours, asteroid.surfaceTexturePath,
                                false,
                                asteroid.orbitCenterEntityId,
                                asteroid.semiMajorAxisGU, asteroid.eccentricity,
                                asteroid.inclinationDeg,
                                asteroid.longitudeOfAscendingNodeDeg,
                                asteroid.periapsisArgDeg,
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
                                moon.planetTypeId, moon.computeHabitability(),
                                moon.hasSurfaceComponent() ? moon.surface.getContinentCount() : 0,
                                moon.hasSurfaceComponent() ? moon.surface.getDiscoveredResourceTypeCount() : 0,
                                moon.radiusGU,
                                moon.rotationPeriodHours, moon.surfaceTexturePath,
                                false,
                                moon.orbitCenterEntityId,
                                moon.semiMajorAxisGU, moon.eccentricity,
                                moon.inclinationDeg,
                                moon.longitudeOfAscendingNodeDeg,
                                moon.periapsisArgDeg,
                                moon.orbitalPeriodDays, moon.meanAnomalyDegAtEpoch)));
            }
        }
        next.publicEntityBaselinesBySectorKey = baselineMap;

        dailySettlementBuffer.swapPublish();
    }

    /**
     * 构造本地库存只读快照（G2.7）。
     * substances / reservedAmounts 在快照构造器内部深拷贝，外部不可见 game 可变对象喵。
     */
    private DailySettlementState.InventoryDailySnapshot toInventorySnapshot(
            staraxis.game.industry.LocalInventory inventory) {
        return new DailySettlementState.InventoryDailySnapshot(
                inventory.inventoryId,
                inventory.ownerEntityId,
                inventory.capacity,
                inventory.getUsedCapacity(),
                inventory.substances,
                inventory.reservedAmounts);
    }

    /**
     * 构造采集设施只读快照（G2.7）。
     */
    private DailySettlementState.ExtractionFacilityDailySnapshot toExtractionFacilitySnapshot(
            staraxis.game.industry.ResourceExtractionFacility facility) {
        return new DailySettlementState.ExtractionFacilityDailySnapshot(
                facility.facilityId,
                facility.facilityType,
                facility.inventoryId,
                facility.locationEntityId,
                facility.resourceId,
                facility.amountPerDay,
                facility.status,
                facility.lastFailureReason);
    }

    /**
     * 构造加工设施只读快照（G2.7）。
     */
    private DailySettlementState.ProcessingFacilityDailySnapshot toProcessingFacilitySnapshot(
            staraxis.game.industry.ProcessingFacility facility) {
        return new DailySettlementState.ProcessingFacilityDailySnapshot(
                facility.facilityId,
                facility.facilityType,
                facility.inventoryId,
                facility.locationEntityId,
                facility.activeRecipeId,
                facility.progressDays,
                facility.progressRecipeId,
                facility.status,
                facility.lastFailureReason);
    }

    /**
     * 构造在途运输只读快照（G2.7）。
     * goods 在快照构造器内部深拷贝喵。
     */
    private DailySettlementState.TransferDailySnapshot toTransferSnapshot(
            staraxis.game.industry.CargoTransfer transfer) {
        return new DailySettlementState.TransferDailySnapshot(
                transfer.transferId,
                transfer.sourceInventoryId,
                transfer.targetInventoryId,
                transfer.goods,
                transfer.status,
                transfer.departedAtTick,
                transfer.arrivedAtTick);
    }

    /**
     * 从最近一次日结算报告（lastSettlementReport）筛选出属于指定行星库存的结算结果，
     * 构造为行星级只读快照（G2.7）。
     *
     * 归属口径与工业快照一致（以 inventoryId 匹配）：
     * - 采集/加工结果：结算结果中的 facilityId 对应的设施所属库存为本行星库存。
     * - 运输结果：结算结果中的 transferId 对应的运输记录源或目标库存为本行星库存。
     * 报告条目顺序保持 SettlementReport 生成顺序（设施/运输创建顺序，即 ID 递增）喵。
     *
     * @param planetInventoryId 行星本地库存 ID（必须 > 0，由调用方保证）
     * @return 该行星的结算结果只读快照；筛选后三类报告均为空时返回 null
     *         （避免新殖民行星/空结算继承旧的全局空报告，明确表示该行星无结算结果）喵
     */
    private DailySettlementState.SettlementReportDailySnapshot toSettlementReportSnapshot(long planetInventoryId) {
        ArrayList<DailySettlementState.ExtractionResultDailySnapshot> extractions = new ArrayList<>();
        ArrayList<DailySettlementState.FacilityResultDailySnapshot> facilities = new ArrayList<>();
        ArrayList<DailySettlementState.TransferResultDailySnapshot> transfers = new ArrayList<>();

        for (staraxis.game.industry.SettlementReport.ExtractionResult result : lastSettlementReport.extractions) {
            staraxis.game.industry.ResourceExtractionFacility facility =
                    worldState.industryRegistry.getExtractionFacility(result.facilityId);
            if (facility != null && facility.inventoryId == planetInventoryId) {
                extractions.add(new DailySettlementState.ExtractionResultDailySnapshot(
                        result.facilityId, result.facilityType, result.resourceId,
                        result.success, result.failureReason, result.extracted));
            }
        }

        for (staraxis.game.industry.SettlementReport.FacilityResult result : lastSettlementReport.facilities) {
            staraxis.game.industry.ProcessingFacility facility =
                    worldState.industryRegistry.getFacility(result.facilityId);
            if (facility != null && facility.inventoryId == planetInventoryId) {
                facilities.add(new DailySettlementState.FacilityResultDailySnapshot(
                        result.facilityId, result.facilityType, result.recipeId,
                        result.success, result.failureReason, result.batchCount,
                        result.produced, result.consumed));
            }
        }

        for (staraxis.game.industry.SettlementReport.TransferResult result : lastSettlementReport.transfers) {
            staraxis.game.industry.CargoTransfer transfer = worldState.industryRegistry.getTransfer(result.transferId);
            if (transfer != null
                    && (transfer.sourceInventoryId == planetInventoryId
                            || transfer.targetInventoryId == planetInventoryId)) {
                transfers.add(new DailySettlementState.TransferResultDailySnapshot(
                        result.transferId, result.resultType, result.goods));
            }
        }

        // 该行星无任何相关结算条目（如结算后才新殖民/空结算）时返回 null，
        // 不得构造携带空列表的旧报告快照，避免污染行星展示态喵
        if (extractions.isEmpty() && facilities.isEmpty() && transfers.isEmpty()) {
            return null;
        }

        return new DailySettlementState.SettlementReportDailySnapshot(
                lastSettlementReport.tick, extractions, facilities, transfers);
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
            SpacePosition facing = null;
            int nationColorRgb = -1;
            if (entity instanceof staraxis.game.ship.ShipBody shipBody) {
                isMoving = shipBody.isMoving;
                movementTarget = shipBody.movementTarget;
                velocity = shipBody.velWorldGU;
                headingDeg = shipBody.currentHeadingDeg;
                facing = shipBody.facing;
                // 国家颜色：用于客户端渲染玩家颜色自发光（无归属时 -1）
                if (shipBody.ownerNationId != null) {
                    var nationState = worldState.nationManager.getNationState(shipBody.ownerNationId);
                    if (nationState != null) {
                        nationColorRgb = nationState.colorRgb;
                    }
                }
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
                    new EntitySnapshot.ShipDetails(customFlags, headingDeg, facing, isMoving, movementTarget, velocity,
                            nationColorRgb),
                    intelRequiredLevel));
        }

        // 5. 对所有实体快照按情报等级排序，供 Webnet 二分查找快速裁剪喵
        s.sortEntitySnapshotsByIntelLevel();

        realTimeBuffer.swapPublish();
    }

}
