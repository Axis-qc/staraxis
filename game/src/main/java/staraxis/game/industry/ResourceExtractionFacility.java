package staraxis.game.industry;

/**
 * ResourceExtractionFacility（资源采集设施）
 *
 * 从资源点向所属本地库存写入固定资源产出的设施运行时状态（G2.2 最小实现）。
 * 设施只读写所属库存（inventoryId），不与其他设施直接互连；
 * 产出固定（resourceId + amountPerDay），第一版支持水采集（WATER_EXTRACTOR）。
 *
 * 生产推进：由 ProductionSettlementService 在每日结算时按 amountPerDay 向
 * 所属库存写入固定产出，库存容量不足时进入 BLOCKED 并记录失败原因。
 */
public class ResourceExtractionFacility {

    /** 设施类型：水采集设施（第一版唯一实现）。 */
    public static final String TYPE_WATER_EXTRACTION = "WATER_EXTRACTOR";

    /** 设施状态：正常运行（每日向库存写入固定产出）。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 设施状态：阻塞（库存缺失 / 采集量非法 / 库存容量不足）。 */
    public static final String STATUS_BLOCKED = "BLOCKED";

    /** 设施 ID（全局唯一，与加工设施共享 IndustryRegistry 的设施 ID 命名空间）。 */
    public long facilityId;

    /** 设施类型（如 WATER_EXTRACTOR 水采集设施）。 */
    public String facilityType;

    /** 所属本地库存 ID（设施只向该库存写入产出）。 */
    public long inventoryId;

    /** 所在实体 ID（行星/空间站/深空资源站的实体 ID，locationEntityId）。 */
    public long locationEntityId;

    /** 采集的资源物质 ID（substanceId，见 {@link SubstanceId}）。 */
    public String resourceId;

    /** 每日固定产出量（单位/日），结算时向所属库存写入。 */
    public double amountPerDay;

    /** 当前状态（STATUS_ACTIVE / STATUS_BLOCKED）。 */
    public String status = STATUS_ACTIVE;

    /** 最近一次失败原因（无失败时为 null，供 UI 反馈）。 */
    public String lastFailureReason;

    /**
     * 默认构造（Jackson 反序列化用）。
     */
    public ResourceExtractionFacility() {
    }

    /**
     * 构造资源采集设施。
     *
     * @param facilityId       设施 ID
     * @param facilityType     设施类型
     * @param inventoryId      所属本地库存 ID
     * @param locationEntityId 所在实体 ID
     * @param resourceId       采集的资源物质 ID
     * @param amountPerDay     每日固定产出量
     */
    public ResourceExtractionFacility(long facilityId, String facilityType, long inventoryId,
            long locationEntityId, String resourceId, double amountPerDay) {
        this.facilityId = facilityId;
        this.facilityType = facilityType;
        this.inventoryId = inventoryId;
        this.locationEntityId = locationEntityId;
        this.resourceId = resourceId;
        this.amountPerDay = amountPerDay;
    }
}
