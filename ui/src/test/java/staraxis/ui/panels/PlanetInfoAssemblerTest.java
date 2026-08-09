package staraxis.ui.panels;

import org.junit.jupiter.api.Test;
import staraxis.game.astro.Habitability;
import staraxis.game.entity.EntityType;
import staraxis.game.industry.SubstanceId;
import staraxis.game.space.SpacePosition;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.DailySettlementState.ExtractionFacilityDailySnapshot;
import staraxis.game.state.DailySettlementState.ExtractionResultDailySnapshot;
import staraxis.game.state.DailySettlementState.FacilityResultDailySnapshot;
import staraxis.game.state.DailySettlementState.InventoryDailySnapshot;
import staraxis.game.state.DailySettlementState.ProcessingFacilityDailySnapshot;
import staraxis.game.state.DailySettlementState.SettlementReportDailySnapshot;
import staraxis.game.state.DailySettlementState.TransferDailySnapshot;
import staraxis.game.state.DailySettlementState.TransferResultDailySnapshot;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.state.snapshot.EntitySnapshot.PlanetDetails;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PlanetInfoAssemblerTest（行星信息组装器单元测试）。
 *
 * 覆盖：
 * - 从 PlanetDetails 组装概览基础字段
 * - 从 planetSurfacesByPlanetId 组装地表区域与城市数据
 * - 工业页：本地库存（容量/已用/物质数量/预留数量）、采集与加工设施
 *   （配方/进度/状态/失败原因）、最近结算产出的真实数据组装
 * - 物流页：在途运输（transferId/源目标库存/货物/状态）的真实数据组装
 * - 行星 A/B 切换时概览/殖民地/工业/物流数据完全隔离（不残留上一颗行星的数据）
 * - 快照缺失/行星不存在/空数据的稳定空态（工业/物流为"暂无…"，不含"尚未接入"）
 */
class PlanetInfoAssemblerTest {

    private static final long SYSTEM_ID = 1L;

    // ===== 构造辅助 =====

    private static EntitySnapshot planetSnapshot(long id, String typeId, Habitability hab) {
        PlanetDetails pd = new PlanetDetails(
                typeId, hab, 3, 2, 6000.0, 24.0, "texture.png",
                false, 1L, 100000.0, 0.01, 0.0, 0.0, 0.0, 365.0, 0.0);
        return new EntitySnapshot(id, EntityType.PLANET, SYSTEM_ID, 1L,
                new SpacePosition(0, 0, 0), null, null, true, pd);
    }

    private static RealTimeWorldState rtWith(EntitySnapshot snap) {
        RealTimeWorldState rt = new RealTimeWorldState();
        rt.putEntitySnapshot(snap);
        return rt;
    }

    private static DailySettlementState dsWith(EntitySnapshot snap) {
        DailySettlementState ds = new DailySettlementState();
        ds.publicEntityBaselinesBySectorKey = new HashMap<>();
        ds.publicEntityBaselinesBySectorKey.put(String.valueOf(SYSTEM_ID), new java.util.ArrayList<>(List.of(snap)));
        ds.planetSurfacesByPlanetId = new HashMap<>();
        return ds;
    }

    private static DailySettlementState.SurfaceRegionDailySnapshot region(long id, String name, double pct, double dev) {
        return new DailySettlementState.SurfaceRegionDailySnapshot(id, "CONTINENT", name, pct, dev);
    }

    private static DailySettlementState.CityDailySnapshot city(long id, String name, String stage,
                                                               int scale, long pop, boolean capital) {
        return new DailySettlementState.CityDailySnapshot(id, name, stage, scale, pop, capital);
    }

    private static boolean hasField(List<EntityInfoViewModel.FieldEntry> rows, String key, String value) {
        return rows.stream().anyMatch(f -> f.key().equals(key) && f.value().contains(value));
    }

    private static boolean hasEmptyRow(List<EntityInfoViewModel.FieldEntry> rows, String text) {
        return rows.stream().anyMatch(f -> f.key().isEmpty() && f.value().equals(text));
    }

    /** key 包含指定子串（用于库存、设施和运输标题行）。 */
    private static boolean hasKeyContaining(List<EntityInfoViewModel.FieldEntry> rows, String keySubstring) {
        return rows.stream().anyMatch(f -> f.key().contains(keySubstring));
    }

    /** 判断指定标题是否被组装为独立分组行。 */
    private static boolean hasSection(List<EntityInfoViewModel.FieldEntry> rows, String title) {
        return rows.stream().anyMatch(f -> f.section() && title.equals(f.key()));
    }

    /** 构造完整工业快照条目（库存 + 采集 + 加工 + 在途运输 + 最近结算）喵 */
    private static DailySettlementState.PlanetSurfaceDailySnapshot surfaceWithIndustry(long planetId,
            List<InventoryDailySnapshot> inventories, List<ExtractionFacilityDailySnapshot> extractions,
            List<ProcessingFacilityDailySnapshot> processings, List<TransferDailySnapshot> transfers,
            SettlementReportDailySnapshot report) {
        return new DailySettlementState.PlanetSurfaceDailySnapshot(planetId, List.of(), List.of(),
                inventories, extractions, processings, transfers, report);
    }

    // ===== 概览基础字段 =====

    @Test
    void assemblesOverviewBaseFromPlanetDetails() {
        PlanetInfoViewModel vm = PlanetInfoAssembler.assemble(1001L,
                rtWith(planetSnapshot(1001L, "TERRESTRIAL", Habitability.HABITABLE)), null);

        assertFalse(vm.missing);
        assertEquals(1001L, vm.planetEntityId);
        assertEquals("行星 #1001", vm.title);
        assertEquals("TERRESTRIAL", vm.typeLabel);
        assertTrue(hasField(vm.overviewFields, "可殖民性", "宜居"));
        assertTrue(hasField(vm.overviewFields, "半径", "6000 GU"));
        assertTrue(hasField(vm.overviewFields, "轨道半径", "100000 GU"));
        assertTrue(hasField(vm.overviewFields, "大陆数量", "3 块"));
        assertTrue(hasField(vm.overviewFields, "已识别资源种类", "2 种"));
    }

    // ===== 地表区域与城市组装 =====

    @Test
    void assemblesRegionsAndCitiesFromSurfaceSnapshot() {
        EntitySnapshot snap = planetSnapshot(1001L, "TERRESTRIAL", Habitability.HABITABLE);
        DailySettlementState ds = dsWith(snap);
        ds.planetSurfacesByPlanetId.put(1001L, new DailySettlementState.PlanetSurfaceDailySnapshot(1001L,
                List.of(
                        region(1L, "希望大陆", 0.32, 0.78),
                        region(2L, "晨曦洋", 0.55, 0.0)),
                List.of(
                        city(10L, "新伊甸", "OUTPOST", 1, 5000, true),
                        city(11L, "北方哨站", "SETTLEMENT", 2, 8000, false))));

        PlanetInfoViewModel vm = PlanetInfoAssembler.assemble(1001L, rtWith(snap), ds);

        assertTrue(hasField(vm.overviewFields, "希望大陆", "占 32% (可开发 78%)"));
        assertTrue(hasField(vm.overviewFields, "晨曦洋", "占 55%"));
        // 海洋无可开发空间时不显示可开发比例喵
        assertFalse(hasField(vm.overviewFields, "晨曦洋", "可开发"));

        assertEquals(3, vm.colonyFields.size());
        assertTrue(hasSection(vm.overviewFields, "地表区域"));
        assertTrue(hasSection(vm.colonyFields, "行星城市"));
        assertTrue(hasField(vm.colonyFields, "[首都] 新伊甸", "前哨殖民地"));
        assertTrue(hasField(vm.colonyFields, "[首都] 新伊甸", "人口 5000"));
        assertTrue(hasField(vm.colonyFields, "北方哨站", "定居点"));
    }

    // ===== 工业页组装（G2.8） =====

    @Test
    void assemblesIndustryInventoryRows() {
        EntitySnapshot snap = planetSnapshot(1001L, "TERRESTRIAL", Habitability.HABITABLE);
        DailySettlementState ds = dsWith(snap);
        Map<String, Double> substances = new HashMap<>();
        substances.put(SubstanceId.WATER, 18.0);
        substances.put(SubstanceId.HYDROGEN, 2.0);
        Map<String, Double> reserved = new HashMap<>();
        reserved.put(SubstanceId.WATER, 5.0);
        InventoryDailySnapshot inv = new InventoryDailySnapshot(7L, 1001L, 1000.0, 20.0, substances, reserved);
        ds.planetSurfacesByPlanetId.put(1001L, surfaceWithIndustry(1001L, List.of(inv),
                List.of(), List.of(), List.of(), null));

        PlanetInfoViewModel vm = PlanetInfoAssembler.assemble(1001L, null, ds);

        // 容量 / 已用容量 / 物质数量 / 预留数量喵
        assertTrue(hasKeyContaining(vm.industryFields, "本地库存 #7"));
        assertTrue(hasSection(vm.industryFields, "本地库存 #7"));
        assertTrue(hasField(vm.industryFields, "库存容量", "20 / 1000"));
        assertTrue(hasField(vm.industryFields, "水 数量", "18"));
        assertTrue(hasField(vm.industryFields, "水 预留", "5"));
        assertTrue(hasField(vm.industryFields, "氢气 数量", "2"));
        // 无预留时不出预留行喵
        assertFalse(hasField(vm.industryFields, "氢气 预留", ""));
    }

    @Test
    void assemblesIndustryFacilityRows() {
        EntitySnapshot snap = planetSnapshot(1001L, "TERRESTRIAL", Habitability.HABITABLE);
        DailySettlementState ds = dsWith(snap);
        ExtractionFacilityDailySnapshot extractor = new ExtractionFacilityDailySnapshot(
                1L, "WATER_EXTRACTOR", 7L, 1001L, SubstanceId.WATER, 20.0, "ACTIVE", null);
        ProcessingFacilityDailySnapshot processor = new ProcessingFacilityDailySnapshot(
                2L, "ELECTROLYZER", 7L, 1001L, "RECIPE_WATER_ELECTROLYSIS", 0.5,
                "RECIPE_WATER_ELECTROLYSIS", "PROCESSING", null);
        ProcessingFacilityDailySnapshot blocked = new ProcessingFacilityDailySnapshot(
                3L, "ELECTROLYZER", 7L, 1001L, "RECIPE_WATER_ELECTROLYSIS", 1.0,
                "RECIPE_WATER_ELECTROLYSIS", "BLOCKED", "ENERGY_INSUFFICIENT");
        ds.planetSurfacesByPlanetId.put(1001L, surfaceWithIndustry(1001L, List.of(),
                List.of(extractor), List.of(processor, blocked), List.of(), null));

        PlanetInfoViewModel vm = PlanetInfoAssembler.assemble(1001L, null, ds);

        // 采集设施：类型 / 采集产物 / 状态喵
        assertTrue(hasField(vm.industryFields, "采集设施 #1", "水采集器"));
        assertTrue(hasField(vm.industryFields, "采集产物", "水 20/日"));
        assertTrue(hasField(vm.industryFields, "状态", "运行中"));

        // 加工设施：类型 / 配方 / 进度 / 状态喵
        assertTrue(hasField(vm.industryFields, "加工设施 #2", "电解槽"));
        assertTrue(hasField(vm.industryFields, "配方", "水电解"));
        assertTrue(hasField(vm.industryFields, "加工进度", "0.5 日"));
        assertTrue(hasField(vm.industryFields, "状态", "加工中"));

        // 阻塞设施：状态 + 失败原因喵
        assertTrue(hasField(vm.industryFields, "状态", "阻塞"));
        assertTrue(hasField(vm.industryFields, "状态", "能源不足"));
    }

    @Test
    void assemblesLastSettlementReportRows() {
        EntitySnapshot snap = planetSnapshot(1001L, "TERRESTRIAL", Habitability.HABITABLE);
        DailySettlementState ds = dsWith(snap);
        ExtractionResultDailySnapshot extraction = new ExtractionResultDailySnapshot(
                1L, "WATER_EXTRACTOR", SubstanceId.WATER, true, null,
                Map.of(SubstanceId.WATER, 20.0));
        // 用 LinkedHashMap 保证展示顺序（与 game SettlementReport 内部一致）喵
        Map<String, Double> produced = new LinkedHashMap<>();
        produced.put(SubstanceId.HYDROGEN, 2.0);
        produced.put(SubstanceId.OXYGEN, 1.0);
        Map<String, Double> consumed = new LinkedHashMap<>();
        consumed.put(SubstanceId.WATER, 2.0);
        FacilityResultDailySnapshot facility = new FacilityResultDailySnapshot(
                2L, "ELECTROLYZER", "RECIPE_WATER_ELECTROLYSIS", true, null, 1,
                produced, consumed);
        TransferResultDailySnapshot transfer = new TransferResultDailySnapshot(
                9L, "ARRIVED", Map.of(SubstanceId.IRON, 4.0));
        SettlementReportDailySnapshot report = new SettlementReportDailySnapshot(
                100L, List.of(extraction), List.of(facility), List.of(transfer));
        ds.planetSurfacesByPlanetId.put(1001L, surfaceWithIndustry(1001L, List.of(),
                List.of(), List.of(), List.of(), report));

        PlanetInfoViewModel vm = PlanetInfoAssembler.assemble(1001L, null, ds);

        // 最近结算产出：采集 / 加工批次与产出消耗 / 运输抵达喵
        assertTrue(hasKeyContaining(vm.industryFields, "最近结算"));
        assertTrue(hasField(vm.industryFields, "采集 #1", "产出 水 20"));
        assertTrue(hasField(vm.industryFields, "加工 #2", "完成 1 批"));
        assertTrue(hasField(vm.industryFields, "加工 #2", "产出 氢气 2"));
        assertTrue(hasField(vm.industryFields, "加工 #2", "消耗 水 2"));
        assertTrue(hasField(vm.industryFields, "运输 #9", "已抵达 铁 4"));
    }

    // ===== 物流页组装（G2.8） =====

    @Test
    void assemblesLogisticsTransferRows() {
        EntitySnapshot snap = planetSnapshot(1001L, "TERRESTRIAL", Habitability.HABITABLE);
        DailySettlementState ds = dsWith(snap);
        // 用 LinkedHashMap 保证货物展示顺序（与 game CargoTransfer.goods 内部一致）喵
        Map<String, Double> goods = new LinkedHashMap<>();
        goods.put(SubstanceId.IRON, 4.0);
        goods.put(SubstanceId.COPPER, 1.5);
        TransferDailySnapshot t = new TransferDailySnapshot(
                9L, 7L, 8L, goods, "IN_TRANSIT", 50L, 0L);
        ds.planetSurfacesByPlanetId.put(1001L, surfaceWithIndustry(1001L, List.of(),
                List.of(), List.of(), List.of(t), null));

        PlanetInfoViewModel vm = PlanetInfoAssembler.assemble(1001L, null, ds);

        // transferId / 源目标库存 / 货物 / 状态喵
        assertTrue(hasKeyContaining(vm.logisticsFields, "运输 #9"));
        assertTrue(hasSection(vm.logisticsFields, "运输 #9"));
        assertTrue(hasField(vm.logisticsFields, "源库存", "#7"));
        assertTrue(hasField(vm.logisticsFields, "目标库存", "#8"));
        assertTrue(hasField(vm.logisticsFields, "货物", "铁 4, 铜 1.5"));
    }

    // ===== A/B 数据隔离（任务 3 核心） =====

    @Test
    void switchingPlanetsProducesFullyIsolatedViewModels() {
        EntitySnapshot snapA = planetSnapshot(1001L, "TERRESTRIAL", Habitability.HABITABLE);
        EntitySnapshot snapB = planetSnapshot(2002L, "GAS_GIANT", Habitability.INHOSPITABLE);

        DailySettlementState ds = dsWith(snapA);
        ds.publicEntityBaselinesBySectorKey.get(String.valueOf(SYSTEM_ID)).add(snapB);
        ds.planetSurfacesByPlanetId.put(1001L, new DailySettlementState.PlanetSurfaceDailySnapshot(1001L,
                List.of(region(1L, "希望大陆", 0.32, 0.78)),
                List.of(city(10L, "新伊甸", "OUTPOST", 1, 5000, true))));
        ds.planetSurfacesByPlanetId.put(2002L, new DailySettlementState.PlanetSurfaceDailySnapshot(2002L,
                List.of(region(3L, "硫磺荒原", 0.6, 0.1)),
                List.of(city(20L, "毒云城", "TOWN", 3, 12000, true))));

        PlanetInfoViewModel vmA = PlanetInfoAssembler.assemble(1001L, null, ds);
        PlanetInfoViewModel vmB = PlanetInfoAssembler.assemble(2002L, null, ds);

        // 标题/绑定 ID 各自正确喵
        assertEquals("行星 #1001", vmA.title);
        assertEquals("行星 #2002", vmB.title);
        assertEquals(1001L, vmA.planetEntityId);
        assertEquals(2002L, vmB.planetEntityId);

        // B 的殖民地页不含 A 的城市，A 的殖民地页不含 B 的城市喵
        assertTrue(hasField(vmB.colonyFields, "[首都] 毒云城", "城镇"));
        assertFalse(hasField(vmB.colonyFields, "新伊甸", ""));
        assertTrue(hasField(vmA.colonyFields, "[首都] 新伊甸", ""));
        assertFalse(hasField(vmA.colonyFields, "毒云城", ""));

        // B 的概览页不含 A 的区域喵
        assertTrue(hasField(vmB.overviewFields, "硫磺荒原", "占 60%"));
        assertFalse(hasField(vmB.overviewFields, "希望大陆", ""));

        // 类型标签各自独立喵
        assertEquals("GAS_GIANT", vmB.typeLabel);
        assertEquals("TERRESTRIAL", vmA.typeLabel);
    }

    @Test
    void switchingPlanetsIsolatesIndustrialAndLogisticsData() {
        EntitySnapshot snapA = planetSnapshot(1001L, "TERRESTRIAL", Habitability.HABITABLE);
        EntitySnapshot snapB = planetSnapshot(2002L, "GAS_GIANT", Habitability.INHOSPITABLE);
        DailySettlementState ds = dsWith(snapA);
        ds.publicEntityBaselinesBySectorKey.get(String.valueOf(SYSTEM_ID)).add(snapB);

        // A：有库存、采集设施、在途运输；B：只有地表/城市、无任何工业数据喵
        InventoryDailySnapshot invA = new InventoryDailySnapshot(7L, 1001L, 1000.0, 20.0,
                Map.of(SubstanceId.WATER, 20.0), Map.of());
        ExtractionFacilityDailySnapshot exA = new ExtractionFacilityDailySnapshot(
                1L, "WATER_EXTRACTOR", 7L, 1001L, SubstanceId.WATER, 20.0, "ACTIVE", null);
        TransferDailySnapshot tA = new TransferDailySnapshot(
                9L, 7L, 8L, Map.of(SubstanceId.IRON, 4.0), "IN_TRANSIT", 50L, 0L);
        ds.planetSurfacesByPlanetId.put(1001L, surfaceWithIndustry(1001L, List.of(invA),
                List.of(exA), List.of(), List.of(tA), null));
        ds.planetSurfacesByPlanetId.put(2002L,
                new DailySettlementState.PlanetSurfaceDailySnapshot(2002L, List.of(), List.of()));

        PlanetInfoViewModel vmA = PlanetInfoAssembler.assemble(1001L, null, ds);
        PlanetInfoViewModel vmB = PlanetInfoAssembler.assemble(2002L, null, ds);

        // A 的工业/物流页只含 A 的数据喵
        assertTrue(hasKeyContaining(vmA.industryFields, "本地库存 #7"));
        assertTrue(hasField(vmA.industryFields, "采集设施 #1", "水采集器"));
        assertTrue(hasKeyContaining(vmA.logisticsFields, "运输 #9"));

        // B 不含 A 的工业/物流数据，且显示明确空态（不伪造、不串数据）喵
        assertTrue(hasEmptyRow(vmB.industryFields, PlanetInfoViewModel.EMPTY_INDUSTRY_TEXT));
        assertTrue(hasEmptyRow(vmB.logisticsFields, PlanetInfoViewModel.EMPTY_LOGISTICS_TEXT));
        assertFalse(hasField(vmB.industryFields, "采集设施 #1", ""));
        assertFalse(hasField(vmB.logisticsFields, "运输 #9", ""));

        // A 的工业/物流页不混入 B 的数据（B 无工业，主要验证 A 不受 B 空态影响）喵
        assertFalse(hasEmptyRow(vmA.industryFields, PlanetInfoViewModel.EMPTY_INDUSTRY_TEXT));
        assertFalse(hasEmptyRow(vmA.logisticsFields, PlanetInfoViewModel.EMPTY_LOGISTICS_TEXT));
    }

    // ===== 快照缺失 / 行星不存在 / 空数据 =====

    @Test
    void missingPlanetReturnsStableEmptyState() {
        PlanetInfoViewModel vm = PlanetInfoAssembler.assemble(9999L, null, null);

        assertNotNull(vm);
        assertTrue(vm.missing);
        assertEquals(9999L, vm.planetEntityId);
        assertEquals("行星 #9999", vm.title);
        assertTrue(hasEmptyRow(vm.overviewFields, PlanetInfoViewModel.MISSING_PLANET_TEXT));
        assertTrue(hasEmptyRow(vm.colonyFields, PlanetInfoViewModel.EMPTY_CITY_TEXT));
        assertTrue(hasEmptyRow(vm.industryFields, PlanetInfoViewModel.EMPTY_INDUSTRY_TEXT));
        assertTrue(hasEmptyRow(vm.logisticsFields, PlanetInfoViewModel.EMPTY_LOGISTICS_TEXT));
    }

    @Test
    void planetNotInAnySnapshotReturnsMissingVm() {
        EntitySnapshot snap = planetSnapshot(1001L, "TERRESTRIAL", Habitability.HABITABLE);
        DailySettlementState ds = dsWith(snap);
        ds.planetSurfacesByPlanetId.put(1001L, new DailySettlementState.PlanetSurfaceDailySnapshot(1001L,
                List.of(), List.of()));

        PlanetInfoViewModel vm = PlanetInfoAssembler.assemble(7777L, rtWith(snap), ds);

        assertTrue(vm.missing);
        assertEquals("行星 #7777", vm.title);
    }

    @Test
    void planetWithoutSurfaceKeepsExplicitEmptyStates() {
        EntitySnapshot snap = planetSnapshot(1001L, "TERRESTRIAL", Habitability.HABITABLE);
        DailySettlementState ds = dsWith(snap);
        ds.planetSurfacesByPlanetId.put(1001L, new DailySettlementState.PlanetSurfaceDailySnapshot(1001L,
                List.of(), List.of()));

        PlanetInfoViewModel vm = PlanetInfoAssembler.assemble(1001L, null, ds);

        assertFalse(vm.missing);
        // 行星存在但无地表数据：概览保留基础字段 + 区域空态、殖民地空态喵
        assertTrue(hasField(vm.overviewFields, "类型", "TERRESTRIAL"));
        assertTrue(hasEmptyRow(vm.overviewFields, PlanetInfoViewModel.EMPTY_REGION_TEXT));
        assertTrue(hasEmptyRow(vm.colonyFields, PlanetInfoViewModel.EMPTY_CITY_TEXT));
    }

    @Test
    void planetWithoutSurfaceEntryInMapKeepsEmptyStates() {
        EntitySnapshot snap = planetSnapshot(1001L, "TERRESTRIAL", Habitability.HABITABLE);
        // 地表映射中无该行星条目（快照链路尚未填充）喵
        DailySettlementState ds = dsWith(snap);

        PlanetInfoViewModel vm = PlanetInfoAssembler.assemble(1001L, null, ds);

        assertFalse(vm.missing);
        assertTrue(hasEmptyRow(vm.overviewFields, PlanetInfoViewModel.EMPTY_REGION_TEXT));
        assertTrue(hasEmptyRow(vm.colonyFields, PlanetInfoViewModel.EMPTY_CITY_TEXT));
    }

    // ===== 工业/物流明确空态（不伪造数据） =====

    @Test
    void industryAndLogisticsPagesAreExplicitEmpty() {
        EntitySnapshot snap = planetSnapshot(1001L, "TERRESTRIAL", Habitability.HABITABLE);
        PlanetInfoViewModel vm = PlanetInfoAssembler.assemble(1001L, rtWith(snap), dsWith(snap));

        // 工业/物流页无数据：显示"暂无…"明确空态，不含"尚未接入"，禁止伪造数据喵
        assertEquals(1, vm.industryFields.size());
        assertTrue(hasEmptyRow(vm.industryFields, PlanetInfoViewModel.EMPTY_INDUSTRY_TEXT));
        assertFalse(PlanetInfoViewModel.EMPTY_INDUSTRY_TEXT.contains("尚未接入"));
        assertEquals(1, vm.logisticsFields.size());
        assertTrue(hasEmptyRow(vm.logisticsFields, PlanetInfoViewModel.EMPTY_LOGISTICS_TEXT));
        assertFalse(PlanetInfoViewModel.EMPTY_LOGISTICS_TEXT.contains("尚未接入"));
    }
}
