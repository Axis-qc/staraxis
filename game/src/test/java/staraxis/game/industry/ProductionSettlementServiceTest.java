package staraxis.game.industry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ProductionSettlementServiceTest（工业日结算服务单元测试）
 *
 * 覆盖 G2.7 可调用结算服务：设施产能推进、能源消耗、失败阻塞、
 * 运输抵达结算与跨日配方进度。
 */
class ProductionSettlementServiceTest {

    private RecipeRepository repository;
    private ProductionSettlementService service;
    private IndustryRegistry registry;
    private LocalInventory inventory;
    private LocalInventory targetInventory;

    @BeforeEach
    void setUp() {
        repository = new RecipeRepository(new ObjectMapper());
        repository.setRecipes(RecipeRepository.defaultRecipes());
        service = new ProductionSettlementService(repository);

        registry = new IndustryRegistry();
        inventory = registry.createInventory(1001L);
        targetInventory = registry.createInventory(1002L);
    }

    @Test
    void electrolyzerProducesAfterOneDaySettlement() {
        inventory.deposit(SubstanceId.WATER, 10.0, 1);
        inventory.deposit(SubstanceId.ENERGY, 5.0, 1);
        registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER, inventory.inventoryId,
                1001L, RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);

        SettlementReport report = service.settleDay(registry, 100);

        assertEquals(1, report.getTotalProducedBatches());
        assertEquals(2.0, inventory.getAmount(SubstanceId.HYDROGEN));
        assertEquals(1.0, inventory.getAmount(SubstanceId.OXYGEN));
        assertEquals(8.0, inventory.getAmount(SubstanceId.WATER));
        assertEquals(4.0, inventory.getAmount(SubstanceId.ENERGY));

        // 设施状态为加工中（进度已归零）
        assertEquals(ProcessingFacility.STATUS_PROCESSING,
                registry.facilitiesById.values().iterator().next().status);
    }

    @Test
    void electrolyzerBlocksWhenEnergyInsufficient() {
        inventory.deposit(SubstanceId.WATER, 10.0, 1);
        // 能源不足（0.5 < 1.0）
        inventory.deposit(SubstanceId.ENERGY, 0.5, 1);
        ProcessingFacility facility = registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER,
                inventory.inventoryId, 1001L, RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);

        SettlementReport report = service.settleDay(registry, 100);

        assertEquals(0, report.getTotalProducedBatches());
        assertEquals(ProcessingFacility.STATUS_BLOCKED, facility.status);
        assertEquals(RecipeProcessor.FAILURE_REASON_ENERGY_INSUFFICIENT, facility.lastFailureReason);
        // 库存未被改动
        assertEquals(10.0, inventory.getAmount(SubstanceId.WATER));
        assertEquals(0.5, inventory.getAmount(SubstanceId.ENERGY));
    }

    @Test
    void electrolyzerBlocksWhenWaterInsufficient() {
        inventory.deposit(SubstanceId.WATER, 1.0, 1);
        inventory.deposit(SubstanceId.ENERGY, 5.0, 1);
        ProcessingFacility facility = registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER,
                inventory.inventoryId, 1001L, RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);

        SettlementReport report = service.settleDay(registry, 100);

        assertEquals(0, report.getTotalProducedBatches());
        assertEquals(ProcessingFacility.STATUS_BLOCKED, facility.status);
        assertEquals(RecipeProcessor.FAILURE_REASON_INPUT_INSUFFICIENT, facility.lastFailureReason);
    }

    @Test
    void facilityWithNoRecipeBlocks() {
        ProcessingFacility facility = registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER,
                inventory.inventoryId, 1001L, "UNKNOWN_RECIPE");

        SettlementReport report = service.settleDay(registry, 100);

        assertEquals(0, report.getTotalProducedBatches());
        assertEquals(ProcessingFacility.STATUS_BLOCKED, facility.status);
        assertEquals(ProductionSettlementService.FAILURE_REASON_NO_RECIPE, facility.lastFailureReason);
    }

    @Test
    void multiDayRecipeProducesOnlyAfterRequiredDays() {
        // 自定义 processTime=2 天的配方
        RecipeDef slowRecipe = new RecipeDef();
        slowRecipe.recipeId = "RECIPE_SLOW";
        slowRecipe.facilityType = RecipeRepository.FACILITY_TYPE_ELECTROLYZER;
        slowRecipe.inputs = java.util.List.of(new RecipeItem(SubstanceId.WATER, 2.0));
        slowRecipe.outputs = java.util.List.of(new RecipeItem(SubstanceId.HYDROGEN, 2.0));
        slowRecipe.byproducts = java.util.List.of(new RecipeItem(SubstanceId.OXYGEN, 1.0));
        slowRecipe.energyCost = 1.0;
        slowRecipe.processTime = 2.0;
        slowRecipe.technologyId = null;
        repository.setRecipes(java.util.List.of(slowRecipe));

        inventory.deposit(SubstanceId.WATER, 10.0, 1);
        inventory.deposit(SubstanceId.ENERGY, 5.0, 1);
        registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER, inventory.inventoryId,
                1001L, "RECIPE_SLOW");

        // 第一天：进度 1/2，未完成
        SettlementReport day1 = service.settleDay(registry, 100);
        assertEquals(0, day1.getTotalProducedBatches());
        assertEquals(0.0, inventory.getAmount(SubstanceId.HYDROGEN));

        // 第二天：进度 2/2，完成一批
        SettlementReport day2 = service.settleDay(registry, 101);
        assertEquals(1, day2.getTotalProducedBatches());
        assertEquals(2.0, inventory.getAmount(SubstanceId.HYDROGEN));
        assertEquals(1.0, inventory.getAmount(SubstanceId.OXYGEN));
    }

    @Test
    void extractionProducesBeforeElectrolyzerConsumesSameDay() {
        inventory.deposit(SubstanceId.ENERGY, 5.0, 1);
        registry.createExtractionFacility(ResourceExtractionFacility.TYPE_WATER_EXTRACTION,
                inventory.inventoryId, 1001L, SubstanceId.WATER, 20.0);
        registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER, inventory.inventoryId,
                1001L, RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);

        SettlementReport report = service.settleDay(registry, 100);

        // 采集先于加工：当日 20 水入库，电解同日消耗 2 水 + 1 能源产出 2H2 + 1O2
        assertEquals(1, report.extractions.size());
        assertEquals(20.0, report.getTotalExtracted().get(SubstanceId.WATER));
        assertEquals(1, report.getTotalProducedBatches());
        assertEquals(18.0, inventory.getAmount(SubstanceId.WATER));
        assertEquals(4.0, inventory.getAmount(SubstanceId.ENERGY));
        assertEquals(2.0, inventory.getAmount(SubstanceId.HYDROGEN));
        assertEquals(1.0, inventory.getAmount(SubstanceId.OXYGEN));
    }

    @Test
    void extractionBlocksWhenInventoryFull() {
        // 库存容量仅 10，能源占 5，无法再容纳 20 单位水
        LocalInventory tiny = new LocalInventory(9L, 1001L, 10.0);
        registry.inventoriesById.put(tiny.inventoryId, tiny);
        tiny.deposit(SubstanceId.ENERGY, 5.0, 1);
        ResourceExtractionFacility extractor = registry.createExtractionFacility(
                ResourceExtractionFacility.TYPE_WATER_EXTRACTION, tiny.inventoryId, 1001L,
                SubstanceId.WATER, 20.0);

        SettlementReport report = service.settleDay(registry, 100);

        // 采集容量不足：不写入、设施阻塞，反馈保持成立
        assertEquals(1, report.extractions.size());
        assertFalse(report.extractions.get(0).success);
        assertEquals(RecipeProcessor.FAILURE_REASON_OUTPUT_CAPACITY_INSUFFICIENT,
                report.extractions.get(0).failureReason);
        assertEquals(ResourceExtractionFacility.STATUS_BLOCKED, extractor.status);
        assertEquals(0.0, tiny.getAmount(SubstanceId.WATER));
    }

    @Test
    void extractionWithoutInventoryBlocks() {
        ResourceExtractionFacility extractor = registry.createExtractionFacility(
                ResourceExtractionFacility.TYPE_WATER_EXTRACTION, 9999L, 1001L,
                SubstanceId.WATER, 20.0);

        SettlementReport report = service.settleDay(registry, 100);

        assertEquals(1, report.extractions.size());
        assertFalse(report.extractions.get(0).success);
        assertEquals(ProductionSettlementService.FAILURE_REASON_NO_INVENTORY,
                report.extractions.get(0).failureReason);
        assertEquals(ResourceExtractionFacility.STATUS_BLOCKED, extractor.status);
    }

    @Test
    void electrolyzerBlocksWhenEnergyRunsOutWhileExtractionContinues() {
        inventory.deposit(SubstanceId.ENERGY, 0.5, 1);
        registry.createExtractionFacility(ResourceExtractionFacility.TYPE_WATER_EXTRACTION,
                inventory.inventoryId, 1001L, SubstanceId.WATER, 20.0);
        ProcessingFacility facility = registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER,
                inventory.inventoryId, 1001L, RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);

        SettlementReport report = service.settleDay(registry, 100);

        // 采集成功（水 20 入库），电解因能源不足阻塞：能源不足反馈仍成立
        assertEquals(20.0, inventory.getAmount(SubstanceId.WATER));
        assertEquals(0.5, inventory.getAmount(SubstanceId.ENERGY));
        assertEquals(0, report.getTotalProducedBatches());
        assertEquals(ProcessingFacility.STATUS_BLOCKED, facility.status);
        assertEquals(RecipeProcessor.FAILURE_REASON_ENERGY_INSUFFICIENT, facility.lastFailureReason);
        assertEquals(0.0, inventory.getAmount(SubstanceId.HYDROGEN));
    }

    @Test
    void electrolyzerBlocksWhenExtractionCannotCoverInput() {
        inventory.deposit(SubstanceId.ENERGY, 5.0, 1);
        // 每日采集 1 水，电解需要 2 水：采集后仍不足，电解阻塞：输入不足反馈仍成立
        registry.createExtractionFacility(ResourceExtractionFacility.TYPE_WATER_EXTRACTION,
                inventory.inventoryId, 1001L, SubstanceId.WATER, 1.0);
        ProcessingFacility facility = registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER,
                inventory.inventoryId, 1001L, RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);

        SettlementReport report = service.settleDay(registry, 100);

        assertEquals(1.0, inventory.getAmount(SubstanceId.WATER));
        assertEquals(0, report.getTotalProducedBatches());
        assertEquals(ProcessingFacility.STATUS_BLOCKED, facility.status);
        assertEquals(RecipeProcessor.FAILURE_REASON_INPUT_INSUFFICIENT, facility.lastFailureReason);
    }

    @Test
    void settlementDeliversInTransitTransfer() {
        inventory.deposit(SubstanceId.IRON, 10.0, 1);
        CargoTransfer transfer = registry.startTransfer(inventory.inventoryId, targetInventory.inventoryId,
                Map.of(SubstanceId.IRON, 4.0), 5);

        SettlementReport report = service.settleDay(registry, 200);

        assertNotNull(report);
        assertEquals(1, report.transfers.size());
        assertEquals(CargoTransfer.STATUS_ARRIVED, report.transfers.get(0).resultType);
        assertEquals(CargoTransfer.STATUS_ARRIVED, transfer.status);
        assertEquals(4.0, targetInventory.getAmount(SubstanceId.IRON));
        assertEquals(6.0, inventory.getAmount(SubstanceId.IRON));
    }

    @Test
    void settlementKeepsTransferInTransitWhenTargetFull() {
        inventory.deposit(SubstanceId.IRON, 10.0, 1);
        LocalInventory full = new LocalInventory(5L, 5L, 2.0);
        registry.inventoriesById.put(full.inventoryId, full);

        CargoTransfer transfer = registry.startTransfer(inventory.inventoryId, full.inventoryId,
                Map.of(SubstanceId.IRON, 4.0), 5);

        SettlementReport report = service.settleDay(registry, 200);

        assertEquals(CargoTransfer.STATUS_IN_TRANSIT, report.transfers.get(0).resultType);
        assertEquals(CargoTransfer.STATUS_IN_TRANSIT, transfer.status);
        assertEquals(0.0, full.getAmount(SubstanceId.IRON));
    }

    @Test
    void emptyRegistrySettlesWithoutError() {
        SettlementReport report = service.settleDay(registry, 300);
        assertNotNull(report);
        assertTrue(report.facilities.isEmpty());
        assertTrue(report.transfers.isEmpty());
    }

    @Test
    void facilitiesWithoutInventoryBlock() {
        ProcessingFacility facility = registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER,
                9999L, 1001L, RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);

        SettlementReport report = service.settleDay(registry, 100);

        assertEquals(0, report.getTotalProducedBatches());
        assertEquals(ProcessingFacility.STATUS_BLOCKED, facility.status);
        assertEquals(ProductionSettlementService.FAILURE_REASON_NO_INVENTORY, facility.lastFailureReason);
    }

    @Test
    void settlementReportAggregatesProduced() {
        inventory.deposit(SubstanceId.WATER, 100.0, 1);
        inventory.deposit(SubstanceId.ENERGY, 50.0, 1);
        registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER, inventory.inventoryId,
                1001L, RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);

        SettlementReport report = service.settleDay(registry, 100);

        assertEquals(2.0, report.getTotalProduced().get(SubstanceId.HYDROGEN));
        assertEquals(1.0, report.getTotalProduced().get(SubstanceId.OXYGEN));
        assertFalse(report.getTotalProduced().containsKey(SubstanceId.IRON));
    }

    @Test
    void switchingRecipeDiscardsOldProgress() {
        // 慢配方 processTime=2，快配方 processTime=1（同设施类型）
        RecipeDef slowRecipe = new RecipeDef();
        slowRecipe.recipeId = "RECIPE_SLOW";
        slowRecipe.facilityType = RecipeRepository.FACILITY_TYPE_ELECTROLYZER;
        slowRecipe.inputs = java.util.List.of(new RecipeItem(SubstanceId.WATER, 2.0));
        slowRecipe.outputs = java.util.List.of(new RecipeItem(SubstanceId.HYDROGEN, 2.0));
        slowRecipe.byproducts = java.util.List.of(new RecipeItem(SubstanceId.OXYGEN, 1.0));
        slowRecipe.energyCost = 1.0;
        slowRecipe.processTime = 2.0;

        RecipeDef fastRecipe = new RecipeDef();
        fastRecipe.recipeId = "RECIPE_FAST";
        fastRecipe.facilityType = RecipeRepository.FACILITY_TYPE_ELECTROLYZER;
        fastRecipe.inputs = java.util.List.of(new RecipeItem(SubstanceId.WATER, 2.0));
        fastRecipe.outputs = java.util.List.of(new RecipeItem(SubstanceId.HYDROGEN, 2.0));
        fastRecipe.byproducts = java.util.List.of(new RecipeItem(SubstanceId.OXYGEN, 1.0));
        fastRecipe.energyCost = 1.0;
        fastRecipe.processTime = 1.0;
        repository.setRecipes(java.util.List.of(slowRecipe, fastRecipe));

        inventory.deposit(SubstanceId.WATER, 10.0, 1);
        inventory.deposit(SubstanceId.ENERGY, 5.0, 1);
        ProcessingFacility facility = registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER,
                inventory.inventoryId, 1001L, "RECIPE_SLOW");

        // 第一天：慢配方进度 1/2
        service.settleDay(registry, 100);
        assertEquals(1.0, facility.progressDays);

        // 换到快配方：旧进度不得继承，按新配方从 0 重新计时
        facility.switchRecipe("RECIPE_FAST");
        assertEquals(0.0, facility.progressDays);

        // 第二天：快配方进度 1/1，只完成一批（若继承旧进度则会完成两批）
        SettlementReport report = service.settleDay(registry, 101);
        assertEquals(1, report.getTotalProducedBatches());
        assertEquals(8.0, inventory.getAmount(SubstanceId.WATER));
        assertEquals(2.0, inventory.getAmount(SubstanceId.HYDROGEN));
    }

    @Test
    void directlyChangedActiveRecipeResetsProgress() {
        // 慢配方 processTime=2，快配方 processTime=1
        RecipeDef slowRecipe = new RecipeDef();
        slowRecipe.recipeId = "RECIPE_SLOW";
        slowRecipe.facilityType = RecipeRepository.FACILITY_TYPE_ELECTROLYZER;
        slowRecipe.inputs = java.util.List.of(new RecipeItem(SubstanceId.WATER, 2.0));
        slowRecipe.outputs = java.util.List.of(new RecipeItem(SubstanceId.HYDROGEN, 2.0));
        slowRecipe.byproducts = java.util.List.of(new RecipeItem(SubstanceId.OXYGEN, 1.0));
        slowRecipe.energyCost = 1.0;
        slowRecipe.processTime = 2.0;

        RecipeDef fastRecipe = new RecipeDef();
        fastRecipe.recipeId = "RECIPE_FAST";
        fastRecipe.facilityType = RecipeRepository.FACILITY_TYPE_ELECTROLYZER;
        fastRecipe.inputs = java.util.List.of(new RecipeItem(SubstanceId.WATER, 2.0));
        fastRecipe.outputs = java.util.List.of(new RecipeItem(SubstanceId.HYDROGEN, 2.0));
        fastRecipe.byproducts = java.util.List.of(new RecipeItem(SubstanceId.OXYGEN, 1.0));
        fastRecipe.energyCost = 1.0;
        fastRecipe.processTime = 1.0;
        repository.setRecipes(java.util.List.of(slowRecipe, fastRecipe));

        inventory.deposit(SubstanceId.WATER, 10.0, 1);
        inventory.deposit(SubstanceId.ENERGY, 5.0, 1);
        ProcessingFacility facility = registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER,
                inventory.inventoryId, 1001L, "RECIPE_SLOW");

        // 第一天：慢配方进度 1/2
        service.settleDay(registry, 100);
        assertEquals(1.0, facility.progressDays);

        // 绕过 switchRecipe 直接改 activeRecipeId：结算时应检测到配方切换并丢弃旧进度
        facility.activeRecipeId = "RECIPE_FAST";

        SettlementReport report = service.settleDay(registry, 101);
        assertEquals(1, report.getTotalProducedBatches());
        assertEquals(2.0, inventory.getAmount(SubstanceId.HYDROGEN));
    }

    @Test
    void settlementReportOrderMatchesFacilityCreationOrder() {
        // 慢配方 + 快配方，按创建顺序注册两个设施
        RecipeDef slowRecipe = new RecipeDef();
        slowRecipe.recipeId = "RECIPE_SLOW";
        slowRecipe.facilityType = RecipeRepository.FACILITY_TYPE_ELECTROLYZER;
        slowRecipe.inputs = java.util.List.of(new RecipeItem(SubstanceId.WATER, 2.0));
        slowRecipe.outputs = java.util.List.of(new RecipeItem(SubstanceId.HYDROGEN, 2.0));
        slowRecipe.byproducts = java.util.List.of(new RecipeItem(SubstanceId.OXYGEN, 1.0));
        slowRecipe.energyCost = 1.0;
        slowRecipe.processTime = 2.0;
        RecipeDef fastRecipe = RecipeRepository.defaultRecipes().get(0);
        repository.setRecipes(java.util.List.of(slowRecipe, fastRecipe));

        inventory.deposit(SubstanceId.WATER, 100.0, 1);
        inventory.deposit(SubstanceId.ENERGY, 50.0, 1);
        ProcessingFacility first = registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER,
                inventory.inventoryId, 1001L, "RECIPE_SLOW");
        ProcessingFacility second = registry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER,
                inventory.inventoryId, 1001L, RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);

        SettlementReport report = service.settleDay(registry, 100);

        // 结算报告顺序必须与设施创建（ID 分配）顺序一致，保证输出确定性
        assertEquals(2, report.facilities.size());
        assertEquals(first.facilityId, report.facilities.get(0).facilityId);
        assertEquals(second.facilityId, report.facilities.get(1).facilityId);
    }
}
