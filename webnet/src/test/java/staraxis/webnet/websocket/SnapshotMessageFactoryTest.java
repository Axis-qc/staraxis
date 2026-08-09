package staraxis.webnet.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.astro.AstroData;
import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.command.ColonizePlanetCommand;
import staraxis.game.command.ColonizePlanetHandler;
import staraxis.game.entity.EntityType;
import staraxis.game.planet.PlanetSurface;
import staraxis.game.planet.surface.SurfaceRegion;
import staraxis.game.ship.ShipBody;
import staraxis.game.ship.ShipDesign;
import staraxis.game.sim.SimulationTime;
import staraxis.game.space.SpacePosition;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.WorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.webnet.dto.DailySettlementStateDto;
import staraxis.webnet.dto.SnapshotMessageDto;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SnapshotMessageFactoryTest（DailySettlementStateDto 转换与 JSON 兼容测试）喵。
 *
 * 覆盖 G2.7：
 * - toDailySettlementStateDto 纯转换（只消费传入 DailySettlementState，不读 WorldState）喵
 * - 行星工业/物流/结算快照全部转换：inventories / extractionFacilities / processingFacilities /
 *   inTransitTransfers / lastSettlementReport 及嵌套 Map/List 喵
 * - 旧 DTO/JSON 字段兼容（surfaceRegions / planetSurfaces / nationAssetsByNationId / publicEntityBaselines）喵
 * - 空值约定（无结算报告为 null，NON_NULL 下不输出；无工业时为稳定空集合）喵
 * - 集成：buildSnapshotMessageWithNation 下发的 daily DTO 与 game 侧 baseline 一致喵
 */
class SnapshotMessageFactoryTest {

    private static final String NATION_ID = "nation_test";

    @Test
    void toDtoReturnsNullForNullDailyState() {
        assertNull(SnapshotMessageFactory.toDailySettlementStateDto(null));
    }

    @Test
    void toDtoConvertsAllIndustrialSnapshotFields() {
        DailySettlementState daily = buildDailyStateWithIndustry(true);
        DailySettlementStateDto dto = SnapshotMessageFactory.toDailySettlementStateDto(daily);

        assertNotNull(dto);
        assertEquals(3, dto.settledDay);
        assertEquals(12345L, dto.settledAtGameSeconds);
        assertEquals(2, dto.sectorCount);

        // 顶层旧字段：nationAssetsByNationId 的 EntityType enum -> String 转换喵
        assertNotNull(dto.nationAssetsByNationId);
        assertEquals(List.of(100L), dto.nationAssetsByNationId.get(NATION_ID).get(EntityType.PLANET.name()));

        // 工业行星地表快照喵
        DailySettlementStateDto.PlanetSurfaceSnapshotDto planet = dto.planetSurfaces.get(100L);
        assertNotNull(planet);
        assertEquals(100L, planet.planetEntityId);

        // 地表区域 + 城市喵
        assertEquals(1, planet.surfaceRegions.size());
        assertEquals("希望大陆", planet.surfaceRegions.get(0).name);
        assertEquals(1, planet.cities.size());
        assertEquals("新家园", planet.cities.get(0).name);
        assertTrue(planet.cities.get(0).isPlanetaryCapital);

        // 库存快照 + 嵌套 substance Map（保持 game 顺序）喵
        assertEquals(1, planet.inventories.size());
        DailySettlementStateDto.InventorySnapshotDto inv = planet.inventories.get(0);
        assertEquals(301L, inv.inventoryId);
        assertEquals(100L, inv.ownerEntityId);
        assertEquals(18.0, inv.substances.get("WATER"));
        assertEquals(99.0, inv.substances.get("ENERGY"));
        assertEquals(5.0, inv.reservedAmounts.get("WATER"));

        // 采集设施快照喵
        assertEquals(1, planet.extractionFacilities.size());
        DailySettlementStateDto.ExtractionFacilitySnapshotDto extractor = planet.extractionFacilities.get(0);
        assertEquals(401L, extractor.facilityId);
        assertEquals("WATER", extractor.resourceId);
        assertEquals(20.0, extractor.amountPerDay);

        // 加工设施快照喵
        assertEquals(1, planet.processingFacilities.size());
        DailySettlementStateDto.ProcessingFacilitySnapshotDto processor = planet.processingFacilities.get(0);
        assertEquals(501L, processor.facilityId);
        assertEquals("RECIPE_WATER_ELECTROLYSIS", processor.activeRecipeId);
        assertEquals(0.5, processor.progressDays);

        // 在途运输快照 + 嵌套 goods Map喵
        assertEquals(1, planet.inTransitTransfers.size());
        DailySettlementStateDto.TransferSnapshotDto transfer = planet.inTransitTransfers.get(0);
        assertEquals(601L, transfer.transferId);
        assertEquals(301L, transfer.sourceInventoryId);
        assertEquals(4.0, transfer.goods.get("IRON"));

        // 结算报告 + 嵌套 extractions/facilities/transfers 与 Map喵
        assertNotNull(planet.lastSettlementReport);
        DailySettlementStateDto.SettlementReportSnapshotDto report = planet.lastSettlementReport;
        assertEquals(100L, report.tick);
        assertEquals(1, report.extractions.size());
        assertEquals(20.0, report.extractions.get(0).extracted.get("WATER"));
        assertEquals(1, report.facilities.size());
        DailySettlementStateDto.FacilityResultSnapshotDto facility = report.facilities.get(0);
        assertEquals(2.0, facility.produced.get("HYDROGEN"));
        assertEquals(1.0, facility.produced.get("OXYGEN"));
        assertEquals(2.0, facility.consumed.get("WATER"));
        assertEquals(1, report.transfers.size());
        assertEquals("IN_TRANSIT", report.transfers.get(0).resultType);
        assertEquals(4.0, report.transfers.get(0).goods.get("IRON"));
    }

    @Test
    void toDtoUsesStableEmptyListsWhenNoIndustry() {
        DailySettlementStateDto dto = SnapshotMessageFactory.toDailySettlementStateDto(buildDailyStateWithIndustry(false));

        // 未结算行星：lastSettlementReport 为 null，其余为空集合喵
        DailySettlementStateDto.PlanetSurfaceSnapshotDto barren = dto.planetSurfaces.get(200L);
        assertNotNull(barren);
        assertTrue(barren.inventories.isEmpty());
        assertTrue(barren.extractionFacilities.isEmpty());
        assertTrue(barren.processingFacilities.isEmpty());
        assertTrue(barren.inTransitTransfers.isEmpty());
        assertNull(barren.lastSettlementReport);
    }

    @Test
    void serializedJsonKeepsLegacyFieldsAndContainsIndustrialFields() throws Exception {
        DailySettlementStateDto dto = SnapshotMessageFactory.toDailySettlementStateDto(buildDailyStateWithIndustry(true));
        String json = new ObjectMapper().writeValueAsString(dto);

        // 旧字段兼容喵
        assertTrue(json.contains("\"settledDay\""), "json_should_keep_settledDay");
        assertTrue(json.contains("\"settledAtGameSeconds\""), "json_should_keep_settledAtGameSeconds");
        assertTrue(json.contains("\"planetSurfaces\""), "json_should_keep_planetSurfaces");
        assertTrue(json.contains("\"surfaceRegions\""), "json_should_keep_surfaceRegions");
        assertTrue(json.contains("\"nationAssetsByNationId\""), "json_should_keep_nationAssetsByNationId");
        assertTrue(json.contains("\"publicEntityBaselinesBySectorKey\""), "json_should_keep_publicEntityBaselines");

        // G2.7 新增字段喵
        assertTrue(json.contains("\"cities\""), "json_should_contain_cities");
        assertTrue(json.contains("\"inventories\""), "json_should_contain_inventories");
        assertTrue(json.contains("\"extractionFacilities\""), "json_should_contain_extractionFacilities");
        assertTrue(json.contains("\"processingFacilities\""), "json_should_contain_processingFacilities");
        assertTrue(json.contains("\"inTransitTransfers\""), "json_should_contain_inTransitTransfers");
        assertTrue(json.contains("\"lastSettlementReport\""), "json_should_contain_lastSettlementReport");

        // 嵌套 Map 值喵
        assertTrue(json.contains("\"substances\""), "json_should_contain_substances");
        assertTrue(json.contains("\"reservedAmounts\""), "json_should_contain_reservedAmounts");
        assertTrue(json.contains("\"goods\""), "json_should_contain_goods");
        assertTrue(json.contains("\"produced\""), "json_should_contain_produced");
        assertTrue(json.contains("\"consumed\""), "json_should_contain_consumed");
        assertTrue(json.contains("\"extracted\""), "json_should_contain_extracted");
    }

    @Test
    void serializedJsonOmitsNullReportAndNullFailureReason() throws Exception {
        // 无结算报告 + 无失败原因：NON_NULL 下不输出对应字段喵
        DailySettlementStateDto dto = SnapshotMessageFactory.toDailySettlementStateDto(buildDailyStateWithIndustry(false));
        String json = new ObjectMapper().writeValueAsString(dto);

        assertTrue(!json.contains("\"lastSettlementReport\""), "json_should_omit_null_lastSettlementReport");
        assertTrue(!json.contains("\"lastFailureReason\""), "json_should_omit_null_lastFailureReason");
    }

    @Test
    void buildSnapshotMessageMatchesGameBaselineForColonizedPlanet() throws Exception {
        StarAxisGameRuntime runtime = buildColonizedRuntime();

        SnapshotMessageDto message = SnapshotMessageFactory.buildSnapshotMessageWithNation(runtime, 1L, null, NATION_ID);
        assertNotNull(message);
        assertNotNull(message.dailySettlementState);

        // game 侧活动 baseline（桌面本地读取的来源）喵
        DailySettlementState dailyGame = runtime.getDailySettlementStateBufferForReadonly().getActive();
        DailySettlementState.PlanetSurfaceDailySnapshot gameSnap = dailyGame.planetSurfacesByPlanetId.get(100L);

        DailySettlementStateDto.PlanetSurfaceSnapshotDto dtoSnap =
                message.dailySettlementState.planetSurfaces.get(100L);
        assertNotNull(dtoSnap, "colonized_planet_dto_should_exist");

        // 远程客户端收到与桌面本地一致的行星工业/物流/结算快照喵
        assertEquals(gameSnap.inventories.size(), dtoSnap.inventories.size());
        assertEquals(gameSnap.inventories.get(0).inventoryId, dtoSnap.inventories.get(0).inventoryId);
        assertEquals(gameSnap.extractionFacilities.size(), dtoSnap.extractionFacilities.size());
        assertEquals(gameSnap.extractionFacilities.get(0).resourceId, dtoSnap.extractionFacilities.get(0).resourceId);
        assertEquals(gameSnap.processingFacilities.size(), dtoSnap.processingFacilities.size());
        assertEquals(gameSnap.processingFacilities.get(0).activeRecipeId,
                dtoSnap.processingFacilities.get(0).activeRecipeId);
        assertEquals(gameSnap.cities.size(), dtoSnap.cities.size());
        // 未跨日结算：lastSettlementReport 两侧均为 null喵
        assertEquals(gameSnap.lastSettlementReport, dtoSnap.lastSettlementReport);
        assertNull(dtoSnap.lastSettlementReport);
    }

    // ── 工具 ──────────────────────────────────────────────────────

    private static DailySettlementState buildDailyStateWithIndustry(boolean withSettlementReport) {
        DailySettlementState daily = new DailySettlementState();
        daily.settledDay = 3;
        daily.settledAtGameSeconds = 12345L;
        daily.sectorCount = 2;

        Map<Long, DailySettlementState.PlanetSurfaceDailySnapshot> surfaces = new HashMap<>();

        List<DailySettlementState.SurfaceRegionDailySnapshot> regions = List.of(
                new DailySettlementState.SurfaceRegionDailySnapshot(101L, "CONTINENT", "希望大陆", 1.0, 0.5));
        List<DailySettlementState.CityDailySnapshot> cities = List.of(
                new DailySettlementState.CityDailySnapshot(201L, "新家园", "COLONY", 1, 1000L, true));

        Map<String, Double> substances = new LinkedHashMap<>();
        substances.put("WATER", 18.0);
        substances.put("ENERGY", 99.0);
        Map<String, Double> reserved = new LinkedHashMap<>();
        reserved.put("WATER", 5.0);
        List<DailySettlementState.InventoryDailySnapshot> inventories = List.of(
                new DailySettlementState.InventoryDailySnapshot(301L, 100L, 1000.0, 20.0, substances, reserved));

        List<DailySettlementState.ExtractionFacilityDailySnapshot> extractionFacilities = List.of(
                new DailySettlementState.ExtractionFacilityDailySnapshot(
                        401L, "WATER_EXTRACTOR", 301L, 100L, "WATER", 20.0, "ACTIVE", null));
        List<DailySettlementState.ProcessingFacilityDailySnapshot> processingFacilities = List.of(
                new DailySettlementState.ProcessingFacilityDailySnapshot(
                        501L, "ELECTROLYZER", 301L, 100L, "RECIPE_WATER_ELECTROLYSIS", 0.5,
                        "RECIPE_WATER_ELECTROLYSIS", "PROCESSING", null));

        Map<String, Double> goods = new LinkedHashMap<>();
        goods.put("IRON", 4.0);
        List<DailySettlementState.TransferDailySnapshot> inTransitTransfers = List.of(
                new DailySettlementState.TransferDailySnapshot(601L, 301L, 302L, goods, "IN_TRANSIT", 5L, 6L));

        DailySettlementState.SettlementReportDailySnapshot report = null;
        if (withSettlementReport) {
            Map<String, Double> extracted = new LinkedHashMap<>();
            extracted.put("WATER", 20.0);
            List<DailySettlementState.ExtractionResultDailySnapshot> extractions = List.of(
                    new DailySettlementState.ExtractionResultDailySnapshot(401L, "WATER_EXTRACTOR", "WATER", true, null,
                            extracted));
            Map<String, Double> produced = new LinkedHashMap<>();
            produced.put("HYDROGEN", 2.0);
            produced.put("OXYGEN", 1.0);
            Map<String, Double> consumed = new LinkedHashMap<>();
            consumed.put("WATER", 2.0);
            List<DailySettlementState.FacilityResultDailySnapshot> facilities = List.of(
                    new DailySettlementState.FacilityResultDailySnapshot(
                            501L, "ELECTROLYZER", "RECIPE_WATER_ELECTROLYSIS", true, null, 1, produced, consumed));
            Map<String, Double> transferGoods = new LinkedHashMap<>();
            transferGoods.put("IRON", 4.0);
            List<DailySettlementState.TransferResultDailySnapshot> transfers = List.of(
                    new DailySettlementState.TransferResultDailySnapshot(601L, "IN_TRANSIT", transferGoods));
            report = new DailySettlementState.SettlementReportDailySnapshot(100L, extractions, facilities, transfers);
        }

        surfaces.put(100L, new DailySettlementState.PlanetSurfaceDailySnapshot(
                100L, regions, cities, inventories, extractionFacilities,
                processingFacilities, inTransitTransfers, report));

        // 无工业行星：旧 2 参构造器（兼容路径）喵
        surfaces.put(200L, new DailySettlementState.PlanetSurfaceDailySnapshot(200L, List.of()));

        daily.planetSurfacesByPlanetId = surfaces;

        Map<EntityType, List<Long>> assets = new HashMap<>();
        assets.put(EntityType.PLANET, List.of(100L));
        daily.nationAssetsByNationId = Map.of(NATION_ID, assets);

        // 公开实体基线（按星区聚合），验证透传喵
        daily.publicEntityBaselinesBySectorKey = Map.of("10", List.of(new EntitySnapshot(
                11L, EntityType.SYSTEM_BARYCENTER, 10L, 0L, new SpacePosition(1000, 0, 500),
                null, null, true, new EntitySnapshot.SystemBarycenterDetails())));

        return daily;
    }

    private static StarAxisGameRuntime buildColonizedRuntime() throws Exception {
        SimulationTime time = new SimulationTime();
        time.simulationTick = 1L;
        time.gameDatetimeDay = 1;
        time.totalGameSecondsAcc = 0.0;
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

        PlanetBody planet = new PlanetBody();
        planet.entityId = 100L;
        planet.systemId = system.systemId;
        planet.planetTypeId = "TERRESTRIAL";
        planet.radiusGU = 100.0;
        planet.posWorldGU = system.galaxyPos;
        PlanetSurface surface = new PlanetSurface(planet.entityId);
        SurfaceRegion region = new SurfaceRegion();
        region.regionId = 200L;
        region.planetEntityId = planet.entityId;
        region.regionType = "CONTINENT";
        region.name = "希望大陆";
        region.surfacePercentage = 1.0;
        region.developableSpaceRatio = 0.5;
        surface.addSurfaceRegion(region);
        planet.surface = surface;
        planet.surfaceComponentId = planet.entityId;
        system.planets.add(planet);

        AstroData astro = new AstroData(List.of(system));
        WorldState world = new WorldState(time, 1000, astro);
        world.registerEntity(star);
        world.registerEntity(planet);
        world.nationManager.registerNation(NATION_ID);

        ShipBody ship = new ShipBody();
        ship.entityId = 300L;
        ship.systemId = system.systemId;
        ship.designId = ShipDesign.DESIGN_ID_COLONY;
        ship.posWorldGU = system.galaxyPos.add(50, 0, 0);
        world.registerEntity(ship);
        world.assetManager.assignToNation(ship.entityId, NATION_ID);

        // 殖民（设置 baselineDirty），随后 start 发布含工业数据的 baseline 喵
        new ColonizePlanetHandler().handle(
                new ColonizePlanetCommand(ship.entityId, planet.entityId, NATION_ID), world, 0);

        StarAxisGameRuntime runtime = new StarAxisGameRuntime(world);
        runtime.start();
        return runtime;
    }
}
