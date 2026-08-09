package staraxis.game.industry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CargoTransfer（货物运输记录）
 *
 * 运输船在源库存与目标库存之间的货物转移记录（G2.6）。
 *
 * 生命周期：
 * - 出发（startTransfer）：从源 LocalInventory 扣除全部货物并创建运输中记录，状态 IN_TRANSIT。
 *   货物扣除后即不可被两端重复使用（既不在源库存，也未写入目标库存）。
 * - 抵达（completeTransfer）：将货物写入目标 LocalInventory，状态置为 ARRIVED，记录抵达 tick。
 *   同一运输记录不可重复结算（状态非 IN_TRANSIT 时返回失败）。
 */
public class CargoTransfer {

    /** 运输状态：运输中（货物已从源库存扣除，尚未写入目标库存）。 */
    public static final String STATUS_IN_TRANSIT = "IN_TRANSIT";

    /** 运输状态：已抵达（货物已写入目标库存）。 */
    public static final String STATUS_ARRIVED = "ARRIVED";

    /** 运输状态：已取消（预留扩展）。 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 运输记录 ID（全局唯一，由 IndustryRegistry 分配）。 */
    public long transferId;

    /** 源库存 ID（sourceInventoryId）。 */
    public long sourceInventoryId;

    /** 目标库存 ID（targetInventoryId）。 */
    public long targetInventoryId;

    /** 货物（substanceId -> 数量）。 */
    public final Map<String, Double> goods = new LinkedHashMap<>();

    /** 当前状态（STATUS_IN_TRANSIT / STATUS_ARRIVED / STATUS_CANCELLED）。 */
    public String status = STATUS_IN_TRANSIT;

    /** 出发时的模拟 tick。 */
    public long departedAtTick;

    /** 抵达时的模拟 tick（0 表示尚未抵达）。 */
    public long arrivedAtTick;

    /**
     * 默认构造（Jackson 反序列化用）。
     */
    public CargoTransfer() {
    }

    /**
     * 构造运输记录（初始状态 IN_TRANSIT）。
     *
     * @param transferId        运输记录 ID
     * @param sourceInventoryId 源库存 ID
     * @param targetInventoryId 目标库存 ID
     * @param goods             货物（substanceId -> 数量）
     * @param departedAtTick    出发时模拟 tick
     */
    public CargoTransfer(long transferId, long sourceInventoryId, long targetInventoryId,
            Map<String, Double> goods, long departedAtTick) {
        this.transferId = transferId;
        this.sourceInventoryId = sourceInventoryId;
        this.targetInventoryId = targetInventoryId;
        this.goods.putAll(goods);
        this.departedAtTick = departedAtTick;
    }

    /**
     * 判断货物是否为纯正数且非空。
     *
     * @return 货物表非空且所有数量为正时返回 true
     */
    public boolean hasValidGoods() {
        if (goods.isEmpty()) {
            return false;
        }
        for (double amount : goods.values()) {
            if (amount <= 0.0) {
                return false;
            }
        }
        return true;
    }
}
