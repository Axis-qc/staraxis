package staraxis.game.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import staraxis.game.entity.EntityType;
import staraxis.game.state.snapshot.EntitySnapshot;

/**
 * DailySettlementState
 *
 * 低频基线快照（Low-Frequency Baseline Snapshot）：
 * - 每 20 tick 发布一次，承载不需要每 tick 更新的数据。
 * - 包含：恒星/行星/卫星/重心 基线快照、国家资产表、行星地表数据、
 *   玩家→国家映射、各国家可见星系列表、各星系情报探测等级。
 * - 双缓冲 + volatile swap，外部只读。
 */
public class DailySettlementState {

    /**
     * 该快照对应的“已落账日序号”（历史兼容字段）。
     *
     * 说明：
     * - 在 1:1 时间模式下，不再以“日”为唯一结算粒度，后续可能仅作为统计/展示维度使用。
     */
    public int settledDay;

    /**
     * 该快照生成时的游戏总秒数（更通用的时间口径）。
     */
    public long settledAtGameSeconds;

    /** 星区总数（用于验证快照链路）。 */
    public int sectorCount;

    /** 该快照对应的 simulationTick（主要版本号）。 */
    public long baselineTick;

    /** 行星地表（低频/静态）快照：Key 为 planetEntityId 喵。 */
    public Map<Long, PlanetSurfaceDailySnapshot> planetSurfacesByPlanetId;

    /** 国家资产基线快照：nationId -> (EntityType -> 该类型下实体ID列表) 喵。 */
    public Map<String, Map<EntityType, List<Long>>> nationAssetsByNationId;

    /** 公开实体基线快照（按恒星系聚合）：systemId字符串 -> 公开实体快照列表 喵。 */
    public Map<String, List<EntitySnapshot>> publicEntityBaselinesBySectorKey;

    /** 玩家→国家映射（playerId -> nationId）。供 webnet 查询玩家所属国家。 */
    public Map<String, String> playerToNationMap;

    /** 国家→玩家列表映射（nationId -> playerId 列表）。 */
    public Map<String, List<String>> nationToPlayerIdsMap;

    /** 各国家的可见星系 ID 列表（预计算，替代 visibilitySystem 实时查询）。 */
    public Map<String, Set<Long>> visibleSystemIdsByNationId;

    /** 各星系对各国家的探测等级（预计算，替代 intelSystem 实时查询）。
     *  结构: nationId -> (systemId -> detectorLevel) */
    public Map<String, Map<Long, Integer>> detectorLevelByNationAndSystem;

    public DailySettlementState() {
    }

    public void resetForFill() {
        settledDay = 0;
        settledAtGameSeconds = 0L;
        sectorCount = 0;
        baselineTick = 0L;
        planetSurfacesByPlanetId = null;
        nationAssetsByNationId = null;
        publicEntityBaselinesBySectorKey = null;
        playerToNationMap = null;
        nationToPlayerIdsMap = null;
        visibleSystemIdsByNationId = null;
        detectorLevelByNationAndSystem = null;
    }

    /**
     * PlanetSurfaceDailySnapshot（行星地表日快照）喵。
     *
     * G2.7 起承载行星的工业只读快照：
     * - {@link #inventories}：该行星本地库存快照（通常 0 或 1 个）。
     * - {@link #extractionFacilities} / {@link #processingFacilities}：该行星上的采集/加工设施只读快照。
     * - {@link #inTransitTransfers}：与该行星库存相关的在途运输快照。
     * - {@link #lastSettlementReport}：最近一次与该行星相关的日结算结果（未结算过为 null）。
     *
     * 空值约定（G2.7）：
     * - 无库存/设施/运输时，对应列表为稳定空集合（不可变），禁止返回 null。
     * - 从未结算过时 {@link #lastSettlementReport} 为 null。
     * - 所有 Map/List 均为深拷贝，外部修改不会影响 game 权威状态。
     */
    public static class PlanetSurfaceDailySnapshot {
        public final long planetEntityId;
        public final List<SurfaceRegionDailySnapshot> surfaceRegions;

        /** 该行星当前城市/殖民地快照列表（供外部读取殖民地状态）喵。 */
        public final List<CityDailySnapshot> cities;

        /** 该行星本地库存只读快照列表（深拷贝，无库存时空集合）喵。 */
        public final List<InventoryDailySnapshot> inventories;

        /** 该行星上的采集设施只读快照列表（确定性 ID 顺序，深拷贝）喵。 */
        public final List<ExtractionFacilityDailySnapshot> extractionFacilities;

        /** 该行星上的加工设施只读快照列表（确定性 ID 顺序，深拷贝）喵。 */
        public final List<ProcessingFacilityDailySnapshot> processingFacilities;

        /** 与该行星库存相关的在途运输只读快照列表（确定性 ID 顺序，深拷贝）喵。 */
        public final List<TransferDailySnapshot> inTransitTransfers;

        /** 该行星最近一次日结算结果只读快照（从未结算为 null）喵。 */
        public final SettlementReportDailySnapshot lastSettlementReport;

        public PlanetSurfaceDailySnapshot(long planetEntityId, List<SurfaceRegionDailySnapshot> surfaceRegions) {
            this(planetEntityId, surfaceRegions, List.of());
        }

        public PlanetSurfaceDailySnapshot(long planetEntityId, List<SurfaceRegionDailySnapshot> surfaceRegions,
                List<CityDailySnapshot> cities) {
            this(planetEntityId, surfaceRegions, cities, List.of(), List.of(), List.of(), List.of(), null);
        }

        /**
         * 完整构造器（G2.7）：额外承载行星工业只读快照。
         *
         * 空值约定：
         * - 列表参数为 null 或空时统一存为稳定空集合（不可变），lastSettlementReport 为 null 表示无结算结果。
         *
         * @param planetEntityId       行星实体 ID
         * @param surfaceRegions       地表区域快照列表
         * @param cities               城市/殖民地快照列表
         * @param inventories          该行星本地库存快照列表
         * @param extractionFacilities 该行星采集设施快照列表
         * @param processingFacilities 该行星加工设施快照列表
         * @param inTransitTransfers   与该行星库存相关的在途运输快照列表
         * @param lastSettlementReport 该行星最近结算结果（可为 null）
         */
        public PlanetSurfaceDailySnapshot(long planetEntityId, List<SurfaceRegionDailySnapshot> surfaceRegions,
                List<CityDailySnapshot> cities, List<InventoryDailySnapshot> inventories,
                List<ExtractionFacilityDailySnapshot> extractionFacilities,
                List<ProcessingFacilityDailySnapshot> processingFacilities,
                List<TransferDailySnapshot> inTransitTransfers,
                SettlementReportDailySnapshot lastSettlementReport) {
            this.planetEntityId = planetEntityId;
            this.surfaceRegions = deepCopyList(surfaceRegions);
            this.cities = deepCopyList(cities);
            this.inventories = deepCopyList(inventories);
            this.extractionFacilities = deepCopyList(extractionFacilities);
            this.processingFacilities = deepCopyList(processingFacilities);
            this.inTransitTransfers = deepCopyList(inTransitTransfers);
            this.lastSettlementReport = lastSettlementReport;
        }
    }

    /**
     * CityDailySnapshot（城市日快照）喵。
     *
     * 承载城市/殖民地的最小可读状态：ID、名称、阶段、规模、人口与首都标记喵。
     */
    public static class CityDailySnapshot {
        public final long cityId;
        public final String name;
        public final String cityStage;
        public final int cityScale;
        public final long population;
        public final boolean isPlanetaryCapital;

        public CityDailySnapshot(long cityId, String name, String cityStage, int cityScale, long population,
                boolean isPlanetaryCapital) {
            this.cityId = cityId;
            this.name = name;
            this.cityStage = cityStage;
            this.cityScale = cityScale;
            this.population = population;
            this.isPlanetaryCapital = isPlanetaryCapital;
        }
    }

    /**
     * SurfaceRegionDailySnapshot（地表区域日快照）喵。
     */
    public static class SurfaceRegionDailySnapshot {
        public final long regionId;
        public final String regionType;
        public final String name;
        public final double surfacePercentage;
        public final double developableSpaceRatio;

        public SurfaceRegionDailySnapshot(long regionId, String regionType, String name, double surfacePercentage,
                double developableSpaceRatio) {
            this.regionId = regionId;
            this.regionType = regionType;
            this.name = name;
            this.surfacePercentage = surfacePercentage;
            this.developableSpaceRatio = developableSpaceRatio;
        }
    }

    /**
     * InventoryDailySnapshot（本地库存只读快照）喵。
     *
     * 承载 LocalInventory 的最小可读状态：inventoryId / ownerEntityId / capacity / usedCapacity /
     * substances（持有量） / reservedAmounts（预留量）。
     * substances 与 reservedAmounts 为深拷贝（substanceId -> 数量），外部修改不影响 game 权威库存。
     */
    public static class InventoryDailySnapshot {
        public final long inventoryId;
        public final long ownerEntityId;
        public final double capacity;
        public final double usedCapacity;

        /** 物质持有量表（substanceId -> 数量，深拷贝，保持 game 迭代顺序）。 */
        public final Map<String, Double> substances;

        /** 物质预留量表（substanceId -> 数量，深拷贝，保持 game 迭代顺序）。 */
        public final Map<String, Double> reservedAmounts;

        public InventoryDailySnapshot(long inventoryId, long ownerEntityId, double capacity, double usedCapacity,
                Map<String, Double> substances, Map<String, Double> reservedAmounts) {
            this.inventoryId = inventoryId;
            this.ownerEntityId = ownerEntityId;
            this.capacity = capacity;
            this.usedCapacity = usedCapacity;
            this.substances = deepCopyAmounts(substances);
            this.reservedAmounts = deepCopyAmounts(reservedAmounts);
        }
    }

    /**
     * FacilityDailySnapshot（设施统一只读快照基类）喵。
     *
     * 采集设施与加工设施共有的只读字段，供 UI 按统一口径读取设施状态：
     * facilityId / facilityType / inventoryId / locationEntityId / status / lastFailureReason。
     */
    public static class FacilityDailySnapshot {
        public final long facilityId;
        public final String facilityType;
        public final long inventoryId;
        public final long locationEntityId;
        public final String status;
        public final String lastFailureReason;

        public FacilityDailySnapshot(long facilityId, String facilityType, long inventoryId,
                long locationEntityId, String status, String lastFailureReason) {
            this.facilityId = facilityId;
            this.facilityType = facilityType;
            this.inventoryId = inventoryId;
            this.locationEntityId = locationEntityId;
            this.status = status;
            this.lastFailureReason = lastFailureReason;
        }
    }

    /**
     * ExtractionFacilityDailySnapshot（采集设施只读快照）喵。
     *
     * 在统一设施字段基础上增加 resourceId（采集物质）与 amountPerDay（每日固定产出量）。
     */
    public static class ExtractionFacilityDailySnapshot extends FacilityDailySnapshot {
        public final String resourceId;
        public final double amountPerDay;

        public ExtractionFacilityDailySnapshot(long facilityId, String facilityType, long inventoryId,
                long locationEntityId, String resourceId, double amountPerDay, String status,
                String lastFailureReason) {
            super(facilityId, facilityType, inventoryId, locationEntityId, status, lastFailureReason);
            this.resourceId = resourceId;
            this.amountPerDay = amountPerDay;
        }
    }

    /**
     * ProcessingFacilityDailySnapshot（加工设施只读快照）喵。
     *
     * 在统一设施字段基础上增加 activeRecipeId（激活配方）、progressDays（当前进度日）与
     * progressRecipeId（进度归属配方）。
     */
    public static class ProcessingFacilityDailySnapshot extends FacilityDailySnapshot {
        public final String activeRecipeId;
        public final double progressDays;
        public final String progressRecipeId;

        public ProcessingFacilityDailySnapshot(long facilityId, String facilityType, long inventoryId,
                long locationEntityId, String activeRecipeId, double progressDays, String progressRecipeId,
                String status, String lastFailureReason) {
            super(facilityId, facilityType, inventoryId, locationEntityId, status, lastFailureReason);
            this.activeRecipeId = activeRecipeId;
            this.progressDays = progressDays;
            this.progressRecipeId = progressRecipeId;
        }
    }

    /**
     * TransferDailySnapshot（在途运输只读快照）喵。
     *
     * 承载 CargoTransfer 的最小可读状态：transferId / sourceInventoryId / targetInventoryId /
     * goods / status / departedAtTick / arrivedAtTick。
     * goods 为深拷贝（substanceId -> 数量），外部修改不影响 game 运输记录。
     */
    public static class TransferDailySnapshot {
        public final long transferId;
        public final long sourceInventoryId;
        public final long targetInventoryId;

        /** 运输货物表（substanceId -> 数量，深拷贝）。 */
        public final Map<String, Double> goods;

        public final String status;
        public final long departedAtTick;
        public final long arrivedAtTick;

        public TransferDailySnapshot(long transferId, long sourceInventoryId, long targetInventoryId,
                Map<String, Double> goods, String status, long departedAtTick, long arrivedAtTick) {
            this.transferId = transferId;
            this.sourceInventoryId = sourceInventoryId;
            this.targetInventoryId = targetInventoryId;
            this.goods = deepCopyAmounts(goods);
            this.status = status;
            this.departedAtTick = departedAtTick;
            this.arrivedAtTick = arrivedAtTick;
        }
    }

    /**
     * SettlementReportDailySnapshot（日结算报告只读快照）喵。
     *
     * 承载最近一次与该行星相关的日结算结果（SettlementReport 的深拷贝）：
     * tick / extractions / facilities / transfers，全部为不可变只读数据。
     */
    public static class SettlementReportDailySnapshot {
        public final long tick;
        public final List<ExtractionResultDailySnapshot> extractions;
        public final List<FacilityResultDailySnapshot> facilities;
        public final List<TransferResultDailySnapshot> transfers;

        public SettlementReportDailySnapshot(long tick, List<ExtractionResultDailySnapshot> extractions,
                List<FacilityResultDailySnapshot> facilities, List<TransferResultDailySnapshot> transfers) {
            this.tick = tick;
            this.extractions = deepCopyList(extractions);
            this.facilities = deepCopyList(facilities);
            this.transfers = deepCopyList(transfers);
        }
    }

    /**
     * ExtractionResultDailySnapshot（采集结算结果只读快照）喵。
     */
    public static class ExtractionResultDailySnapshot {
        public final long facilityId;
        public final String facilityType;
        public final String resourceId;
        public final boolean success;
        public final String failureReason;

        /** 当日累计产出（substanceId -> 数量，深拷贝）。 */
        public final Map<String, Double> extracted;

        public ExtractionResultDailySnapshot(long facilityId, String facilityType, String resourceId,
                boolean success, String failureReason, Map<String, Double> extracted) {
            this.facilityId = facilityId;
            this.facilityType = facilityType;
            this.resourceId = resourceId;
            this.success = success;
            this.failureReason = failureReason;
            this.extracted = deepCopyAmounts(extracted);
        }
    }

    /**
     * FacilityResultDailySnapshot（加工结算结果只读快照）喵。
     */
    public static class FacilityResultDailySnapshot {
        public final long facilityId;
        public final String facilityType;
        public final String recipeId;
        public final boolean success;
        public final String failureReason;
        public final int batchCount;

        /** 当日累计产出（substanceId -> 数量，深拷贝）。 */
        public final Map<String, Double> produced;

        /** 当日累计消耗（substanceId -> 数量，深拷贝）。 */
        public final Map<String, Double> consumed;

        public FacilityResultDailySnapshot(long facilityId, String facilityType, String recipeId,
                boolean success, String failureReason, int batchCount, Map<String, Double> produced,
                Map<String, Double> consumed) {
            this.facilityId = facilityId;
            this.facilityType = facilityType;
            this.recipeId = recipeId;
            this.success = success;
            this.failureReason = failureReason;
            this.batchCount = batchCount;
            this.produced = deepCopyAmounts(produced);
            this.consumed = deepCopyAmounts(consumed);
        }
    }

    /**
     * TransferResultDailySnapshot（运输结算结果只读快照）喵。
     */
    public static class TransferResultDailySnapshot {
        public final long transferId;

        /** 结果类型：ARRIVED（已抵达）/ IN_TRANSIT（目标库存已满，保持运输中）。 */
        public final String resultType;

        /** 运输货物（substanceId -> 数量，深拷贝）。 */
        public final Map<String, Double> goods;

        public TransferResultDailySnapshot(long transferId, String resultType, Map<String, Double> goods) {
            this.transferId = transferId;
            this.resultType = resultType;
            this.goods = deepCopyAmounts(goods);
        }
    }

    /**
     * 深拷贝物质数量表（substanceId -> 数量）。
     *
     * 保留源表迭代顺序（与 game LinkedHashMap 顺序一致，保证输出确定性），
     * 并包装为不可变视图，外部无法通过快照修改 game 权威状态。
     *
     * @param source 源物质表（可为 null）
     * @return 独立不可变拷贝；null 或空时返回稳定空集合
     */
    private static Map<String, Double> deepCopyAmounts(Map<String, Double> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    /**
     * 深拷贝只读快照列表。
     *
     * 元素本身已是不可变只读快照，仅需拷贝容器并包装为不可变视图，
     * 防止外部对列表进行结构性修改（add/remove）。
     *
     * @param source 源列表（可为 null）
     * @return 独立不可变列表；null 或空时返回稳定空集合
     */
    private static <T> List<T> deepCopyList(List<T> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
