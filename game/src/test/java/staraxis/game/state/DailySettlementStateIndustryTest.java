package staraxis.game.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.astro.AstroData;
import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.command.ColonizePlanetCommand;
import staraxis.game.command.ColonizePlanetHandler;
import staraxis.game.industry.CargoTransfer;
import staraxis.game.industry.ColonialStartupKit;
import staraxis.game.industry.LocalInventory;
import staraxis.game.industry.ProcessingFacility;
import staraxis.game.industry.RecipeRepository;
import staraxis.game.industry.SubstanceId;
import staraxis.game.planet.PlanetSurface;
import staraxis.game.planet.surface.SurfaceRegion;
import staraxis.game.ship.ShipBody;
import staraxis.game.ship.ShipDesign;
import staraxis.game.sim.SimulationClock;
import staraxis.game.sim.SimulationTime;
import staraxis.game.space.SpacePosition;
import staraxis.game.state.DailySettlementState.CityDailySnapshot;
import staraxis.game.state.DailySettlementState.ExtractionFacilityDailySnapshot;
import staraxis.game.state.DailySettlementState.ExtractionResultDailySnapshot;
import staraxis.game.state.DailySettlementState.FacilityResultDailySnapshot;
import staraxis.game.state.DailySettlementState.InventoryDailySnapshot;
import staraxis.game.state.DailySettlementState.PlanetSurfaceDailySnapshot;
import staraxis.game.state.DailySettlementState.ProcessingFacilityDailySnapshot;
import staraxis.game.state.DailySettlementState.SettlementReportDailySnapshot;
import staraxis.game.state.DailySettlementState.TransferDailySnapshot;

/**
 * DailySettlementStateIndustryTest（DailySettlementState 工业快照单元测试）喵。
 *
 * 覆盖 G2.7：
 * - 快照构造与深拷贝（Map/List 不可变，外部修改不影响 game 权威状态）喵
 * - 行星工业快照（库存 / 采集与加工设施 / 在途运输 / 最近结算结果）接入 baseline 喵
 * - 两个行星的工业数据隔离喵
 * - 跨日结算在本 tick baseline 发布前完成：同 tick 可读结算后库存与 SettlementReport，无需额外下一 tick 发布喵
 * - 新殖民行星不继承旧全局结算报告（筛选后为空则报告为 null）喵
 * - 无工业时的稳定空集合 / null 约定喵
 */
class DailySettlementStateIndustryTest {

    private static final String NATION_ID = "nation_test";

    /** 初始模拟 tick（时间轴推进后 +1）。 */
    private static final long INITIAL_SIMULATION_TICK = 1L;

    /** 1 现实秒 = 1 游戏日，便于测试在少量 update 内跨日触发结算喵。 */
    private static final double ONE_REAL_SECOND_PER_GAME_DAY = (double) SimulationClock.SECONDS_PER_DAY;

    // ── 快照构造与深拷贝 ─────────────────────────────────────────

    @Test
    void inventorySnapshotDeepCopiesAmountMaps() {
        LocalInventory inventory = new LocalInventory(7L, 100L, 1000.0);
        inventory.deposit(SubstanceId.WATER, 20.0, 1);
        inventory.reserve(SubstanceId.WATER, 5.0, 1);

        InventoryDailySnapshot snapshot = new InventoryDailySnapshot(
                inventory.inventoryId, inventory.ownerEntityId, inventory.capacity,
                inventory.getUsedCapacity(), inventory.substances, inventory.reservedAmounts);

        assertEquals(7L, snapshot.inventoryId);
        assertEquals(100L, snapshot.ownerEntityId);
        assertEquals(1000.0, snapshot.capacity);
        assertEquals(20.0, snapshot.usedCapacity);
        assertEquals(20.0, snapshot.substances.get(SubstanceId.WATER));
        assertEquals(5.0, snapshot.reservedAmounts.get(SubstanceId.WATER));

        // 修改 game 库存不影响快照（深拷贝）
        inventory.deposit(SubstanceId.WATER, 30.0, 2);
        assertEquals(20.0, snapshot.substances.get(SubstanceId.WATER));

        // 快照 Map 不可变，外部无法写回
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.substances.put(SubstanceId.WATER, 999.0));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.reservedAmounts.put(SubstanceId.WATER, 999.0));
    }

    @Test
    void facilitySnapshotsExposeUnifiedReadonlyFields() {
        ExtractionFacilityDailySnapshot extraction = new ExtractionFacilityDailySnapshot(
                1L, "WATER_EXTRACTOR", 5L, 100L, "WATER", 20.0, "ACTIVE", null);
        ProcessingFacilityDailySnapshot processing = new ProcessingFacilityDailySnapshot(
                2L, "ELECTROLYZER", 5L, 100L, "RECIPE_WATER_ELECTROLYSIS", 0.5,
                "RECIPE_WATER_ELECTROLYSIS", "PROCESSING", null);

        // 基类统一只读字段
        assertEquals(1L, extraction.facilityId);
        assertEquals("WATER_EXTRACTOR", extraction.facilityType);
        assertEquals(5L, extraction.inventoryId);
        assertEquals(100L, extraction.locationEntityId);
        assertEquals("ACTIVE", extraction.status);
        assertNull(extraction.lastFailureReason);
        // 采集专属字段
        assertEquals("WATER", extraction.resourceId);
        assertEquals(20.0, extraction.amountPerDay);

        // 加工设施统一字段 + 专属字段
        assertEquals(2L, processing.facilityId);
        assertEquals("PROCESSING", processing.status);
        assertEquals("RECIPE_WATER_ELECTROLYSIS", processing.activeRecipeId);
        assertEquals(0.5, processing.progressDays);
        assertEquals("RECIPE_WATER_ELECTROLYSIS", processing.progressRecipeId);
    }

    @Test
    void settlementReportSnapshotDeepCopiesNestedMaps() {
        Map<String, Double> produced = new LinkedHashMap<>();
        produced.put(SubstanceId.HYDROGEN, 2.0);
        produced.put(SubstanceId.OXYGEN, 1.0);
        Map<String, Double> consumed = new LinkedHashMap<>();
        consumed.put(SubstanceId.WATER, 2.0);

        FacilityResultDailySnapshot facility = new FacilityResultDailySnapshot(
                1L, "ELECTROLYZER", "RECIPE_WATER_ELECTROLYSIS", true, null, 1, produced, consumed);

        assertEquals(2.0, facility.produced.get(SubstanceId.HYDROGEN));
        assertEquals(1.0, facility.produced.get(SubstanceId.OXYGEN));
        assertEquals(2.0, facility.consumed.get(SubstanceId.WATER));

        // 修改源 Map 不影响快照（深拷贝）
        produced.put(SubstanceId.HYDROGEN, 99.0);
        assertEquals(2.0, facility.produced.get(SubstanceId.HYDROGEN));
        assertThrows(UnsupportedOperationException.class,
                () -> facility.produced.put(SubstanceId.HYDROGEN, 999.0));

        SettlementReportDailySnapshot report = new SettlementReportDailySnapshot(
                100L, List.of(), List.of(facility), List.of());
        assertEquals(100L, report.tick);
        assertEquals(1, report.facilities.size());
        assertTrue(report.extractions.isEmpty());
        assertTrue(report.transfers.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> report.facilities.add(null));
    }

    @Test
    void planetSurfaceSnapshotDefaultsToStableEmptyWhenNoIndustry() {
        // 旧 2 参构造器（G2.7 之前签名）保持兼容
        PlanetSurfaceDailySnapshot legacy = new PlanetSurfaceDailySnapshot(100L, List.of());
        assertEquals(100L, legacy.planetEntityId);
        assertTrue(legacy.surfaceRegions.isEmpty());
        assertTrue(legacy.cities.isEmpty());

        // 旧 3 参构造器：工业字段为稳定空集合 + null 报告（明确约定）
        PlanetSurfaceDailySnapshot legacy3 = new PlanetSurfaceDailySnapshot(100L, List.of(), List.of());
        assertTrue(legacy3.inventories.isEmpty());
        assertTrue(legacy3.extractionFacilities.isEmpty());
        assertTrue(legacy3.processingFacilities.isEmpty());
        assertTrue(legacy3.inTransitTransfers.isEmpty());
        assertNull(legacy3.lastSettlementReport);

        // 空列表不可变
        assertThrows(UnsupportedOperationException.class, () -> legacy3.inventories.add(null));
        assertThrows(UnsupportedOperationException.class, () -> legacy3.extractionFacilities.add(null));
        assertThrows(UnsupportedOperationException.class, () -> legacy3.inTransitTransfers.add(null));
    }

    // ── 集成：殖民后工业快照接入 baseline ─────────────────────────

    @Test
    void colonizedPlanetIndustrialSnapshotVisibleInBaseline() throws Exception {
        TestWorld tw = buildSinglePlanetWorld(0.0);
        colonize(tw);

        StarAxisGameRuntime runtime = new StarAxisGameRuntime(tw.world);
        runtime.start();

        PlanetSurfaceDailySnapshot planetSnap = planetSnapshot(runtime, tw.planet.entityId);
        assertNotNull(planetSnap);

        // 1. 库存快照（inventoryId/owner/capacity/used/substances）
        LocalInventory inventory = tw.world.industryRegistry.getInventoryByOwner(tw.planet.entityId);
        assertEquals(1, planetSnap.inventories.size());
        InventoryDailySnapshot invSnap = planetSnap.inventories.get(0);
        assertEquals(inventory.inventoryId, invSnap.inventoryId);
        assertEquals(tw.planet.entityId, invSnap.ownerEntityId);
        assertEquals(inventory.capacity, invSnap.capacity);
        assertEquals(ColonialStartupKit.INITIAL_ENERGY, invSnap.usedCapacity);
        assertEquals(ColonialStartupKit.INITIAL_ENERGY, invSnap.substances.get(SubstanceId.ENERGY));

        // 2. 采集与加工设施快照
        assertEquals(1, planetSnap.extractionFacilities.size());
        ExtractionFacilityDailySnapshot extractor = planetSnap.extractionFacilities.get(0);
        assertEquals(SubstanceId.WATER, extractor.resourceId);
        assertEquals(ColonialStartupKit.WATER_EXTRACTION_PER_DAY, extractor.amountPerDay);
        assertEquals(tw.planet.entityId, extractor.locationEntityId);

        assertEquals(1, planetSnap.processingFacilities.size());
        ProcessingFacilityDailySnapshot processor = planetSnap.processingFacilities.get(0);
        assertEquals(RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID, processor.activeRecipeId);
        assertEquals(tw.planet.entityId, processor.locationEntityId);

        // 3. 尚未结算：报告为 null，无在途运输
        assertNull(planetSnap.lastSettlementReport);
        assertTrue(planetSnap.inTransitTransfers.isEmpty());
    }

    @Test
    void planetWithoutIndustryHasStableEmptyIndustrialSnapshot() throws Exception {
        TestWorld tw = buildSinglePlanetWorld(0.0);
        // 不殖民：行星有地表但无任何工业状态

        StarAxisGameRuntime runtime = new StarAxisGameRuntime(tw.world);
        runtime.start();

        PlanetSurfaceDailySnapshot planetSnap = planetSnapshot(runtime, tw.planet.entityId);
        assertNotNull(planetSnap, "planet_with_surface_should_have_snapshot");
        assertTrue(planetSnap.inventories.isEmpty());
        assertTrue(planetSnap.extractionFacilities.isEmpty());
        assertTrue(planetSnap.processingFacilities.isEmpty());
        assertTrue(planetSnap.inTransitTransfers.isEmpty());
        assertNull(planetSnap.lastSettlementReport);
    }

    // ── 集成：结算报告保存到下一次 baseline ───────────────────────

    @Test
    void settlementReportPersistedUntilNextBaseline() throws Exception {
        // 时间推进 1 现实秒 = 1 游戏日，起点设在日边界前，第一次 update 即跨日触发结算喵
        TestWorld tw = buildSinglePlanetWorld(SimulationClock.SECONDS_PER_DAY - 1000.0);
        tw.world.time.gameSecondsPerRealSecond = ONE_REAL_SECOND_PER_GAME_DAY;
        colonize(tw);

        StarAxisGameRuntime runtime = new StarAxisGameRuntime(tw.world);
        runtime.start();

        // 第一次 update：跨日结算在本 tick baseline 发布之前完成，同 tick 即携带结算报告喵
        runtime.update(0.05f);
        // 第二次 update：不跨日、无脏标记，active baseline 保持上一次内容，验证报告持续可见喵
        runtime.update(0.05f);

        PlanetSurfaceDailySnapshot planetSnap = planetSnapshot(runtime, tw.planet.entityId);
        assertNotNull(planetSnap);

        // 报告已写入 baseline 而非仅在 settleDay 返回后丢失
        SettlementReportDailySnapshot report = planetSnap.lastSettlementReport;
        assertNotNull(report, "settlement_report_should_be_persisted_until_next_baseline");
        assertEquals(INITIAL_SIMULATION_TICK + 1L, report.tick);

        // 采集 20 水成功、电解完成 1 批
        assertEquals(1, report.extractions.size());
        ExtractionResultDailySnapshot extraction = report.extractions.get(0);
        assertTrue(extraction.success);
        assertEquals(ColonialStartupKit.WATER_EXTRACTION_PER_DAY,
                extraction.extracted.get(SubstanceId.WATER));

        assertEquals(1, report.facilities.size());
        FacilityResultDailySnapshot facility = report.facilities.get(0);
        assertEquals(1, facility.batchCount);
        assertEquals(2.0, facility.produced.get(SubstanceId.HYDROGEN));
        assertEquals(1.0, facility.produced.get(SubstanceId.OXYGEN));

        // 库存快照反映结算后状态（20 水入库 - 2 消耗；初始 100 能源 - 1）喵
        InventoryDailySnapshot invSnap = planetSnap.inventories.get(0);
        assertEquals(ColonialStartupKit.WATER_EXTRACTION_PER_DAY - 2.0,
                invSnap.substances.get(SubstanceId.WATER));
        assertEquals(ColonialStartupKit.INITIAL_ENERGY - 1.0,
                invSnap.substances.get(SubstanceId.ENERGY));
        assertEquals(2.0, invSnap.substances.get(SubstanceId.HYDROGEN));
        assertEquals(1.0, invSnap.substances.get(SubstanceId.OXYGEN));
    }

    @Test
    void settlementReportVisibleInSameTickAsDayChange() throws Exception {
        // 时间起点设在日边界前，第一次 update 即跨日触发结算喵
        TestWorld tw = buildSinglePlanetWorld(SimulationClock.SECONDS_PER_DAY - 1000.0);
        tw.world.time.gameSecondsPerRealSecond = ONE_REAL_SECOND_PER_GAME_DAY;
        colonize(tw);

        StarAxisGameRuntime runtime = new StarAxisGameRuntime(tw.world);
        runtime.start();

        // 只执行一次 update：跨日结算必须在本 tick 的 baseline 发布之前完成，
        // 无需额外的下一 tick 发布就能同时看到结算后库存与 SettlementReport 喵
        runtime.update(0.05f);

        PlanetSurfaceDailySnapshot planetSnap = planetSnapshot(runtime, tw.planet.entityId);
        assertNotNull(planetSnap);

        SettlementReportDailySnapshot report = planetSnap.lastSettlementReport;
        assertNotNull(report, "settlement_report_should_be_visible_in_same_tick_baseline");
        assertEquals(INITIAL_SIMULATION_TICK + 1L, report.tick);
        assertEquals(1, report.extractions.size());
        assertEquals(1, report.facilities.size());

        // 库存快照反映结算后状态（与 settleDay 同一 tick，无需第二次 update）喵
        InventoryDailySnapshot invSnap = planetSnap.inventories.get(0);
        assertEquals(ColonialStartupKit.WATER_EXTRACTION_PER_DAY - 2.0,
                invSnap.substances.get(SubstanceId.WATER));
        assertEquals(ColonialStartupKit.INITIAL_ENERGY - 1.0,
                invSnap.substances.get(SubstanceId.ENERGY));
    }

    @Test
    void newColonyAfterSettlementDoesNotInheritOldReport() throws Exception {
        TestWorld tw2 = buildTwoPlanetWorld();

        ColonizePlanetHandler handler = new ColonizePlanetHandler();
        handler.handle(new ColonizePlanetCommand(tw2.shipA.entityId, tw2.planetA.entityId, NATION_ID),
                tw2.world, 0);

        StarAxisGameRuntime runtime = new StarAxisGameRuntime(tw2.world);
        runtime.start();

        // 第一次 update：跨日结算并发布含 A 报告的 baseline 喵
        runtime.update(0.05f);

        // 结算完成后新殖民 B：B 未参与上一次结算喵
        handler.handle(new ColonizePlanetCommand(tw2.shipB.entityId, tw2.planetB.entityId, NATION_ID),
                tw2.world, 0);

        // 第二次 update：不跨日，因殖民脏标记发布 baseline，验证 B 不继承全局旧报告喵
        runtime.update(0.05f);

        PlanetSurfaceDailySnapshot snapA = planetSnapshot(runtime, tw2.planetA.entityId);
        PlanetSurfaceDailySnapshot snapB = planetSnapshot(runtime, tw2.planetB.entityId);
        assertNotNull(snapA);
        assertNotNull(snapB);

        // A 参与了上次结算，保留其报告喵
        assertNotNull(snapA.lastSettlementReport, "existing_colony_should_keep_its_report");

        // B 为结算后新殖民：筛选后无任何相关条目，报告必须为 null，不得继承旧的全局空报告喵
        assertNull(snapB.lastSettlementReport, "new_colony_must_not_inherit_old_global_report");

        // B 的工业快照仍正常（库存 + 采集/加工设施存在），仅报告为空喵
        assertEquals(1, snapB.inventories.size());
        assertEquals(1, snapB.extractionFacilities.size());
        assertEquals(1, snapB.processingFacilities.size());
    }

    // ── 集成：两个行星工业数据隔离 ────────────────────────────────

    @Test
    void twoPlanetsIndustrialDataIsolated() throws Exception {
        TestWorld tw2 = buildTwoPlanetWorld();

        ColonizePlanetHandler handler = new ColonizePlanetHandler();
        handler.handle(new ColonizePlanetCommand(tw2.shipA.entityId, tw2.planetA.entityId, NATION_ID),
                tw2.world, 0);
        handler.handle(new ColonizePlanetCommand(tw2.shipB.entityId, tw2.planetB.entityId, NATION_ID),
                tw2.world, 0);

        LocalInventory invA = tw2.world.industryRegistry.getInventoryByOwner(tw2.planetA.entityId);
        LocalInventory invB = tw2.world.industryRegistry.getInventoryByOwner(tw2.planetB.entityId);
        // B 库存容量改为恰好等于已占用（初始能源 100），运输货物无法抵达，保持 IN_TRANSIT 喵
        invB.capacity = ColonialStartupKit.INITIAL_ENERGY;
        invA.deposit(SubstanceId.IRON, 10.0, 1);
        CargoTransfer transfer = tw2.world.industryRegistry.startTransfer(
                invA.inventoryId, invB.inventoryId, Map.of(SubstanceId.IRON, 4.0), 5);

        StarAxisGameRuntime runtime = new StarAxisGameRuntime(tw2.world);
        runtime.start();
        // 跨日结算并发布含报告的 baseline 喵
        runtime.update(0.05f);
        runtime.update(0.05f);

        PlanetSurfaceDailySnapshot snapA = planetSnapshot(runtime, tw2.planetA.entityId);
        PlanetSurfaceDailySnapshot snapB = planetSnapshot(runtime, tw2.planetB.entityId);
        assertNotNull(snapA);
        assertNotNull(snapB);

        // 1. 库存互不可见：A 只含 A 库存，B 只含 B 库存喵
        assertEquals(1, snapA.inventories.size());
        assertEquals(invA.inventoryId, snapA.inventories.get(0).inventoryId);
        assertEquals(1, snapB.inventories.size());
        assertEquals(invB.inventoryId, snapB.inventories.get(0).inventoryId);

        // 2. 在途运输归属隔离：A（源）与 B（目标）的 snapshot 均包含这笔运输喵
        assertEquals(1, snapA.inTransitTransfers.size());
        assertEquals(transfer.transferId, snapA.inTransitTransfers.get(0).transferId);
        assertEquals(1, snapB.inTransitTransfers.size());
        assertEquals(transfer.transferId, snapB.inTransitTransfers.get(0).transferId);

        // 3. 结算报告隔离：各行星报告只含各自设施结果，互不混入喵
        SettlementReportDailySnapshot reportA = snapA.lastSettlementReport;
        SettlementReportDailySnapshot reportB = snapB.lastSettlementReport;
        assertNotNull(reportA);
        assertNotNull(reportB);

        long processingA = findProcessingFacilityId(tw2.world, tw2.planetA.entityId);
        long processingB = findProcessingFacilityId(tw2.world, tw2.planetB.entityId);
        assertNotEquals(processingA, processingB);
        assertEquals(1, reportA.facilities.size());
        assertEquals(processingA, reportA.facilities.get(0).facilityId);
        assertEquals(1, reportB.facilities.size());
        assertEquals(processingB, reportB.facilities.get(0).facilityId);

        long extractionA = findExtractionFacilityId(tw2.world, tw2.planetA.entityId);
        long extractionB = findExtractionFacilityId(tw2.world, tw2.planetB.entityId);
        assertEquals(1, reportA.extractions.size());
        assertEquals(extractionA, reportA.extractions.get(0).facilityId);
        assertEquals(1, reportB.extractions.size());
        assertEquals(extractionB, reportB.extractions.get(0).facilityId);

        assertEquals(1, reportA.transfers.size());
        assertEquals(transfer.transferId, reportA.transfers.get(0).transferId);
        assertEquals(1, reportB.transfers.size());
        assertEquals(transfer.transferId, reportB.transfers.get(0).transferId);
    }

    // ── 集成：设施/运输确定性 ID 顺序 ─────────────────────────────

    @Test
    void facilitiesAndTransfersEmitDeterministicIdOrder() throws Exception {
        TestWorld tw = buildSinglePlanetWorld(0.0);
        colonize(tw);

        LocalInventory inv = tw.world.industryRegistry.getInventoryByOwner(tw.planet.entityId);
        inv.deposit(SubstanceId.IRON, 10.0, 1);
        // 目标库存容量 0：任何货物都无法抵达，运输保持 IN_TRANSIT 喵
        LocalInventory target = tw.world.industryRegistry.createInventory(999L);
        target.capacity = 0.0;

        ProcessingFacility f1 = tw.world.industryRegistry.createFacility(
                RecipeRepository.FACILITY_TYPE_ELECTROLYZER, inv.inventoryId, tw.planet.entityId,
                RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);
        ProcessingFacility f2 = tw.world.industryRegistry.createFacility(
                RecipeRepository.FACILITY_TYPE_ELECTROLYZER, inv.inventoryId, tw.planet.entityId,
                RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);
        CargoTransfer t1 = tw.world.industryRegistry.startTransfer(
                inv.inventoryId, target.inventoryId, Map.of(SubstanceId.IRON, 2.0), 5);
        CargoTransfer t2 = tw.world.industryRegistry.startTransfer(
                inv.inventoryId, target.inventoryId, Map.of(SubstanceId.IRON, 3.0), 6);

        StarAxisGameRuntime runtime = new StarAxisGameRuntime(tw.world);
        runtime.start();

        PlanetSurfaceDailySnapshot planetSnap = planetSnapshot(runtime, tw.planet.entityId);
        assertNotNull(planetSnap);

        // 加工设施按 ID 升序（创建顺序）：殖民电解槽 + f1 + f2 喵
        assertEquals(3, planetSnap.processingFacilities.size());
        assertEquals(f1.facilityId, planetSnap.processingFacilities.get(1).facilityId);
        assertEquals(f2.facilityId, planetSnap.processingFacilities.get(2).facilityId);

        // 在途运输按 ID 升序（创建顺序）喵
        assertEquals(2, planetSnap.inTransitTransfers.size());
        assertEquals(t1.transferId, planetSnap.inTransitTransfers.get(0).transferId);
        assertEquals(t2.transferId, planetSnap.inTransitTransfers.get(1).transferId);
        assertEquals(2.0, planetSnap.inTransitTransfers.get(0).goods.get(SubstanceId.IRON));
        assertEquals(3.0, planetSnap.inTransitTransfers.get(1).goods.get(SubstanceId.IRON));
    }

    // ── 工具 ──────────────────────────────────────────────────────

    private static TestWorld buildSinglePlanetWorld(double totalGameSecondsAcc) {
        SimulationTime time = new SimulationTime();
        time.simulationTick = INITIAL_SIMULATION_TICK;
        time.gameDatetimeDay = 1;
        time.totalGameSecondsAcc = totalGameSecondsAcc;
        time.gameSecondsPerRealSecond = 1.0;

        StarSystem system = new StarSystem();
        system.systemId = 10L;
        system.barycenterEntityId = 11L;
        system.galaxyPos = new SpacePosition(1000, 0, 500);

        StarBody star = new StarBody();
        star.entityId = 12L;
        star.systemId = system.systemId;
        star.parentEntityId = system.barycenterEntityId;
        star.posWorldGU = system.galaxyPos;
        star.radiusGU = 50.0;
        system.stars.add(star);

        PlanetBody planet = buildPlanet(100L, system);
        system.planets.add(planet);

        AstroData astro = new AstroData(List.of(system));
        WorldState world = new WorldState(time, 1000, astro);
        world.registerEntity(star);
        world.registerEntity(planet);
        world.nationManager.registerNation(NATION_ID);

        ShipBody ship = buildColonyShip(300L, system);
        world.registerEntity(ship);
        world.assetManager.assignToNation(ship.entityId, NATION_ID);

        return new TestWorld(world, system, planet, ship);
    }

    private static TestWorld buildTwoPlanetWorld() {
        SimulationTime time = new SimulationTime();
        time.simulationTick = INITIAL_SIMULATION_TICK;
        time.gameDatetimeDay = 1;
        time.totalGameSecondsAcc = SimulationClock.SECONDS_PER_DAY - 1000.0;
        time.gameSecondsPerRealSecond = ONE_REAL_SECOND_PER_GAME_DAY;

        StarSystem system = new StarSystem();
        system.systemId = 10L;
        system.barycenterEntityId = 11L;
        system.galaxyPos = new SpacePosition(1000, 0, 500);

        StarBody star = new StarBody();
        star.entityId = 12L;
        star.systemId = system.systemId;
        star.parentEntityId = system.barycenterEntityId;
        star.posWorldGU = system.galaxyPos;
        star.radiusGU = 50.0;
        system.stars.add(star);

        PlanetBody planetA = buildPlanet(100L, system);
        PlanetBody planetB = buildPlanet(101L, system);
        system.planets.add(planetA);
        system.planets.add(planetB);

        AstroData astro = new AstroData(List.of(system));
        WorldState world = new WorldState(time, 1000, astro);
        world.registerEntity(star);
        world.registerEntity(planetA);
        world.registerEntity(planetB);
        world.nationManager.registerNation(NATION_ID);

        ShipBody shipA = buildColonyShip(300L, system);
        ShipBody shipB = buildColonyShip(301L, system);
        world.registerEntity(shipA);
        world.registerEntity(shipB);
        world.assetManager.assignToNation(shipA.entityId, NATION_ID);
        world.assetManager.assignToNation(shipB.entityId, NATION_ID);

        return new TestWorld(world, system, planetA, planetB, shipA, shipB);
    }

    private static PlanetBody buildPlanet(long entityId, StarSystem system) {
        PlanetBody planet = new PlanetBody();
        planet.entityId = entityId;
        planet.systemId = system.systemId;
        planet.planetTypeId = "TERRESTRIAL";
        planet.radiusGU = 100.0;
        planet.posWorldGU = system.galaxyPos;

        PlanetSurface surface = new PlanetSurface(planet.entityId);
        SurfaceRegion region = new SurfaceRegion();
        region.regionId = entityId + 100L;
        region.planetEntityId = planet.entityId;
        region.regionType = "CONTINENT";
        region.name = "希望大陆";
        region.surfacePercentage = 1.0;
        region.developableSpaceRatio = 0.5;
        surface.addSurfaceRegion(region);
        planet.surface = surface;
        planet.surfaceComponentId = planet.entityId;
        return planet;
    }

    private static ShipBody buildColonyShip(long entityId, StarSystem system) {
        ShipBody ship = new ShipBody();
        ship.entityId = entityId;
        ship.systemId = system.systemId;
        ship.designId = ShipDesign.DESIGN_ID_COLONY;
        ship.posWorldGU = system.galaxyPos.add(50, 0, 0);
        return ship;
    }

    private static void colonize(TestWorld tw) throws Exception {
        ColonizePlanetCommand cmd = new ColonizePlanetCommand(tw.ship.entityId, tw.planet.entityId, NATION_ID);
        new ColonizePlanetHandler().handle(cmd, tw.world, 0);
    }

    private static PlanetSurfaceDailySnapshot planetSnapshot(StarAxisGameRuntime runtime, long planetEntityId) {
        DailySettlementState baseline = runtime.getDailySettlementStateBufferForReadonly().getActive();
        return baseline.planetSurfacesByPlanetId.get(planetEntityId);
    }

    private static long findProcessingFacilityId(WorldState world, long planetEntityId) {
        for (ProcessingFacility facility : world.industryRegistry.facilitiesById.values()) {
            if (facility.locationEntityId == planetEntityId) {
                return facility.facilityId;
            }
        }
        return -1L;
    }

    private static long findExtractionFacilityId(WorldState world, long planetEntityId) {
        for (var facility : world.industryRegistry.extractionFacilitiesById.values()) {
            if (facility.locationEntityId == planetEntityId) {
                return facility.facilityId;
            }
        }
        return -1L;
    }

    /**
     * 测试世界状态容器喵。
     */
    private static final class TestWorld {
        final WorldState world;
        final StarSystem system;
        final PlanetBody planet;
        final ShipBody ship;
        final PlanetBody planetA;
        final PlanetBody planetB;
        final ShipBody shipA;
        final ShipBody shipB;

        TestWorld(WorldState world, StarSystem system, PlanetBody planet, ShipBody ship) {
            this.world = world;
            this.system = system;
            this.planet = planet;
            this.ship = ship;
            this.planetA = null;
            this.planetB = null;
            this.shipA = null;
            this.shipB = null;
        }

        TestWorld(WorldState world, StarSystem system, PlanetBody planetA, PlanetBody planetB,
                ShipBody shipA, ShipBody shipB) {
            this.world = world;
            this.system = system;
            this.planet = null;
            this.ship = null;
            this.planetA = planetA;
            this.planetB = planetB;
            this.shipA = shipA;
            this.shipB = shipB;
        }
    }
}
