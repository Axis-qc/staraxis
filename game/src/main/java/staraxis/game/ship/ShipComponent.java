/*
 * ShipComponent
 *
 * 文件作用：
 * - 舰船组件实例（运行时状态）。
 * - 与 ShipModuleDef（只读定义）对应：ShipComponent 表示“已装配到某艘舰船上的一个模块实例”。
 *
 * 提供的接口 API：
 * - 公共字段：moduleId、hp、level、customName。
 *
 * 使用方式：
 * - ShipDesign（蓝图）使用 moduleId 列表表达“需要装配哪些模块”。
 * - ShipBody（实体）持有 ShipComponent 列表表达“当前舰船实际装配与损伤状态”。
 *
 * 注意事项：
 * - moduleId 必须能在 ShipAssetRepository 中解析到 ShipModuleDef，否则视为无效组件。
 * - 本类为权威可变状态的一部分，只允许在 game 模拟层修改。
 */

package staraxis.game.ship;

/**
 * 舰船组件实例（运行时状态）。
 */
public class ShipComponent {

    /** 指向 ShipModuleDef.moduleId。 */
    public String moduleId;

    /** 组件耐久（0~1 或 0~maxHP，口径后续统一；当前先用 0~1）。 */
    public double hp;

    /** 组件等级（用于升级系统，当前占位）。 */
    public int level;

    /** 玩家自定义名称（可选）。 */
    public String customName;

    public ShipComponent() {
    }

    public ShipComponent(String moduleId) {
        this.moduleId = moduleId;
        this.hp = 1.0;
        this.level = 1;
    }
}
