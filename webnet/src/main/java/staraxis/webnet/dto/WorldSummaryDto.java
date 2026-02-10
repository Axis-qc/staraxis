package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * WorldSummaryDto
 *
 * 作用（description）：
 * - 提供游戏世界的宏观统计简报喵。
 * - 用于 AI 助手快速掌握全局局势，避免拉取海量详细快照喵。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class WorldSummaryDto {
    /** 游戏当前天数 (gameDay)。 */
    public int gameDay;
    /** 游戏当前 Tick (simulationTick)。 */
    public long simulationTick;

    /** 实体统计：按类型 (EntityType) 统计数量喵。 */
    public Map<String, Integer> entityCounts;

    /** 国家概况：nationId -> 领土/资产统计喵。 */
    public Map<Long, NationSummary> nations;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class NationSummary {
        /** 国家名称 (name)。 */
        public String name;
        /** 拥有的行星数量 (planetCount)。 */
        public int planetCount;
        /** 拥有的舰队数量 (fleetCount)。 */
        public int fleetCount;
        /** 简单战力评估 (totalPower) 喵。 */
        public double totalPower;
    }

    public WorldSummaryDto() {
    }
}
