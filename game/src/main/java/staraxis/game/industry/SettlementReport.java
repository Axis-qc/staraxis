package staraxis.game.industry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SettlementReport（日结算报告）
 *
 * 由 ProductionSettlementService 在每日结算时生成，
 * 汇总每个设施的生产结果与每笔运输的抵达结果，供 UI 反馈（G2.8）与测试断言。
 */
public class SettlementReport {

    /** 结算时的模拟 tick。 */
    public final long tick;

    /** 采集设施结算结果列表。 */
    public final List<ExtractionResult> extractions = new ArrayList<>();

    /** 设施结算结果列表。 */
    public final List<FacilityResult> facilities = new ArrayList<>();

    /** 运输结算结果列表。 */
    public final List<TransferResult> transfers = new ArrayList<>();

    /**
     * 构造结算报告。
     *
     * @param tick 结算时模拟 tick
     */
    public SettlementReport(long tick) {
        this.tick = tick;
    }

    /**
     * 采集设施结算结果。
     */
    public static class ExtractionResult {

        /** 设施 ID。 */
        public long facilityId;

        /** 设施类型。 */
        public String facilityType;

        /** 采集的资源物质 ID。 */
        public String resourceId;

        /** 当日是否成功写入固定产出。 */
        public boolean success;

        /** 失败原因（阻塞/失败时非空，见 ProductionSettlementService / RecipeProcessor 常量）。 */
        public String failureReason;

        /** 当日累计产出（substanceId -> 数量）。 */
        public Map<String, Double> extracted = new LinkedHashMap<>();
    }

    /**
     * 设施生产结果。
     */
    public static class FacilityResult {

        /** 设施 ID。 */
        public long facilityId;

        /** 设施类型。 */
        public String facilityType;

        /** 配方 ID。 */
        public String recipeId;

        /** 当日是否至少成功生产一批。 */
        public boolean success;

        /** 失败原因（阻塞/失败时非空，见 RecipeProcessor 常量）。 */
        public String failureReason;

        /** 当日完成批次数量。 */
        public int batchCount;

        /** 当日累计产出（substanceId -> 数量）。 */
        public Map<String, Double> produced = new LinkedHashMap<>();

        /** 当日累计消耗（substanceId -> 数量）。 */
        public Map<String, Double> consumed = new LinkedHashMap<>();
    }

    /**
     * 运输结算结果。
     */
    public static class TransferResult {

        /** 运输记录 ID。 */
        public long transferId;

        /** 结果类型：ARRIVED（已抵达）/ TARGET_FULL（目标库存已满，保持运输中）。 */
        public String resultType;

        /** 运输货物（substanceId -> 数量）。 */
        public Map<String, Double> goods = new LinkedHashMap<>();
    }

    /**
     * 汇总统计当日累计采集产出（所有采集设施）。
     *
     * @return substanceId -> 总量
     */
    public Map<String, Double> getTotalExtracted() {
        Map<String, Double> total = new LinkedHashMap<>();
        for (ExtractionResult result : extractions) {
            for (Map.Entry<String, Double> entry : result.extracted.entrySet()) {
                total.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
        return total;
    }

    /**
     * 汇总统计当日成功生产批次总数。
     *
     * @return 全部设施成功批次之和
     */
    public int getTotalProducedBatches() {
        int total = 0;
        for (FacilityResult result : facilities) {
            total += result.batchCount;
        }
        return total;
    }

    /**
     * 汇总统计当日累计产出（所有设施）。
     *
     * @return substanceId -> 总量
     */
    public Map<String, Double> getTotalProduced() {
        Map<String, Double> total = new LinkedHashMap<>();
        for (FacilityResult result : facilities) {
            for (Map.Entry<String, Double> entry : result.produced.entrySet()) {
                total.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
        return total;
    }
}
