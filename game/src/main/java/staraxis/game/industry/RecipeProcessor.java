package staraxis.game.industry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RecipeProcessor（配方生产处理器）
 *
 * 执行单个配方的一次生产尝试（纯逻辑，不持有状态）。
 * 从所属本地库存校验并扣除输入与能源，校验输出容量，写回产物与副产物。
 *
 * 失败原因口径（lastFailureReason / SettlementReport 引用）：
 * - INPUT_INSUFFICIENT：输入物不足。
 * - ENERGY_INSUFFICIENT：能源不足。
 * - OUTPUT_CAPACITY_INSUFFICIENT：输出容量不足。
 */
public final class RecipeProcessor {

    /** 配方能源物质：energyCost 从库存中扣减的能源物质 ID。 */
    public static final String ENERGY_SUBSTANCE = SubstanceId.ENERGY;

    /** 失败原因：输入物不足。 */
    public static final String FAILURE_REASON_INPUT_INSUFFICIENT = "INPUT_INSUFFICIENT";

    /** 失败原因：能源不足。 */
    public static final String FAILURE_REASON_ENERGY_INSUFFICIENT = "ENERGY_INSUFFICIENT";

    /** 失败原因：输出容量不足。 */
    public static final String FAILURE_REASON_OUTPUT_CAPACITY_INSUFFICIENT = "OUTPUT_CAPACITY_INSUFFICIENT";

    private RecipeProcessor() {
    }

    /**
     * 尝试执行一次配方生产。
     *
     * 校验顺序（任一不满足即失败，且不修改库存）：
     * 1. 能源：库存 ENERGY 可支配量 >= energyCost。
     * 2. 输入：每个 inputs 的可支配量 >= 要求数量。
     * 3. 输出容量：现有占用 + 全部产出/副产物总量 <= 容量上限。
     *
     * 全部满足后：扣除能源与输入，写入产物与副产物，并返回产出/消耗明细。
     *
     * @param recipe    配方定义
     * @param inventory 所属本地库存（设施只读写该库存）
     * @param tick      当前模拟 tick
     * @return 生产结果（success 或 failureReason 之一）
     */
    public static ProductionResult tryProduceOnce(RecipeDef recipe, LocalInventory inventory, long tick) {
        if (recipe == null || inventory == null) {
            return ProductionResult.failure(FAILURE_REASON_INPUT_INSUFFICIENT);
        }

        // 1. 能源校验
        if (recipe.energyCost > 0.0
                && inventory.getAvailableAmount(ENERGY_SUBSTANCE) < recipe.energyCost - LocalInventory.EPSILON) {
            return ProductionResult.failure(FAILURE_REASON_ENERGY_INSUFFICIENT);
        }

        // 2. 输入校验
        if (recipe.inputs != null) {
            for (RecipeItem input : recipe.inputs) {
                if (input == null || input.substanceId == null || input.substanceId.isBlank()) {
                    continue;
                }
                if (inventory.getAvailableAmount(input.substanceId) < input.amount - LocalInventory.EPSILON) {
                    return ProductionResult.failure(FAILURE_REASON_INPUT_INSUFFICIENT);
                }
            }
        }

        // 3. 输出容量校验（产出 + 副产物总量）
        double added = 0.0;
        if (recipe.outputs != null) {
            for (RecipeItem output : recipe.outputs) {
                if (output != null && output.substanceId != null && !output.substanceId.isBlank()) {
                    added += output.amount;
                }
            }
        }
        if (recipe.byproducts != null) {
            for (RecipeItem byproduct : recipe.byproducts) {
                if (byproduct != null && byproduct.substanceId != null && !byproduct.substanceId.isBlank()) {
                    added += byproduct.amount;
                }
            }
        }
        if (inventory.getUsedCapacity() + added > inventory.capacity + LocalInventory.EPSILON) {
            return ProductionResult.failure(FAILURE_REASON_OUTPUT_CAPACITY_INSUFFICIENT);
        }

        // 4. 执行：扣能源、扣输入、写产物与副产物
        Map<String, Double> consumed = new LinkedHashMap<>();
        Map<String, Double> produced = new LinkedHashMap<>();

        if (recipe.energyCost > 0.0) {
            inventory.withdraw(ENERGY_SUBSTANCE, recipe.energyCost, tick);
            consumed.put(ENERGY_SUBSTANCE, recipe.energyCost);
        }
        if (recipe.inputs != null) {
            for (RecipeItem input : recipe.inputs) {
                if (input == null || input.substanceId == null || input.substanceId.isBlank()) {
                    continue;
                }
                inventory.withdraw(input.substanceId, input.amount, tick);
                consumed.put(input.substanceId, input.amount);
            }
        }
        if (recipe.outputs != null) {
            for (RecipeItem output : recipe.outputs) {
                if (output == null || output.substanceId == null || output.substanceId.isBlank()) {
                    continue;
                }
                inventory.deposit(output.substanceId, output.amount, tick);
                produced.put(output.substanceId, output.amount);
            }
        }
        if (recipe.byproducts != null) {
            for (RecipeItem byproduct : recipe.byproducts) {
                if (byproduct == null || byproduct.substanceId == null || byproduct.substanceId.isBlank()) {
                    continue;
                }
                inventory.deposit(byproduct.substanceId, byproduct.amount, tick);
                produced.put(byproduct.substanceId, byproduct.amount);
            }
        }

        return ProductionResult.success(produced, consumed);
    }
}
