package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import staraxis.game.state.snapshot.EntitySnapshot;

import java.util.List;
import java.util.Map;

/**
 * DailySettlementStateDto
 *
 * 对应 game 侧的 DailySettlementState，用于向前端传输低频/基线数据喵。
 * G2.7 起行星地表快照额外承载城市与工业只读快照（库存 / 采集与加工设施 / 在途运输 / 最近结算结果），
 * 与桌面本地读取的 DailySettlementState.PlanetSurfaceDailySnapshot 字段口径保持一致喵。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailySettlementStateDto {

    /** 历史兼容字段：日序号喵。 */
    public final int settledDay;

    /** 基线快照生成时的游戏总秒数喵。 */
    public final long settledAtGameSeconds;

    public final int sectorCount;

    /** 行星地表（低频/静态）快照：Key 为 planetEntityId 喵。 */
    public final Map<Long, PlanetSurfaceSnapshotDto> planetSurfaces;

    /** 国家资产基线快照：nationId -> (entityTypeName -> entityId 列表) 喵。 */
    public final Map<String, Map<String, List<Long>>> nationAssetsByNationId;

    /** 公开实体基线快照（按星区聚合）：sectorKey -> 公开实体快照列表 喵。 */
    public final Map<String, List<EntitySnapshot>> publicEntityBaselinesBySectorKey;

    public DailySettlementStateDto(int settledDay, long settledAtGameSeconds, int sectorCount,
            Map<Long, PlanetSurfaceSnapshotDto> planetSurfaces,
            Map<String, Map<String, List<Long>>> nationAssetsByNationId,
            Map<String, List<EntitySnapshot>> publicEntityBaselinesBySectorKey) {
        this.settledDay = settledDay;
        this.settledAtGameSeconds = settledAtGameSeconds;
        this.sectorCount = sectorCount;
        this.planetSurfaces = planetSurfaces;
        this.nationAssetsByNationId = nationAssetsByNationId;
        this.publicEntityBaselinesBySectorKey = publicEntityBaselinesBySectorKey;
    }

    /**
     * 行星地表快照 DTO 喵。
     *
     * G2.7 起承载与桌面本地一致的完整行星可读状态：
     * surfaceRegions / cities / inventories / extractionFacilities / processingFacilities /
     * inTransitTransfers / lastSettlementReport。
     *
     * 空值约定与 game 侧一致：
     * - 无库存/设施/运输时列表为稳定空集合，禁止 null。
     * - 从未结算过时 {@link #lastSettlementReport} 为 null（NON_NULL 下不输出该字段）。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlanetSurfaceSnapshotDto {
        public final long planetEntityId;
        public final List<SurfaceRegionSnapshotDto> surfaceRegions;

        /** 城市/殖民地快照列表（无殖民地时为空集合）喵。 */
        public final List<CitySnapshotDto> cities;

        /** 本地库存只读快照列表（无库存时为空集合）喵。 */
        public final List<InventorySnapshotDto> inventories;

        /** 采集设施只读快照列表（无设施时为空集合）喵。 */
        public final List<ExtractionFacilitySnapshotDto> extractionFacilities;

        /** 加工设施只读快照列表（无设施时为空集合）喵。 */
        public final List<ProcessingFacilitySnapshotDto> processingFacilities;

        /** 在途运输只读快照列表（无运输时为空集合）喵。 */
        public final List<TransferSnapshotDto> inTransitTransfers;

        /** 最近一次日结算结果只读快照（从未结算为 null）喵。 */
        public final SettlementReportSnapshotDto lastSettlementReport;

        /** 历史兼容构造器（G2.7 之前签名）：城市与工业字段为空集合 + null 报告喵。 */
        public PlanetSurfaceSnapshotDto(long planetEntityId, List<SurfaceRegionSnapshotDto> surfaceRegions) {
            this(planetEntityId, surfaceRegions, List.of(), List.of(), List.of(), List.of(), List.of(), null);
        }

        /**
         * 完整构造器（G2.7）：承载城市与行星工业只读快照喵。
         *
         * @param planetEntityId       行星实体 ID
         * @param surfaceRegions       地表区域快照列表
         * @param cities               城市/殖民地快照列表
         * @param inventories          本地库存快照列表
         * @param extractionFacilities 采集设施快照列表
         * @param processingFacilities 加工设施快照列表
         * @param inTransitTransfers   在途运输快照列表
         * @param lastSettlementReport 最近结算结果（可为 null）
         */
        public PlanetSurfaceSnapshotDto(long planetEntityId, List<SurfaceRegionSnapshotDto> surfaceRegions,
                List<CitySnapshotDto> cities, List<InventorySnapshotDto> inventories,
                List<ExtractionFacilitySnapshotDto> extractionFacilities,
                List<ProcessingFacilitySnapshotDto> processingFacilities,
                List<TransferSnapshotDto> inTransitTransfers,
                SettlementReportSnapshotDto lastSettlementReport) {
            this.planetEntityId = planetEntityId;
            this.surfaceRegions = surfaceRegions;
            this.cities = cities;
            this.inventories = inventories;
            this.extractionFacilities = extractionFacilities;
            this.processingFacilities = processingFacilities;
            this.inTransitTransfers = inTransitTransfers;
            this.lastSettlementReport = lastSettlementReport;
        }
    }

    /**
     * 地表区域快照 DTO 喵。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SurfaceRegionSnapshotDto {
        public final long regionId;
        public final String regionType;
        public final String name;
        public final double surfacePercentage;
        public final double developableSpaceRatio;

        public SurfaceRegionSnapshotDto(long regionId, String regionType, String name, double surfacePercentage,
                double developableSpaceRatio) {
            this.regionId = regionId;
            this.regionType = regionType;
            this.name = name;
            this.surfacePercentage = surfacePercentage;
            this.developableSpaceRatio = developableSpaceRatio;
        }
    }

    /**
     * 城市/殖民地日快照 DTO 喵。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CitySnapshotDto {
        public final long cityId;
        public final String name;
        public final String cityStage;
        public final int cityScale;
        public final long population;
        public final boolean isPlanetaryCapital;

        public CitySnapshotDto(long cityId, String name, String cityStage, int cityScale, long population,
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
     * 本地库存只读快照 DTO 喵。
     *
     * substances / reservedAmounts 为 (substanceId -> 数量) 表，保持 game 迭代顺序喵。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InventorySnapshotDto {
        public final long inventoryId;
        public final long ownerEntityId;
        public final double capacity;
        public final double usedCapacity;

        /** 物质持有量表（substanceId -> 数量）喵。 */
        public final Map<String, Double> substances;

        /** 物质预留量表（substanceId -> 数量）喵。 */
        public final Map<String, Double> reservedAmounts;

        public InventorySnapshotDto(long inventoryId, long ownerEntityId, double capacity, double usedCapacity,
                Map<String, Double> substances, Map<String, Double> reservedAmounts) {
            this.inventoryId = inventoryId;
            this.ownerEntityId = ownerEntityId;
            this.capacity = capacity;
            this.usedCapacity = usedCapacity;
            this.substances = substances;
            this.reservedAmounts = reservedAmounts;
        }
    }

    /**
     * 采集设施只读快照 DTO 喵。
     *
     * 在统一设施字段基础上增加 resourceId（采集物质）与 amountPerDay（每日固定产出量）喵。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExtractionFacilitySnapshotDto {
        public final long facilityId;
        public final String facilityType;
        public final long inventoryId;
        public final long locationEntityId;
        public final String resourceId;
        public final double amountPerDay;
        public final String status;
        public final String lastFailureReason;

        public ExtractionFacilitySnapshotDto(long facilityId, String facilityType, long inventoryId,
                long locationEntityId, String resourceId, double amountPerDay, String status,
                String lastFailureReason) {
            this.facilityId = facilityId;
            this.facilityType = facilityType;
            this.inventoryId = inventoryId;
            this.locationEntityId = locationEntityId;
            this.resourceId = resourceId;
            this.amountPerDay = amountPerDay;
            this.status = status;
            this.lastFailureReason = lastFailureReason;
        }
    }

    /**
     * 加工设施只读快照 DTO 喵。
     *
     * 在统一设施字段基础上增加 activeRecipeId / progressDays / progressRecipeId 喵。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProcessingFacilitySnapshotDto {
        public final long facilityId;
        public final String facilityType;
        public final long inventoryId;
        public final long locationEntityId;
        public final String activeRecipeId;
        public final double progressDays;
        public final String progressRecipeId;
        public final String status;
        public final String lastFailureReason;

        public ProcessingFacilitySnapshotDto(long facilityId, String facilityType, long inventoryId,
                long locationEntityId, String activeRecipeId, double progressDays, String progressRecipeId,
                String status, String lastFailureReason) {
            this.facilityId = facilityId;
            this.facilityType = facilityType;
            this.inventoryId = inventoryId;
            this.locationEntityId = locationEntityId;
            this.activeRecipeId = activeRecipeId;
            this.progressDays = progressDays;
            this.progressRecipeId = progressRecipeId;
            this.status = status;
            this.lastFailureReason = lastFailureReason;
        }
    }

    /**
     * 在途运输只读快照 DTO 喵。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransferSnapshotDto {
        public final long transferId;
        public final long sourceInventoryId;
        public final long targetInventoryId;

        /** 运输货物表（substanceId -> 数量）喵。 */
        public final Map<String, Double> goods;

        public final String status;
        public final long departedAtTick;
        public final long arrivedAtTick;

        public TransferSnapshotDto(long transferId, long sourceInventoryId, long targetInventoryId,
                Map<String, Double> goods, String status, long departedAtTick, long arrivedAtTick) {
            this.transferId = transferId;
            this.sourceInventoryId = sourceInventoryId;
            this.targetInventoryId = targetInventoryId;
            this.goods = goods;
            this.status = status;
            this.departedAtTick = departedAtTick;
            this.arrivedAtTick = arrivedAtTick;
        }
    }

    /**
     * 日结算报告只读快照 DTO 喵。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SettlementReportSnapshotDto {
        public final long tick;
        public final List<ExtractionResultSnapshotDto> extractions;
        public final List<FacilityResultSnapshotDto> facilities;
        public final List<TransferResultSnapshotDto> transfers;

        public SettlementReportSnapshotDto(long tick, List<ExtractionResultSnapshotDto> extractions,
                List<FacilityResultSnapshotDto> facilities, List<TransferResultSnapshotDto> transfers) {
            this.tick = tick;
            this.extractions = extractions;
            this.facilities = facilities;
            this.transfers = transfers;
        }
    }

    /**
     * 采集结算结果只读快照 DTO 喵。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExtractionResultSnapshotDto {
        public final long facilityId;
        public final String facilityType;
        public final String resourceId;
        public final boolean success;
        public final String failureReason;

        /** 当日累计产出（substanceId -> 数量）喵。 */
        public final Map<String, Double> extracted;

        public ExtractionResultSnapshotDto(long facilityId, String facilityType, String resourceId,
                boolean success, String failureReason, Map<String, Double> extracted) {
            this.facilityId = facilityId;
            this.facilityType = facilityType;
            this.resourceId = resourceId;
            this.success = success;
            this.failureReason = failureReason;
            this.extracted = extracted;
        }
    }

    /**
     * 加工结算结果只读快照 DTO 喵。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FacilityResultSnapshotDto {
        public final long facilityId;
        public final String facilityType;
        public final String recipeId;
        public final boolean success;
        public final String failureReason;
        public final int batchCount;

        /** 当日累计产出（substanceId -> 数量）喵。 */
        public final Map<String, Double> produced;

        /** 当日累计消耗（substanceId -> 数量）喵。 */
        public final Map<String, Double> consumed;

        public FacilityResultSnapshotDto(long facilityId, String facilityType, String recipeId,
                boolean success, String failureReason, int batchCount, Map<String, Double> produced,
                Map<String, Double> consumed) {
            this.facilityId = facilityId;
            this.facilityType = facilityType;
            this.recipeId = recipeId;
            this.success = success;
            this.failureReason = failureReason;
            this.batchCount = batchCount;
            this.produced = produced;
            this.consumed = consumed;
        }
    }

    /**
     * 运输结算结果只读快照 DTO 喵。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransferResultSnapshotDto {
        public final long transferId;

        /** 结果类型：ARRIVED（已抵达）/ IN_TRANSIT（目标库存已满，保持运输中）喵。 */
        public final String resultType;

        /** 运输货物表（substanceId -> 数量）喵。 */
        public final Map<String, Double> goods;

        public TransferResultSnapshotDto(long transferId, String resultType, Map<String, Double> goods) {
            this.transferId = transferId;
            this.resultType = resultType;
            this.goods = goods;
        }
    }
}
