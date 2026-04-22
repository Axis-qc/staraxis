/*
 * ShipBody
 *
 * 文件作用：
 * - 舰船实体（权威世界状态中的可交互对象），继承 Entity。
 * - 持有舰船的运行时状态：蓝图引用、装配组件实例、归属信息、基础运动状态等。
 *
 * 提供的接口 API：
 * - 公共字段：designId、components、hpHull、power、fuel；ownerNationId 继承自 Entity。
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
import staraxis.game.world.Vec2d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 舰船实体（权威世界状态中的可交互对象）。
 */
public class ShipBody extends Entity {

    /** 舰船来源蓝图 ID（ShipDesign.designId）。 */
    public String designId;


    /** 已装配组件实例列表。 */
    public List<ShipComponent> components = new ArrayList<>();

    /** 船体耐久（0~1 或 0~maxHP，口径后续统一；当前先用 0~1）。 */
    public double hpHull = 1.0;

    /** 能源（占位）。 */
    public double power;

    /** 燃料（占位）。 */
    public double fuel;

    /**
     * 舰船自定义标记集合（customFlags）喵。
     *
     * 说明：
     * - 用于承载可扩展语义（例如初始出生舰船、任务专用舰船等）喵。
     * - 采用字符串集合避免频繁新增布尔字段喵。
     */
    public Set<String> customFlags = new LinkedHashSet<>();

    public ShipBody() {
        this.entityType = EntityType.SHIP;
    }

    /**
     * 移动目标位置（世界坐标 GU）喵。
     * 当 isMoving = true 时，舰船会向此位置移动。
     */
    public Vec2d movementTarget;

    /**
     * 是否正在移动喵。
     */
    public boolean isMoving = false;

    /**
     * 当前舰首朝向（角度制，0度朝+X方向）喵。
     * 这是舰船当前的实际朝向，用于物理计算。
     */
    public double currentHeadingDeg = 0.0;

    /**
     * 目标朝向（角度制，0度朝+X方向）喵。
     * 这是舰船想要转向的目标朝向。
     */
    public double targetHeadingDeg = 0.0;

    // ========== 舰船性能数据（TODO: 应从 ShipDesign 配置读取，并支持科技等级加成）==========

    /**
     * 最大速度（GU/游戏秒）喵。
     * 全向移动时的理论最大速度。
     * TODO: 当前为殖民舰默认值，后续应根据舰船设计和科技等级计算喵。
     */
    public double maxSpeed = 20.0;

    /**
     * 基础加速度（GU/游戏秒²）喵。
     * 侧向/反向移动时的加速度。
     * TODO: 后续应根据舰船设计和科技等级计算喵。
     */
    public double baseAcceleration = 5.0;

    /**
     * 舰首朝向加速度加成（GU/游戏秒²）喵。
     * 当移动方向与舰首朝向一致时的额外加速度。
     * 总加速度 = baseAcceleration + bowAccelerationBonus
     * TODO: 后续应根据舰船设计和科技等级计算喵。
     */
    public double bowAccelerationBonus = 5.0;

    /**
     * 转向角速度（度/游戏秒）喵。
     * 舰首旋转的速度。
     * TODO: 后续应根据舰船设计和科技等级计算喵。
     */
    public double turnRate = 45.0;

    /**
     * 侧向移动速度惩罚系数（0.0~1.0）喵。
     * 垂直于舰首方向移动时的速度惩罚。
     * 实际最大速度 = maxSpeed * lateralSpeedPenalty
     */
    public double lateralSpeedPenalty = 0.6;

    /**
     * 反向移动速度惩罚系数（0.0~1.0）喵。
     * 与舰首相反方向移动时的速度惩罚。
     * 实际最大速度 = maxSpeed * reverseSpeedPenalty
     */
    public double reverseSpeedPenalty = 0.3;

    /**
     * 当前移动指令（简化计算模式）喵。
     * 当启用简化计算时，存储移动指令信息喵。
     */
    public MovementCommand movementCommand;

    public String activeClientCommandId;

    public String lastCompletedClientCommandId;

    /**
     * 是否启用简化计算模式喵。
     * 为 true 时，ShipMovementSystem 使用基于指令的推测计算喵。
     * 为 false 时，使用完整的物理计算（向后兼容）喵。
     */
    public boolean simplifiedMovementEnabled = true;
}
