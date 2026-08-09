package staraxis.game.industry;

/**
 * InventoryChange（库存变更记录）
 *
 * 每次对 LocalInventory 的存取/预留操作落一条不可变变更记录，
 * 用于资源可见性反馈（G2.8）与测试断言。
 */
public class InventoryChange {

    /** 变更方向：存入。 */
    public static final String TYPE_DEPOSIT = "DEPOSIT";

    /** 变更方向：取出。 */
    public static final String TYPE_WITHDRAW = "WITHDRAW";

    /** 变更方向：预留。 */
    public static final String TYPE_RESERVE = "RESERVE";

    /** 变更方向：释放预留。 */
    public static final String TYPE_RELEASE_RESERVE = "RELEASE_RESERVE";

    /** 变更方向标识（TYPE_DEPOSIT / TYPE_WITHDRAW / TYPE_RESERVE / TYPE_RELEASE_RESERVE）。 */
    public final String type;

    /** 涉及的物质 ID（substanceId，见 {@link SubstanceId}）。 */
    public final String substanceId;

    /** 变更数量（正数，单位）。 */
    public final double amount;

    /** 变更发生时的模拟 tick。 */
    public final long tick;

    /** 变更后该物质的库存余额（便于 UI 直接展示）。 */
    public final double resultBalance;

    /**
     * 构造一条库存变更记录。
     *
     * @param type          变更方向标识
     * @param substanceId   物质 ID
     * @param amount        变更数量（正数）
     * @param tick          变更时的模拟 tick
     * @param resultBalance 变更后库存余额
     */
    public InventoryChange(String type, String substanceId, double amount, long tick, double resultBalance) {
        this.type = type;
        this.substanceId = substanceId;
        this.amount = amount;
        this.tick = tick;
        this.resultBalance = resultBalance;
    }
}
