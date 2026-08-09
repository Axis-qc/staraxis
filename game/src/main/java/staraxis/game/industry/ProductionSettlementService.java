package staraxis.game.industry;

import java.util.Map;

/**
 * ProductionSettlementService（工业日结算服务）
 *
 * 由 game 权威层在每日结算时调用，按固定顺序推进本地库存、设施产能、配方产出与运输抵达：
 * 1. 采集设施：每日向所属库存写入固定资源产出（先于加工，保证当日新增原料可被加工设施消费）。
 * 2. 加工设施：每日累加 progressDays，达到 recipe.processTime 后通过 RecipeProcessor 尝试生产，
 *    输入不足/能源不足/输出容量不足时设施进入 BLOCKED 并记录失败原因。
 * 3. 货物运输：对每笔 IN_TRANSIT 运输尝试结算抵达（写入目标库存），
 *    目标库存无法容纳时保持运输中，下个结算日重试。
 *
 * 与 DailySettlementState 的关系（G2.7）：
 * - 本服务为可调用的权威结算入口，结算结果通过 SettlementReport 返回。
 * - 完整接入 DailySettlementState 双缓冲（将报告写入低频基线快照字段）由后续里程碑完成，
 *   避免与现有 baseline 发布链路冲突。
 */
public class ProductionSettlementService {

    /** 失败原因：设施未配置有效配方。 */
    public static final String FAILURE_REASON_NO_RECIPE = "NO_RECIPE";

    /** 失败原因：设施所属库存不存在。 */
    public static final String FAILURE_REASON_NO_INVENTORY = "NO_INVENTORY";

    /** 失败原因：采集设施每日产出量非法（非正或资源 ID 缺失）。 */
    public static final String FAILURE_REASON_INVALID_EXTRACTION_AMOUNT = "INVALID_EXTRACTION_AMOUNT";

    /** 单日最大完成批次上限（防止极小 processTime 导致单日无限循环）。 */
    private static final int MAX_BATCHES_PER_DAY = 100;

    private final RecipeRepository recipeRepository;

    /**
     * 构造日结算服务。
     *
     * @param recipeRepository 配方仓库（提供设施所需配方）
     */
    public ProductionSettlementService(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    /**
     * 执行一次每日结算。
     *
     * 步骤（固定顺序保证确定性）：
     * 1. 采集设施：向所属库存写入固定资源产出（先于加工，当日新增原料可被加工消费）。
     * 2. 推进全部加工设施的生产进度，并尝试完成配方。
     * 3. 结算全部在途运输（尝试写入目标库存）。
     *
     * @param registry 工业系统注册表
     * @param tick     当前模拟 tick
     * @return 结算报告
     */
    public SettlementReport settleDay(IndustryRegistry registry, long tick) {
        SettlementReport report = new SettlementReport(tick);
        if (registry == null) {
            return report;
        }

        // 1. 采集设施产出（先于加工设施，保证当日新增原料可被加工设施消费）
        for (ResourceExtractionFacility facility : registry.extractionFacilitiesById.values()) {
            settleExtraction(facility, registry, report, tick);
        }

        // 2. 推进加工设施
        for (ProcessingFacility facility : registry.facilitiesById.values()) {
            settleFacility(facility, registry, report, tick);
        }

        // 3. 结算在途运输
        for (CargoTransfer transfer : registry.getInTransitTransfers()) {
            CargoTransfer completed = registry.completeTransfer(transfer.transferId, tick);
            SettlementReport.TransferResult transferResult = new SettlementReport.TransferResult();
            transferResult.transferId = transfer.transferId;
            transferResult.goods.putAll(transfer.goods);
            if (completed != null) {
                transferResult.resultType = CargoTransfer.STATUS_ARRIVED;
            } else {
                transferResult.resultType = CargoTransfer.STATUS_IN_TRANSIT;
            }
            report.transfers.add(transferResult);
        }

        return report;
    }

    /**
     * 推进单个采集设施一个结算日（向所属库存写入固定产出）。
     *
     * 失败分支：
     * - 所属库存缺失：NO_INVENTORY。
     * - 每日产出量非正或资源 ID 缺失：INVALID_EXTRACTION_AMOUNT。
     * - 库存容量不足：OUTPUT_CAPACITY_INSUFFICIENT（不写入，保持阻塞，容量释放后自动恢复）。
     */
    private void settleExtraction(ResourceExtractionFacility facility, IndustryRegistry registry,
            SettlementReport report, long tick) {
        SettlementReport.ExtractionResult result = new SettlementReport.ExtractionResult();
        result.facilityId = facility.facilityId;
        result.facilityType = facility.facilityType;
        result.resourceId = facility.resourceId;

        // 校验所属库存
        LocalInventory inventory = registry.getInventory(facility.inventoryId);
        if (inventory == null) {
            result.failureReason = FAILURE_REASON_NO_INVENTORY;
            facility.status = ResourceExtractionFacility.STATUS_BLOCKED;
            facility.lastFailureReason = FAILURE_REASON_NO_INVENTORY;
            report.extractions.add(result);
            return;
        }

        // 校验产出定义
        if (facility.amountPerDay <= 0.0 || facility.resourceId == null || facility.resourceId.isBlank()) {
            result.failureReason = FAILURE_REASON_INVALID_EXTRACTION_AMOUNT;
            facility.status = ResourceExtractionFacility.STATUS_BLOCKED;
            facility.lastFailureReason = FAILURE_REASON_INVALID_EXTRACTION_AMOUNT;
            report.extractions.add(result);
            return;
        }

        // 写入固定产出（容量不足时失败，不部分写入）
        if (!inventory.deposit(facility.resourceId, facility.amountPerDay, tick)) {
            result.failureReason = RecipeProcessor.FAILURE_REASON_OUTPUT_CAPACITY_INSUFFICIENT;
            facility.status = ResourceExtractionFacility.STATUS_BLOCKED;
            facility.lastFailureReason = RecipeProcessor.FAILURE_REASON_OUTPUT_CAPACITY_INSUFFICIENT;
            report.extractions.add(result);
            return;
        }

        result.success = true;
        result.extracted.put(facility.resourceId, facility.amountPerDay);
        facility.status = ResourceExtractionFacility.STATUS_ACTIVE;
        facility.lastFailureReason = null;
        report.extractions.add(result);
    }

    /**
     * 推进单个加工设施一个结算日。
     */
    private void settleFacility(ProcessingFacility facility, IndustryRegistry registry,
            SettlementReport report, long tick) {
        SettlementReport.FacilityResult result = new SettlementReport.FacilityResult();
        result.facilityId = facility.facilityId;
        result.facilityType = facility.facilityType;
        result.recipeId = facility.activeRecipeId;

        // 校验配方
        RecipeDef recipe = recipeRepository.getRecipe(facility.activeRecipeId);
        if (recipe == null) {
            result.failureReason = FAILURE_REASON_NO_RECIPE;
            facility.status = ProcessingFacility.STATUS_BLOCKED;
            facility.lastFailureReason = FAILURE_REASON_NO_RECIPE;
            report.facilities.add(result);
            return;
        }

        // 校验所属库存
        LocalInventory inventory = registry.getInventory(facility.inventoryId);
        if (inventory == null) {
            result.failureReason = FAILURE_REASON_NO_INVENTORY;
            facility.status = ProcessingFacility.STATUS_BLOCKED;
            facility.lastFailureReason = FAILURE_REASON_NO_INVENTORY;
            report.facilities.add(result);
            return;
        }

        // 换配方防御：若 activeRecipeId 与进度归属配方不一致（绕过 switchRecipe 直接改字段），
        // 丢弃旧配方进度，避免进度按新配方的 processTime 错误继承
        if (!java.util.Objects.equals(facility.activeRecipeId, facility.progressRecipeId)) {
            facility.progressDays = 0.0;
            facility.progressRecipeId = facility.activeRecipeId;
        }

        // 推进一日进度并尝试生产
        facility.progressDays += 1.0;
        int batchCount = 0;
        while (facility.progressDays + LocalInventory.EPSILON >= recipe.processTime
                && batchCount < MAX_BATCHES_PER_DAY) {
            ProductionResult production = RecipeProcessor.tryProduceOnce(recipe, inventory, tick);
            if (production.success) {
                facility.progressDays -= recipe.processTime;
                batchCount++;
                mergeInto(result.produced, production.produced);
                mergeInto(result.consumed, production.consumed);
            } else {
                result.failureReason = production.failureReason;
                facility.status = ProcessingFacility.STATUS_BLOCKED;
                facility.lastFailureReason = production.failureReason;
                break;
            }
        }

        result.success = batchCount > 0;
        result.batchCount = batchCount;
        if (result.failureReason == null) {
            facility.status = ProcessingFacility.STATUS_PROCESSING;
        }
        report.facilities.add(result);
    }

    /**
     * 将明细合并入累计表。
     */
    private void mergeInto(Map<String, Double> target, Map<String, Double> source) {
        for (java.util.Map.Entry<String, Double> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Double::sum);
        }
    }
}
