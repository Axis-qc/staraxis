/*
 * ShipDesign
 *
 * 文件作用：
 * - 舰船蓝图（玩家在舰船设计器中自定义并保存的配置）。
 * - 蓝图是“可序列化的设计意图”，用于生产系统批量建造舰船，以及在 UI 中展示/编辑。
 *
 * 提供的接口 API：
 * - 公共字段：designId、name、description、hullId、moduleIds、tags。
 *
 * 使用方式：
 * - 设计器：玩家编辑 moduleIds（按槽位/布局约束，后续由 HullDef 约束）。
 * - 保存：由 ShipDesignRepository 将 ShipDesign 序列化到 gamedata/designs/ship/{designId}.json。
 * - 生产：生产系统读取 ShipDesign，并从 ShipAssetRepository 解析 moduleId -> ShipModuleDef 计算成本与属性。
 *
 * 注意事项：
 * - designId 为蓝图主键（建议使用稳定 ID，而非显示名称）。
 * - moduleIds 中的每个 moduleId 必须在 ShipAssetRepository 中可解析，否则生产/生成应失败或跳过（口径后续统一）。
 * - 本类为数据模型，不包含权威运行时状态；运行时状态在 ShipBody/ShipComponent。
 */

package staraxis.game.ship;

import java.util.List;

/**
 * 舰船蓝图（玩家自定义并保存的设计）。
 */
public class ShipDesign {

    /** 蓝图唯一 ID（主键）。 */
    public String designId;

    /** 显示名称（可国际化或玩家输入）。 */
    public String name;

    /** 描述（可选）。 */
    public String description;

    /** 船体 ID（未来对齐 HullDef；当前占位）。 */
    public String hullId;

    /** 模块 ID 列表（指向 ShipModuleDef.moduleId）。 */
    public List<String> moduleIds;

    /** 标签（用于筛选/分组，如 role:combat, size:frigate 等）。 */
    public List<String> tags;

    public ShipDesign() {
    }
}
