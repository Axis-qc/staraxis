package staraxis.game.industry;

/**
 * ProcessingFacility（加工设施）
 *
 * 从所属本地库存读取输入并写回产物/副产物的设施运行时状态。
 * 设施只读写所属库存（inventoryId），不与其他设施直接互连。
 *
 * 生产推进：由 ProductionSettlementService 在每日结算时按 recipe.processTime 累加进度，
 * 达到完成条件后通过 RecipeProcessor 尝试执行一批生产。
 */
public class ProcessingFacility {

    /** 设施状态：闲置（无配方或未开始加工）。 */
    public static final String STATUS_IDLE = "IDLE";

    /** 设施状态：加工中（进度未满，或等待配方完成）。 */
    public static final String STATUS_PROCESSING = "PROCESSING";

    /** 设施状态：阻塞（缺少配方/库存/能源/输入或输出容量不足）。 */
    public static final String STATUS_BLOCKED = "BLOCKED";

    /** 设施 ID（全局唯一，由 IndustryRegistry 分配）。 */
    public long facilityId;

    /** 设施类型（如 ELECTROLYZER 电解槽）。 */
    public String facilityType;

    /** 所属本地库存 ID（设施只读写该库存）。 */
    public long inventoryId;

    /** 所在实体 ID（行星/空间站/深空资源站的实体 ID，locationEntityId）。 */
    public long locationEntityId;

    /** 当前激活的配方 ID。 */
    public String activeRecipeId;

    /** 当前加工进度（游戏日），达到 recipe.processTime 时完成一批。 */
    public double progressDays;

    /** 当前进度所属的配方 ID（progressDays 归属，换配方时据此重置进度，避免跨配方继承）。 */
    public String progressRecipeId;

    /** 当前状态（STATUS_IDLE / STATUS_PROCESSING / STATUS_BLOCKED）。 */
    public String status = STATUS_IDLE;

    /** 最近一次失败原因（无失败时为 null，供 UI 反馈）。 */
    public String lastFailureReason;

    /**
     * 默认构造（Jackson 反序列化用）。
     */
    public ProcessingFacility() {
    }

    /**
     * 构造加工设施。
     *
     * @param facilityId      设施 ID
     * @param facilityType    设施类型
     * @param inventoryId     所属本地库存 ID
     * @param locationEntityId 所在实体 ID
     * @param activeRecipeId  激活配方 ID
     */
    public ProcessingFacility(long facilityId, String facilityType, long inventoryId,
            long locationEntityId, String activeRecipeId) {
        this.facilityId = facilityId;
        this.facilityType = facilityType;
        this.inventoryId = inventoryId;
        this.locationEntityId = locationEntityId;
        this.activeRecipeId = activeRecipeId;
        this.progressRecipeId = activeRecipeId;
    }

    /**
     * 切换设施当前激活的配方。
     *
     * 切换配方时丢弃旧配方的加工进度并清理运行状态，避免进度按新配方的
     * processTime 错误继承（进度属于旧配方，不能带过来）。
     *
     * @param newRecipeId 新配方 ID（null 表示解除配方，状态回到闲置）
     */
    public void switchRecipe(String newRecipeId) {
        boolean changed = (newRecipeId == null)
                ? activeRecipeId != null
                : !newRecipeId.equals(activeRecipeId);
        this.activeRecipeId = newRecipeId;
        if (changed) {
            this.progressDays = 0.0;
            this.progressRecipeId = newRecipeId;
            this.status = STATUS_IDLE;
            this.lastFailureReason = null;
        }
    }
}
