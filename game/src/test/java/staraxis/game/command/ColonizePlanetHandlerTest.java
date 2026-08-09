package staraxis.game.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.astro.AstroData;
import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.industry.ColonialStartupKit;
import staraxis.game.industry.LocalInventory;
import staraxis.game.industry.ProcessingFacility;
import staraxis.game.industry.ProductionSettlementService;
import staraxis.game.industry.RecipeRepository;
import staraxis.game.industry.ResourceExtractionFacility;
import staraxis.game.industry.SettlementReport;
import staraxis.game.industry.SubstanceId;
import staraxis.game.planet.PlanetSurface;
import staraxis.game.planet.city.City;
import staraxis.game.planet.surface.SurfaceRegion;
import staraxis.game.ship.ShipBody;
import staraxis.game.ship.ShipDesign;
import staraxis.game.sim.SimulationTime;
import staraxis.game.space.OrbitSolver;
import staraxis.game.space.SpacePosition;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.WorldState;

/**
 * ColonizePlanetHandlerTest（殖民命令处理器测试）喵。
 *
 * 覆盖 G0.1~G0.3 殖民闭环验证：
 * - 成功殖民：行星归属、国家资产、城市/殖民地创建、殖民舰消耗喵
 * - 失败路径：目标已占有、舰船类型错误、距离过远、归属不匹配、位置缺失、
 *   不可殖民、国家状态（不存在/不活跃）、参数非法喵
 * - DailySettlementState 集成：殖民结果可被低频基线快照读取喵
 */
class ColonizePlanetHandlerTest {

    private static final String NATION_ID = "nation_test";
    private static final String NATION_OTHER = "nation_other";
    private static final String NATION_GHOST = "ghost_nation";

    private ColonizePlanetHandler handler;
    private WorldState world;
    private StarSystem system;
    private PlanetBody planet;
    private ShipBody colonyShip;

    @BeforeEach
    void setUp() {
        handler = new ColonizePlanetHandler();

        SimulationTime time = new SimulationTime();
        time.simulationTick = 1L;
        time.gameDatetimeDay = 30;

        system = new StarSystem();
        system.systemId = 1L;
        system.galaxyPos = new SpacePosition(0, 0, 0);

        // 无主宜居行星（TERRESTRIAL），带一个可开发地表区域喵
        planet = new PlanetBody();
        planet.entityId = 100L;
        planet.systemId = system.systemId;
        planet.planetTypeId = "TERRESTRIAL";
        planet.posWorldGU = new SpacePosition(0, 0, 0);
        planet.radiusGU = 100.0;

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
        world = new WorldState(time, 1000, astro);

        world.registerEntity(planet);
        world.nationManager.registerNation(NATION_ID);

        // 生成殖民舰并归属国家喵
        colonyShip = new ShipBody();
        colonyShip.entityId = 300L;
        colonyShip.systemId = system.systemId;
        colonyShip.designId = ShipDesign.DESIGN_ID_COLONY;
        colonyShip.posWorldGU = new SpacePosition(50, 0, 0);
        world.registerEntity(colonyShip);
        world.assetManager.assignToNation(colonyShip.entityId, NATION_ID);
    }

    private void assertColonizationRejected(String expectedMessage) throws Exception {
        ColonizePlanetCommand cmd = new ColonizePlanetCommand(colonyShip.entityId, planet.entityId, NATION_ID);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> handler.handle(cmd, world, 0));
        assertEquals(expectedMessage, ex.getMessage());
    }

    // ── 成功路径 ──────────────────────────────────────────────

    @Test
    void colonizeSucceedsAndUpdatesOwnership() throws Exception {
        ColonizePlanetCommand cmd = new ColonizePlanetCommand(colonyShip.entityId, planet.entityId, NATION_ID);
        handler.handle(cmd, world, 0);

        // 1. 行星归属喵
        assertEquals(NATION_ID, planet.ownerNationId);
        assertTrue(world.nationManager.getNationState(NATION_ID)
                .getOwnedEntityIds(EntityType.PLANET).contains(planet.entityId));

        // 2. 城市/殖民地状态喵
        assertNotNull(planet.surface);
        assertEquals(1, planet.surface.cities.size());
        City city = planet.surface.cities.get(0);
        assertTrue(city.isPlanetaryCapital);
        assertEquals(planet.entityId, city.planetEntityId);
        assertEquals(colonyShip.entityId, city.sourceShipEntityId);
        assertEquals("OUTPOST", city.cityStage);
        assertEquals(100L, city.population);
        assertEquals(planet.surface.planetaryCapitalCityId, city.cityId);
        assertEquals(regionIdOfFirstRegion(), city.regionId);

        // 3. 殖民舰被消耗（转化为城市实体，舰船本体消失）喵
        assertFalse(world.entitiesById.containsKey(colonyShip.entityId));
        assertFalse(world.nationManager.getNationState(NATION_ID)
                .getOwnedEntityIds(EntityType.SHIP).contains(colonyShip.entityId));

        // 4. 脏标记触发基线快照推送喵
        assertTrue(world.baselineDirty);
    }

    @Test
    void colonizeResultIsReadableByDailySettlementState() throws Exception {
        ColonizePlanetCommand cmd = new ColonizePlanetCommand(colonyShip.entityId, planet.entityId, NATION_ID);
        handler.handle(cmd, world, 0);

        // 用同一 WorldState 构造运行时并发布一次低频基线快照喵
        StarAxisGameRuntime runtime = new StarAxisGameRuntime(world);
        runtime.start();

        DailySettlementState baseline = runtime.getDailySettlementStateBufferForReadonly().getActive();

        // 1. 行星地表快照包含城市/殖民地状态喵
        DailySettlementState.PlanetSurfaceDailySnapshot planetSnap = baseline.planetSurfacesByPlanetId.get(
                planet.entityId);
        assertNotNull(planetSnap, "planet_surface_snapshot_expected");
        assertEquals(1, planetSnap.surfaceRegions.size());
        assertEquals(1, planetSnap.cities.size());

        DailySettlementState.CityDailySnapshot citySnap = planetSnap.cities.get(0);
        assertEquals("OUTPOST", citySnap.cityStage);
        assertEquals(100L, citySnap.population);
        assertTrue(citySnap.isPlanetaryCapital);

        // 2. 国家资产表包含行星、不含已消耗的殖民舰喵
        var nationAssets = baseline.nationAssetsByNationId.get(NATION_ID);
        assertNotNull(nationAssets);
        assertTrue(nationAssets.get(EntityType.PLANET).contains(planet.entityId));
        var shipAssets = nationAssets.get(EntityType.SHIP);
        assertTrue(shipAssets == null || !shipAssets.contains(colonyShip.entityId));
    }

    // ── 真实轨道回归：生产数据口径（OrbitSolver 实时解算） ──────

    /**
     * 构造与生产数据口径一致的世界状态喵：
     * - 真实 StarBody 注册到 WorldState（恒星 posWorldGU = 星系坐标，作为行星轨道中心）喵
     * - 行星 orbitCenterEntityId 指向恒星，轨道根数有效；行星 posWorldGU 仍为恒星中心
     *   （生产生成期口径，真实轨道位置由 OrbitSolver 按当前游戏时间实时解算）喵
     */
    private ProductionWorld buildProductionLikeWorld() {
        SimulationTime time = new SimulationTime();
        time.simulationTick = 1L;
        time.gameDatetimeDay = 30;
        time.totalGameSecondsAcc = 3600.0;

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
        // 生产口径：行星 posWorldGU 仍为恒星中心，真实轨道位置由轨道根数实时解算喵
        planet.posWorldGU = star.posWorldGU;
        planet.orbitCenterEntityId = star.entityId;
        planet.semiMajorAxisGU = 5000.0;
        planet.eccentricity = 0.1;
        planet.inclinationDeg = 5.0;
        planet.longitudeOfAscendingNodeDeg = 0.0;
        planet.periapsisArgDeg = 30.0;
        planet.meanAnomalyDegAtEpoch = 45.0;
        planet.orbitalPeriodDays = 30.0;

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
        world.registerEntity(ship);
        world.assetManager.assignToNation(ship.entityId, NATION_ID);

        return new ProductionWorld(world, system, star, planet, ship);
    }

    /**
     * 生产口径世界状态容器喵。
     */
    private static final class ProductionWorld {
        final WorldState world;
        final StarSystem system;
        final StarBody star;
        final PlanetBody planet;
        final ShipBody ship;

        ProductionWorld(WorldState world, StarSystem system, StarBody star, PlanetBody planet, ShipBody ship) {
            this.world = world;
            this.system = system;
            this.star = star;
            this.planet = planet;
            this.ship = ship;
        }
    }

    /**
     * 行星当前权威轨道位置（恒星中心 + OrbitSolver 解算偏移），与 ColonizePlanetHandler 口径一致喵。
     */
    private SpacePosition solvePlanetPosition(ProductionWorld pw) {
        return pw.star.posWorldGU.add(
                OrbitSolver.solve(pw.planet.toOrbitalElements(), pw.world.time.totalGameSecondsAcc));
    }

    /**
     * 回归：行星 posWorldGU 仍为恒星中心且轨道字段有效时，殖民舰位于 OrbitSolver
     * 当前解算位置附近（100GU 内）即可殖民成功；并验证星区声明与国家资产表一致喵。
     */
    @Test
    void colonizeSucceedsWhenShipNearOrbitSolverPosition() throws Exception {
        ProductionWorld pw = buildProductionLikeWorld();

        SpacePosition solverPos = solvePlanetPosition(pw);

        // 行星 posWorldGU 保持恒星中心（生产口径），轨道字段有效；真实轨道位置距恒星中心
        // 远超 1000GU，证明距离校验使用的是 OrbitSolver 解算结果而非恒星中心的 posWorldGU 喵
        assertEquals(pw.star.posWorldGU, pw.planet.posWorldGU);
        assertTrue(pw.planet.toOrbitalElements().isValid());
        assertTrue(solverPos.distanceTo(pw.planet.posWorldGU) > 1000.0);

        // 殖民舰位于解算位置附近（100GU 内）喵
        pw.ship.posWorldGU = solverPos.add(100, 0, 0);

        ColonizePlanetCommand cmd = new ColonizePlanetCommand(pw.ship.entityId, pw.planet.entityId, NATION_ID);
        handler.handle(cmd, pw.world, 0);

        // 1. 行星归属喵
        assertEquals(NATION_ID, pw.planet.ownerNationId);
        assertTrue(pw.world.nationManager.getNationState(NATION_ID)
                .getOwnedEntityIds(EntityType.PLANET).contains(pw.planet.entityId));

        // 2. 星区声明与资产表一致：system.ownerNationId 更新，恒星同步进入国家资产表喵
        assertEquals(NATION_ID, pw.system.ownerNationId);
        assertEquals(NATION_ID, pw.star.ownerNationId);
        assertTrue(pw.world.nationManager.getNationState(NATION_ID)
                .getOwnedEntityIds(EntityType.STAR).contains(pw.star.entityId));

        // 3. 行星 posWorldGU 与轨道字段口径不被殖民覆盖喵
        assertEquals(pw.star.posWorldGU, pw.planet.posWorldGU);
        assertTrue(pw.planet.toOrbitalElements().isValid());

        // 4. 殖民地创建、殖民舰消耗喵
        assertNotNull(pw.planet.surface);
        assertEquals(1, pw.planet.surface.cities.size());
        assertFalse(pw.world.entitiesById.containsKey(pw.ship.entityId));
    }

    /**
     * 回归：殖民舰位于 OrbitSolver 解算位置外侧超过 1000 GU 时殖民失败，
     * 行星/星区归属均不得变更喵。
     */
    @Test
    void colonizeFailsWhenShipOutsideOrbitSolverPositionBeyond1000GU() throws Exception {
        ProductionWorld pw = buildProductionLikeWorld();

        SpacePosition solverPos = solvePlanetPosition(pw);

        // 殖民舰距解算位置 1500 GU（超过 1000 GU 殖民上限）喵
        pw.ship.posWorldGU = solverPos.add(1500, 0, 0);

        ColonizePlanetCommand cmd = new ColonizePlanetCommand(pw.ship.entityId, pw.planet.entityId, NATION_ID);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> handler.handle(cmd, pw.world, 0));
        assertEquals("ship_too_far_from_planet", ex.getMessage());

        // 殖民失败：归属与星区声明均不得变更喵
        assertNull(pw.planet.ownerNationId);
        assertNull(pw.system.ownerNationId);
        assertNull(pw.star.ownerNationId);
        assertFalse(pw.world.nationManager.getNationState(NATION_ID)
                .getOwnedEntityIds(EntityType.PLANET).contains(pw.planet.entityId));
        assertFalse(pw.world.nationManager.getNationState(NATION_ID)
                .getOwnedEntityIds(EntityType.STAR).contains(pw.star.entityId));
        assertTrue(pw.world.entitiesById.containsKey(pw.ship.entityId));
    }

    // ── 工业接入：殖民后库存与设施 ─────────────────────────────

    @Test
    void colonizeCreatesPlanetInventoryAndFacilities() throws Exception {
        ColonizePlanetCommand cmd = new ColonizePlanetCommand(colonyShip.entityId, planet.entityId, NATION_ID);
        handler.handle(cmd, world, 0);

        // 1. 行星本地库存已创建且只创建一份喵
        LocalInventory inventory = world.industryRegistry.getInventoryByOwner(planet.entityId);
        assertNotNull(inventory, "colonized_planet_should_have_local_inventory");
        assertEquals(planet.entityId, inventory.ownerEntityId);
        assertEquals(1, world.industryRegistry.inventoriesById.size());

        // 2. 初始能源库存已预存（G2 第一阶段临时资源，供电解槽消耗）喵
        assertEquals(ColonialStartupKit.INITIAL_ENERGY, inventory.getAmount(SubstanceId.ENERGY));

        // 3. 水采集设施已创建喵
        assertEquals(1, world.industryRegistry.extractionFacilitiesById.size());
        ResourceExtractionFacility extractor = world.industryRegistry.extractionFacilitiesById.values()
                .iterator().next();
        assertEquals(ResourceExtractionFacility.TYPE_WATER_EXTRACTION, extractor.facilityType);
        assertEquals(inventory.inventoryId, extractor.inventoryId);
        assertEquals(planet.entityId, extractor.locationEntityId);
        assertEquals(SubstanceId.WATER, extractor.resourceId);
        assertEquals(ColonialStartupKit.WATER_EXTRACTION_PER_DAY, extractor.amountPerDay);

        // 4. 水电解槽已创建，与采集设施共享同一库存喵
        assertEquals(1, world.industryRegistry.facilitiesById.size());
        ProcessingFacility electrolyzer = world.industryRegistry.facilitiesById.values().iterator().next();
        assertEquals(RecipeRepository.FACILITY_TYPE_ELECTROLYZER, electrolyzer.facilityType);
        assertEquals(inventory.inventoryId, electrolyzer.inventoryId);
        assertEquals(planet.entityId, electrolyzer.locationEntityId);
        assertEquals(RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID, electrolyzer.activeRecipeId);
    }

    @Test
    void colonizedPlanetSettlesWaterExtractionAndElectrolysis() throws Exception {
        ColonizePlanetCommand cmd = new ColonizePlanetCommand(colonyShip.entityId, planet.entityId, NATION_ID);
        handler.handle(cmd, world, 0);

        LocalInventory inventory = world.industryRegistry.getInventoryByOwner(planet.entityId);
        assertNotNull(inventory);

        // 使用默认水电解配方执行一次日结算（采集先于加工）喵
        RecipeRepository repository = new RecipeRepository(new ObjectMapper());
        repository.setRecipes(RecipeRepository.defaultRecipes());
        ProductionSettlementService service = new ProductionSettlementService(repository);
        SettlementReport report = service.settleDay(world.industryRegistry, 100);

        // 1. 采集设施产出 20 水喵
        assertEquals(1, report.extractions.size());
        assertEquals(ColonialStartupKit.WATER_EXTRACTION_PER_DAY,
                report.getTotalExtracted().get(SubstanceId.WATER));

        // 2. 电解槽同日消耗 2 水 + 1 能源，产出 2H2 + 1O2喵
        assertEquals(1, report.getTotalProducedBatches());
        assertEquals(ColonialStartupKit.WATER_EXTRACTION_PER_DAY - 2.0, inventory.getAmount(SubstanceId.WATER));
        assertEquals(ColonialStartupKit.INITIAL_ENERGY - 1.0, inventory.getAmount(SubstanceId.ENERGY));
        assertEquals(2.0, inventory.getAmount(SubstanceId.HYDROGEN));
        assertEquals(1.0, inventory.getAmount(SubstanceId.OXYGEN));
    }

    @Test
    void repeatedColonizeDoesNotDuplicateInventoryOrFacilities() throws Exception {
        ColonizePlanetCommand first = new ColonizePlanetCommand(colonyShip.entityId, planet.entityId, NATION_ID);
        handler.handle(first, world, 0);

        // 第二个殖民舰：行星已被殖民，重复殖民必须失败且不新增任何工业状态喵
        ShipBody secondShip = new ShipBody();
        secondShip.entityId = 500L;
        secondShip.systemId = system.systemId;
        secondShip.designId = ShipDesign.DESIGN_ID_COLONY;
        secondShip.posWorldGU = new SpacePosition(50, 0, 0);
        world.registerEntity(secondShip);
        world.assetManager.assignToNation(secondShip.entityId, NATION_ID);

        ColonizePlanetCommand second = new ColonizePlanetCommand(secondShip.entityId, planet.entityId, NATION_ID);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> handler.handle(second, world, 0));
        assertEquals("planet_already_owned", ex.getMessage());

        // 库存/设施不得重复创建，第二个殖民舰未被消耗喵
        assertEquals(1, world.industryRegistry.inventoriesById.size());
        assertEquals(1, world.industryRegistry.extractionFacilitiesById.size());
        assertEquals(1, world.industryRegistry.facilitiesById.size());
        assertTrue(world.entitiesById.containsKey(secondShip.entityId));
        assertNotNull(world.industryRegistry.getInventoryByOwner(planet.entityId));
    }

    // ── 失败路径：目标已占有 ───────────────────────────────────

    @Test
    void colonizeFailsWhenPlanetAlreadyOwned() throws Exception {
        world.nationManager.registerNation(NATION_OTHER);
        world.assetManager.assignToNation(planet.entityId, NATION_OTHER);

        assertColonizationRejected("planet_already_owned");
        assertEquals(NATION_OTHER, planet.ownerNationId);
    }

    // ── 失败路径：舰船类型错误 ─────────────────────────────────

    @Test
    void colonizeFailsWhenShipIsNotColonyShip() throws Exception {
        colonyShip.designId = "default_frigate";

        assertColonizationRejected("ship_is_not_colony_ship");
        assertNull(planet.ownerNationId);
        assertTrue(world.entitiesById.containsKey(colonyShip.entityId));
    }

    @Test
    void colonizeFailsWhenShipEntityIsNotShipBody() throws Exception {
        Entity fakeShip = new Entity();
        fakeShip.entityId = 400L;
        fakeShip.entityType = EntityType.SHIP;
        fakeShip.posWorldGU = new SpacePosition(10, 0, 0);
        world.registerEntity(fakeShip);

        ColonizePlanetCommand cmd = new ColonizePlanetCommand(fakeShip.entityId, planet.entityId, NATION_ID);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> handler.handle(cmd, world, 0));
        assertEquals("ship_is_not_colony_ship", ex.getMessage());
    }

    // ── 失败路径：距离过远 ─────────────────────────────────────

    @Test
    void colonizeFailsWhenShipTooFarFromPlanet() throws Exception {
        colonyShip.posWorldGU = new SpacePosition(5000, 0, 0);

        assertColonizationRejected("ship_too_far_from_planet");
        assertNull(planet.ownerNationId);
    }

    // ── 失败路径：归属不匹配 ───────────────────────────────────

    @Test
    void colonizeFailsWhenShipOwnerMismatch() throws Exception {
        world.nationManager.registerNation(NATION_OTHER);
        world.assetManager.assignToNation(colonyShip.entityId, NATION_OTHER);

        assertColonizationRejected("ship_owner_mismatch");
        assertNull(planet.ownerNationId);
    }

    // ── 失败路径：位置缺失 ─────────────────────────────────────

    @Test
    void colonizeFailsWhenShipPositionMissing() throws Exception {
        colonyShip.posWorldGU = null;

        assertColonizationRejected("entity_position_missing");
    }

    @Test
    void colonizeFailsWhenPlanetPositionMissing() throws Exception {
        planet.posWorldGU = null;

        assertColonizationRejected("entity_position_missing");
    }

    // ── 失败路径：不可殖民 ─────────────────────────────────────

    @Test
    void colonizeFailsWhenPlanetNotColonizable() throws Exception {
        planet.planetTypeId = "GAS_GIANT";

        assertColonizationRejected("planet_not_colonizable");
        assertNull(planet.ownerNationId);
    }

    // ── 失败路径：国家状态 ─────────────────────────────────────

    @Test
    void colonizeFailsWhenNationNotFound() throws Exception {
        ColonizePlanetCommand cmd = new ColonizePlanetCommand(colonyShip.entityId, planet.entityId, NATION_GHOST);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> handler.handle(cmd, world, 0));
        assertEquals("nation_not_found", ex.getMessage());
    }

    @Test
    void colonizeFailsWhenNationInactive() throws Exception {
        world.nationManager.getNationState(NATION_ID).setActive(false);

        assertColonizationRejected("nation_inactive");
        assertNull(planet.ownerNationId);
    }

    // ── 失败路径：参数/实体缺失 ────────────────────────────────

    @Test
    void colonizeFailsWhenParametersInvalid() {
        ColonizePlanetCommand cmd = new ColonizePlanetCommand(0, planet.entityId, NATION_ID);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> handler.handle(cmd, world, 0));
        assertEquals("invalid_colonization_parameters", ex.getMessage());
    }

    @Test
    void colonizeFailsWhenShipEntityNotFound() {
        ColonizePlanetCommand cmd = new ColonizePlanetCommand(9999L, planet.entityId, NATION_ID);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> handler.handle(cmd, world, 0));
        assertEquals("ship_entity_not_found", ex.getMessage());
    }

    // ── 工具方法 ───────────────────────────────────────────────

    private long regionIdOfFirstRegion() {
        return planet.surface.surfaceRegions.get(0).regionId;
    }
}
