package staraxis.game.industry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ProductionResult（单次生产结果）
 *
 * 记录一次配方生产尝试的结果：成功或失败原因、产出与消耗明细。
 * 由 RecipeProcessor 生成，供设施结算与 UI 反馈使用。
 */
public class ProductionResult {

    /** 是否生产成功。 */
    public final boolean success;

    /** 失败原因（成功时为 null，见 RecipeProcessor 常量）。 */
    public final String failureReason;

    /** 产出明细（产物 + 副产物，substanceId -> 数量），成功时非空。 */
    public final Map<String, Double> produced;

    /** 消耗明细（输入 + 能源，substanceId -> 数量），成功时非空。 */
    public final Map<String, Double> consumed;

    private ProductionResult(boolean success, String failureReason,
            Map<String, Double> produced, Map<String, Double> consumed) {
        this.success = success;
        this.failureReason = failureReason;
        this.produced = produced;
        this.consumed = consumed;
    }

    /**
     * 构造成功结果。
     *
     * @param produced 产出明细
     * @param consumed 消耗明细
     * @return 成功结果
     */
    public static ProductionResult success(Map<String, Double> produced, Map<String, Double> consumed) {
        return new ProductionResult(true, null, produced, consumed);
    }

    /**
     * 构造失败结果（产出/消耗为空）。
     *
     * @param failureReason 失败原因
     * @return 失败结果
     */
    public static ProductionResult failure(String failureReason) {
        return new ProductionResult(false, failureReason, new LinkedHashMap<>(), new LinkedHashMap<>());
    }
}
