package staraxis.game.industry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * RecipeItem（配方物品项）
 *
 * 配方中单个物质的消耗/产出/副产物条目（substanceId + amount）。
 *
 * 未知字段兼容策略：忽略未来新增的未知字段（@JsonIgnoreProperties），
 * 保证读取未来版本配方数据（单向兼容）不受影响。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecipeItem {

    /** 物质 ID（见 {@link SubstanceId}）。 */
    public String substanceId;

    /** 数量（单位）。 */
    public double amount;

    /**
     * 默认构造（Jackson 反序列化用）。
     */
    public RecipeItem() {
    }

    /**
     * 构造配方物品项。
     *
     * @param substanceId 物质 ID
     * @param amount      数量
     */
    public RecipeItem(String substanceId, double amount) {
        this.substanceId = substanceId;
        this.amount = amount;
    }
}
