/*
 * ShipBody
 *
 * 文件作用：
 * - 舰船实体（权威世界状态中的可交互对象），继承 Entity。
 * - 持有舰船的运行时状态：蓝图引用、装配组件实例、归属信息、基础运动状态等。
 *
 * 提供的接口 API：
 * - 公共字段：designId、ownerNationId、components、hpHull、power、fuel。
 *
 * 使用方式：
 * - 世界生成/生产：根据 ShipDesign 生成 ShipBody，并将 moduleIds 实例化为 ShipComponent 列表。
 * - 战斗/移动系统：在 tick 内修改 ShipBody 的 posWorldGU/velWorldGU/组件 hp 等权威状态。
 *
 * 注意事项：
 * - ShipBody 是权威可变状态，只允许在 game 模拟层修改。
 * - designId 指向 ShipDesign.designId，用于追溯“该船由哪个蓝图建造”。
 * - components 中的 moduleId 应能在 ShipAssetRepository 中解析到 ShipModuleDef。
 */

package staraxis.game.ship;

import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * 舰船实体（权威世界状态中的可交互对象）。
 */
public class ShipBody extends Entity {

    /** 舰船来源蓝图 ID（ShipDesign.designId）。 */
    public String designId;

    /** 所属国家/文明 ID（nationId 口径，当前用 long，占位）。 */
    public long ownerNationId;

    /** 已装配组件实例列表。 */
    public List<ShipComponent> components = new ArrayList<>();

    /** 船体耐久（0~1 或 0~maxHP，口径后续统一；当前先用 0~1）。 */
    public double hpHull = 1.0;

    /** 能源（占位）。 */
    public double power;

    /** 燃料（占位）。 */
    public double fuel;

    public ShipBody() {
        this.entityType = EntityType.SHIP;
    }
}
