/*
 * ShipDetails
 *
 * 文件作用：
 * - EntitySnapshot.details 的 Ship 类型载荷（DTO），用于前端展示舰船的最小必要信息。
 * - 作为 game -> webnet/web 的数据契约的一部分，必须保持向后兼容与可演进。
 *
 * 提供的接口 API：
 * - 公共字段：designId、ownerNationId（String）、hpHull、componentModuleIds。
 *
 * 使用方式：
 * - 构建 EntitySnapshot 时，当 entityType = SHIP，details 赋值为 ShipDetails 实例。
 * - 前端通过 entityType 判别并读取对应字段。
 *
 * 注意事项：
 * - 本类是只读快照 DTO，不应包含可变引用。
 * - componentModuleIds 仅用于展示/调试；后续如需详细组件状态，应新增版本化字段或新 DTO。
 */

package staraxis.game.state.snapshot;

import java.util.List;

/**
 * 舰船 details 快照（EntitySnapshot.details for SHIP）。
 */
public class ShipDetails {

    /** 舰船来源蓝图 ID（ShipDesign.designId）。 */
    public final String designId;

    /** 船体耐久（当前口径 0~1）。 */
    public final double hpHull;

    /** 已装配组件的 moduleId 列表（最小展示信息）。 */
    public final List<String> componentModuleIds;

    public ShipDetails(String designId, double hpHull, List<String> componentModuleIds) {
        this.designId = designId;
        this.hpHull = hpHull;
        this.componentModuleIds = componentModuleIds;
    }
}
