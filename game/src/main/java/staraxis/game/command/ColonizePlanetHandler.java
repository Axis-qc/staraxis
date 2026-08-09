package staraxis.game.command;

import staraxis.game.astro.Habitability;
import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.industry.ColonialStartupKit;
import staraxis.game.industry.IndustryRegistry;
import staraxis.game.industry.LocalInventory;
import staraxis.game.industry.RecipeRepository;
import staraxis.game.industry.ResourceExtractionFacility;
import staraxis.game.industry.SubstanceId;
import staraxis.game.planet.PlanetSurface;
import staraxis.game.planet.city.City;
import staraxis.game.planet.surface.SurfaceRegion;
import staraxis.game.ship.ShipBody;
import staraxis.game.ship.ShipDesign;
import staraxis.game.space.OrbitalElements;
import staraxis.game.space.OrbitSolver;
import staraxis.game.space.SpacePosition;
import staraxis.game.state.WorldState;

/**
 * ColonizePlanetHandler
 *
 * @description
 *              ColonizePlanetCommand 的处理器，在模拟 tick 内执行殖民逻辑喵。
 *
 *              作用：
 *              - 验证殖民条件：行星无主、殖民舰属于指定国家、殖民舰类型、距离、可殖民性、国家状态等喵
 *              - 执行殖民操作：将行星所有权分配给指定国家喵
 *              - 创建最小城市/殖民地状态（殖民舰转化为城市实体，舰船本体消失）喵
 *              - 通过 AssetManager 更新国家资产表，保持归属一致性喵
 *              - 殖民成功后声明行星所在恒星系的星区归属喵
 *
 * @important_notes
 *                  - 殖民操作需要严格的条件验证，避免非法殖民喵
 *                  - 殖民成功后通过 AssetManager 分配归属，禁止直接写 ownerNationId 喵
 *                  - 距离校验基于行星当前轨道位置（OrbitSolver 实时解算），不依赖
 *                    生成期被错误设为恒星中心的 posWorldGU；无轨道数据时回退手动 posWorldGU喵
 */
public class ColonizePlanetHandler implements CommandHandler<ColonizePlanetCommand> {

    /** 最大殖民距离：殖民舰需要在行星的多少GU距离内才能殖民喵 */
    private static final double COLONIZATION_MAX_DISTANCE_GU = 1000.0; // 距离行星1000GU喵

    /** 初始殖民地人口喵。 */
    private static final long COLONY_INITIAL_POPULATION = 100L;

    /** 初始殖民地专精（与 CityProductionCalculator 默认权重对齐）喵。 */
    private static final String COLONY_INITIAL_SPECIALIZATION = "MINING";

    /** 无地表区域时的城市默认名称词根喵。 */
    private static final String COLONY_CITY_FALLBACK_NAME = "殖民地";

    /** 殖民地城市名称后缀喵。 */
    private static final String COLONY_CITY_NAME_SUFFIX = "前哨站";

    @Override
    public void handle(ColonizePlanetCommand command, WorldState worldState, double dtGameHours) throws Exception {
        if (command == null) {
            throw new IllegalArgumentException("command_required");
        }
        if (worldState == null) {
            throw new IllegalArgumentException("world_state_required");
        }

        long shipEntityId = command.getShipEntityId();
        long planetEntityId = command.getPlanetEntityId();
        String nationId = command.getNationId();

        // 1. 验证参数合法性喵
        if (shipEntityId <= 0 || planetEntityId <= 0 || nationId == null || nationId.isBlank()) {
            throw new IllegalArgumentException("invalid_colonization_parameters");
        }

        // 2. 获取实体喵
        Entity shipEntity = worldState.entitiesById.get(shipEntityId);
        Entity planetEntity = worldState.entitiesById.get(planetEntityId);

        if (shipEntity == null) {
            throw new IllegalArgumentException("ship_entity_not_found");
        }
        if (planetEntity == null) {
            throw new IllegalArgumentException("planet_entity_not_found");
        }

        // 3. 验证殖民舰类型：必须是 SHIP 且为殖民舰蓝图或带 COLONY 标记喵
        if (!(shipEntity instanceof ShipBody ship) || shipEntity.entityType != EntityType.SHIP) {
            throw new IllegalArgumentException("ship_is_not_colony_ship");
        }
        boolean isColonyShip = ShipDesign.DESIGN_ID_COLONY.equals(ship.designId)
                || (ship.customFlags != null && ship.customFlags.contains(ShipDesign.FLAG_COLONY));
        if (!isColonyShip) {
            throw new IllegalArgumentException("ship_is_not_colony_ship");
        }

        // 4. 验证行星类型喵
        if (!(planetEntity instanceof PlanetBody planet) || planetEntity.entityType != EntityType.PLANET) {
            throw new IllegalArgumentException("target_is_not_planet");
        }

        // 5. 验证行星无主喵
        if (planetEntity.ownerNationId != null && !planetEntity.ownerNationId.isBlank()) {
            throw new IllegalArgumentException("planet_already_owned");
        }

        // 5b. 重复殖民防护：行星已存在城市即为已殖民，禁止二次殖民喵
        if (planet.hasCities()) {
            throw new IllegalArgumentException("planet_already_colonized");
        }

        // 6. 验证可殖民性：非 INHOSPITABLE（不宜居）均可殖民喵
        if (planet.computeHabitability() == Habitability.INHOSPITABLE) {
            throw new IllegalArgumentException("planet_not_colonizable");
        }

        // 7. 验证距离喵
        // 距离校验基于行星当前轨道位置（按轨道根数 + 当前游戏时间实时解算），
        // 不依赖生成期被错误设为恒星中心的 posWorldGU；无有效轨道数据时回退手动 posWorldGU喵
        if (shipEntity.posWorldGU == null) {
            throw new IllegalArgumentException("entity_position_missing");
        }
        SpacePosition planetCurrentPos = resolvePlanetCurrentPosition(worldState, planet);
        if (planetCurrentPos == null) {
            throw new IllegalArgumentException("entity_position_missing");
        }
        double distance = shipEntity.posWorldGU.distanceTo(planetCurrentPos);

        // 验证距离是否在允许的殖民范围内喵（1000GU）喵
        if (distance > COLONIZATION_MAX_DISTANCE_GU) {
            throw new IllegalArgumentException("ship_too_far_from_planet");
        }

        // 8. 验证国家状态喵
        var nation = worldState.nationManager.getNationState(nationId);
        if (nation == null) {
            throw new IllegalArgumentException("nation_not_found");
        }
        if (!nation.isActive()) {
            throw new IllegalArgumentException("nation_inactive");
        }

        // 9. 验证舰船归属：殖民舰必须属于命令指定的国家喵
        if (shipEntity.ownerNationId == null || !shipEntity.ownerNationId.equals(nationId)) {
            throw new IllegalArgumentException("ship_owner_mismatch");
        }

        // 10. 执行殖民操作喵
        // 10.1 行星所有权分配（AssetManager 统一维护国家资产表）喵
        worldState.assetManager.assignToNation(planetEntityId, nationId);

        // 10.1b 星区归属：殖民成功后声明行星所在恒星系的星区归属喵
        StarSystem system = findStarSystemByPlanet(worldState, planet);
        if (system != null) {
            worldState.assetManager.assignStarSystemClaimToNation(system, nationId);
        }

        // 10.2 创建最小城市/殖民地状态（殖民舰转化为城市实体，舰船本体消失）喵
        createColonyCity(worldState, planet, shipEntityId);

        // 10.2b 初始化行星工业：本地库存 + 水采集设施 + 水电解槽（G2 第一阶段闭环）喵
        setupColonialIndustry(worldState, planet);

        // 10.3 消耗殖民舰：清理归属并从世界中移除喵
        consumeColonyShip(worldState, shipEntityId);

        // 10.4 标记低频基线快照为脏以触发推送喵
        worldState.baselineDirty = true;
    }

    /**
     * 计算行星当前权威轨道位置喵。
     *
     * 优先按轨道根数 + 当前游戏时间实时解算（OrbitSolver），与 client SystemView
     * 渲染口径一致；行星无有效轨道数据（手动构造的测试行星）时回退 posWorldGU，
     * 保留手动位置测试语义喵。
     *
     * @param worldState 权威世界状态
     * @param planet     目标行星
     * @return 行星当前世界位置；无位置可解析时返回 null
     */
    private SpacePosition resolvePlanetCurrentPosition(WorldState worldState, PlanetBody planet) {
        OrbitalElements elements = planet.toOrbitalElements();
        if (elements != null && elements.isValid()) {
            SpacePosition orbitCenterPos = resolveOrbitCenterWorldPos(worldState, planet);
            if (orbitCenterPos != null) {
                double gameSeconds = worldState.time.totalGameSecondsAcc;
                SpacePosition orbitOffset = OrbitSolver.solve(elements, gameSeconds);
                return orbitCenterPos.add(orbitOffset);
            }
        }
        // 无有效轨道数据（手动构造）时回退手动 posWorldGU，保留测试语义喵
        return planet.posWorldGU;
    }

    /**
     * 解析行星轨道中心（恒星）的权威世界位置喵。
     *
     * 行星的轨道中心实体为所属恒星，其世界坐标为恒星 posWorldGU；
     * 无法定位恒星时回退到所属恒星系的 galaxyPos（SystemView 原点口径）喵。
     *
     * @param worldState 权威世界状态
     * @param planet     目标行星
     * @return 轨道中心世界位置；无法定位时返回 null
     */
    private SpacePosition resolveOrbitCenterWorldPos(WorldState worldState, PlanetBody planet) {
        if (planet.orbitCenterEntityId > 0) {
            Entity center = worldState.entitiesById.get(planet.orbitCenterEntityId);
            if (center != null && center.posWorldGU != null) {
                return center.posWorldGU;
            }
        }
        for (StarSystem system : worldState.astro.getSystemsView()) {
            if (system == null || system.systemId != planet.systemId) {
                continue;
            }
            if (system.stars != null) {
                for (StarBody star : system.stars) {
                    if (star != null && star.posWorldGU != null) {
                        return star.posWorldGU;
                    }
                }
            }
            return system.galaxyPos;
        }
        return null;
    }

    /**
     * 根据行星所属恒星系ID查找其恒星系喵。
     *
     * @param worldState 权威世界状态
     * @param planet     目标行星
     * @return 所属恒星系；未找到返回 null
     */
    private StarSystem findStarSystemByPlanet(WorldState worldState, PlanetBody planet) {
        for (StarSystem system : worldState.astro.getSystemsView()) {
            if (system != null && system.systemId == planet.systemId) {
                return system;
            }
        }
        return null;
    }

    /**
     * 在行星上创建首个殖民地城市喵。
     *
     * 落点选择：
     * - 优先选择第一个仍有剩余可开发空间的大陆区域喵
     * - 若无合适区域则退回第一个地表区域；无地表区域时以 regionId=0 兜底挂载喵
     *
     * @param worldState   权威世界状态（用于生成全局唯一城市ID）
     * @param planet       目标行星实体
     * @param colonyShipId 殖民舰实体ID（作为城市溯源 sourceShipEntityId 记录）喵
     */
    private void createColonyCity(WorldState worldState, PlanetBody planet, long colonyShipId) {
        // 防御：行星无地表组件时创建空地表容器，保证城市状态可挂载喵
        if (planet.surface == null) {
            planet.surface = new PlanetSurface(planet.entityId);
            planet.surfaceComponentId = planet.entityId;
        }
        PlanetSurface surface = planet.surface;

        // 选取落点区域喵
        SurfaceRegion region = surface.getRegionsSuitableForNewCity().stream()
                .findFirst()
                .orElse(surface.surfaceRegions.isEmpty() ? null : surface.surfaceRegions.get(0));

        City city = new City();
        city.cityId = worldState.generateEntityId();
        city.planetEntityId = planet.entityId;
        city.regionId = region != null ? region.regionId : 0;
        city.sourceShipEntityId = colonyShipId;
        city.name = buildColonyCityName(region);
        city.population = COLONY_INITIAL_POPULATION;
        city.specializationId = COLONY_INITIAL_SPECIALIZATION;
        city.lastPopulationGrowthDay = worldState.time.gameDatetimeDay;
        city.attractiveness = 1.0;

        surface.addCity(city);
    }

    /**
     * 生成殖民地城市名称喵。
     * 优先使用落点区域名称作为词根，无区域时使用默认词根喵。
     *
     * @param region 落点地表区域（可为 null）
     * @return 城市名称
     */
    private String buildColonyCityName(SurfaceRegion region) {
        String base = region != null && region.name != null && !region.name.isBlank()
                ? region.name
                : COLONY_CITY_FALLBACK_NAME;
        return base + COLONY_CITY_NAME_SUFFIX;
    }

    /**
     * 殖民成功后初始化行星工业（G2 第一阶段：本地库存 + 水采集 + 水电解闭环）。
     *
     * 幂等保证：行星已存在本地库存时直接返回，重复殖民不会创建第二套库存/设施。
     *
     * 能源来源策略：殖民初始能源库存（ColonialStartupKit.INITIAL_ENERGY）预存到
     * 行星本地库存，作为 G2 第一阶段临时资源，供电解槽消耗；正式发电系统落地前
     * 配方不会凭空生成能源，电解闭环能源完全来自该初始库存喵。
     *
     * @param worldState 权威世界状态
     * @param planet     目标行星实体
     */
    private void setupColonialIndustry(WorldState worldState, PlanetBody planet) {
        IndustryRegistry registry = worldState.industryRegistry;
        // 幂等：行星已存在本地库存（已殖民）则不重复创建喵
        LocalInventory inventory = registry.getInventoryByOwner(planet.entityId);
        if (inventory != null) {
            return;
        }

        // 1. 创建行星本地库存，并预存 G2 第一阶段临时能源（供电解槽消耗）喵
        inventory = registry.createInventory(planet.entityId);
        inventory.deposit(SubstanceId.ENERGY, ColonialStartupKit.INITIAL_ENERGY, worldState.time.simulationTick);

        // 2. 水采集设施：每日向该库存写入固定水产出喵
        registry.createExtractionFacility(ResourceExtractionFacility.TYPE_WATER_EXTRACTION,
                inventory.inventoryId, planet.entityId, SubstanceId.WATER,
                ColonialStartupKit.WATER_EXTRACTION_PER_DAY);

        // 3. 电解槽（ProcessingFacility）：水电解配方，与采集设施共享同一库存喵
        registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER,
                inventory.inventoryId, planet.entityId, RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);
    }

    /**
     * 消耗殖民舰喵。
     * 清理国家/玩家归属并移除舰船实体（殖民舰转化为城市实体，舰船本体消失）喵。
     *
     * @param worldState   权威世界状态
     * @param colonyShipId 殖民舰实体ID
     */
    private void consumeColonyShip(WorldState worldState, long colonyShipId) {
        worldState.assetManager.releaseAllOwnership(colonyShipId);
        worldState.removeEntity(colonyShipId);
    }

}
