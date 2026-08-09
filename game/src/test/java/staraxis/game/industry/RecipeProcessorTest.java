package staraxis.game.industry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RecipeProcessorTest（配方生产处理器单元测试）
 *
 * 覆盖 G2.5 水电解生产线：2H2O + 电力 -> 2H2 + O2，
 * 以及输入不足 / 能源不足 / 输出容量不足等失败分支。
 */
class RecipeProcessorTest {

    private RecipeDef electrolysis;
    private LocalInventory inventory;

    @BeforeEach
    void setUp() {
        // 使用内置默认配方（与 assets/industry/recipes.json 一致的水电解）
        electrolysis = RecipeRepository.defaultRecipes().get(0);
        assertNotNull(electrolysis);
        assertEquals(RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID, electrolysis.recipeId);

        inventory = new LocalInventory(1L, 100L, 1000.0);
    }

    @Test
    void waterElectrolysisProducesHydrogenAndOxygen() {
        inventory.deposit(SubstanceId.WATER, 10.0, 1);
        inventory.deposit(SubstanceId.ENERGY, 5.0, 1);

        ProductionResult result = RecipeProcessor.tryProduceOnce(electrolysis, inventory, 2);

        assertTrue(result.success);
        // 2H2O + 电力 -> 2H2 + O2
        assertEquals(2.0, inventory.getAmount(SubstanceId.HYDROGEN));
        assertEquals(1.0, inventory.getAmount(SubstanceId.OXYGEN));
        assertEquals(8.0, inventory.getAmount(SubstanceId.WATER));
        assertEquals(4.0, inventory.getAmount(SubstanceId.ENERGY));

        // 产出/消耗明细
        assertEquals(2.0, result.produced.get(SubstanceId.HYDROGEN));
        assertEquals(1.0, result.produced.get(SubstanceId.OXYGEN));
        assertEquals(2.0, result.consumed.get(SubstanceId.WATER));
        assertEquals(1.0, result.consumed.get(SubstanceId.ENERGY));
    }

    @Test
    void failsWhenWaterInputInsufficient() {
        inventory.deposit(SubstanceId.WATER, 1.0, 1);
        inventory.deposit(SubstanceId.ENERGY, 5.0, 1);

        ProductionResult result = RecipeProcessor.tryProduceOnce(electrolysis, inventory, 2);

        assertFalse(result.success);
        assertEquals(RecipeProcessor.FAILURE_REASON_INPUT_INSUFFICIENT, result.failureReason);
        // 失败时不修改库存
        assertEquals(1.0, inventory.getAmount(SubstanceId.WATER));
        assertEquals(5.0, inventory.getAmount(SubstanceId.ENERGY));
    }

    @Test
    void failsWhenEnergyInsufficient() {
        inventory.deposit(SubstanceId.WATER, 10.0, 1);
        inventory.deposit(SubstanceId.ENERGY, 0.5, 1);

        ProductionResult result = RecipeProcessor.tryProduceOnce(electrolysis, inventory, 2);

        assertFalse(result.success);
        assertEquals(RecipeProcessor.FAILURE_REASON_ENERGY_INSUFFICIENT, result.failureReason);
        // 失败时不修改库存
        assertEquals(10.0, inventory.getAmount(SubstanceId.WATER));
        assertEquals(0.5, inventory.getAmount(SubstanceId.ENERGY));
    }

    @Test
    void failsWhenOutputCapacityInsufficient() {
        // 容量 16：可容纳输入 WATER 10 + ENERGY 5（占用 15），但不足以再容纳
        // 产物 2 H2 + 1 O2 的 3 单位新增产出（15 + 3 = 18 > 16）
        inventory = new LocalInventory(1L, 100L, 16.0);
        inventory.deposit(SubstanceId.WATER, 10.0, 1);
        inventory.deposit(SubstanceId.ENERGY, 5.0, 1);

        ProductionResult result = RecipeProcessor.tryProduceOnce(electrolysis, inventory, 2);

        assertFalse(result.success);
        assertEquals(RecipeProcessor.FAILURE_REASON_OUTPUT_CAPACITY_INSUFFICIENT, result.failureReason);
        // 失败时不修改库存
        assertEquals(10.0, inventory.getAmount(SubstanceId.WATER));
        assertEquals(5.0, inventory.getAmount(SubstanceId.ENERGY));
    }

    @Test
    void productionRecordsInventoryChanges() {
        inventory.deposit(SubstanceId.WATER, 10.0, 1);
        inventory.deposit(SubstanceId.ENERGY, 5.0, 1);

        RecipeProcessor.tryProduceOnce(electrolysis, inventory, 2);

        // 变更日志：2 次存入 + 1 次能源取出 + 1 次水取出 + 1 次氢气存入 + 1 次氧气存入
        assertEquals(6, inventory.changes.size());
    }

    @Test
    void recipeMetadataMatchesRequiredFields() {
        // 配方数据必须表达 G2.3 全部要素
        assertNotNull(electrolysis.inputs);
        assertNotNull(electrolysis.outputs);
        assertNotNull(electrolysis.byproducts);
        assertEquals(RecipeRepository.FACILITY_TYPE_ELECTROLYZER, electrolysis.facilityType);
        assertTrue(electrolysis.energyCost > 0);
        assertTrue(electrolysis.processTime > 0);
        assertFalse(electrolysis.hasTechnologyRequirement());
    }
}
