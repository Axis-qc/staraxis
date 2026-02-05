/*
 * ShipModuleDef
 *
 * 文件作用：
 * - 舰船模块定义（从 JSON 加载，数据驱动）。
 * - 用于舰船拼装系统的最小单元（推进、武器、装甲、电子、结构等）。
 *
 * 提供的接口 API：
 * - 公共字段：moduleId、nameKey、descriptionKey、category、slotType、size、mass、cost、prerequisites、stats、spriteCandidates。
 * - 嵌套类：ShipModuleStats（模块属性容器，按 category 区分有效字段）。
 *
 * 使用方式：
 * - 由 ShipAssetRepository 在启动时一次性加载并缓存为只读列表。
 * - 舰船设计器（ShipDesign）通过 moduleId 引用模块定义。
 * - 生产系统根据 ShipDesign 中的模块列表计算总成本、总质量、总属性等。
 *
 * 注意事项：
 * - 本类为只读数据模型，禁止在运行时修改。
 * - 所有数值必须通过 JSON 配置，禁止硬编码。
 * - spriteCandidates 为列表，渲染器可按策略选择（如按科技等级、玩家偏好等）。
 * - JSON 路径示例：
 *   - 本体：assets/ship/modules.json
 *   - Mod：gamedata/mods/{modId}/ship/modules.json（按 mod-order.json 顺序覆盖）
 */

package staraxis.game.ship.def;

import java.util.List;

/**
 * 舰船模块定义（从 JSON 加载，数据驱动）。
 * 用于舰船拼装系统的最小单元（推进、武器、装甲、电子、结构等）。
 */
public class ShipModuleDef {
    public String moduleId;
    public String nameKey;
    public String descriptionKey;
    public String category;
    public String slotType;
    public int size;
    public double mass;
    public List<String> cost;
    public List<String> prerequisites;
    public ShipModuleStats stats;
    public List<String> spriteCandidates;

    /** 挂载点坐标（开发模式编辑后写入，运行时用于特效/武器定位）。 */
    public MountPoints mountPoints;

    /** 纹理路径（开发模式用于绑定纹理与模块，用于“已使用”判定）。 */
    public String texturePath;

    /**
     * ShipModuleStats
     *
     * 模块属性容器（按 category 区分有效字段）。
     * 未使用的字段将被忽略。
     */
    public static class MountPoints {
        /** 引擎特效挂载点（模块纹理中心为 0,0；1px=1单位）。 */
        public Point engineMount;

        /** 开火特效挂载点（模块纹理中心为 0,0；1px=1单位）。 */
        public Point fireMount;

        /** 炮塔中心点（模块纹理中心为 0,0；1px=1单位）。 */
        public Point turretCenter;
    }

    public static class Point {
        public double x;
        public double y;

        public Point() {
        }

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class ShipModuleStats {
        // 推进模块（ENGINE）
        public double thrust; // 推进力
        public double fuelEfficiency; // 燃料效率

        // 武器模块（WEAPON）
        public double damage; // 伤害值
        public double range; // 射程
        public double fireRate; // 射速（次/秒）

        // 装甲模块（ARMOR）
        public double armor; // 装甲值
        public double damageReduction; // 伤害减免

        // 电子模块（ELECTRONIC）
        public double sensorRange; // 传感器范围
        public double ecmStrength; // 电子战强度

        // 结构模块（STRUCTURE）
        public double structureIntegrity; // 结构完整度
        public double repairRate; // 自修速率

        // 通用模块（UTILITY）
        public double powerOutput; // 能源输出
        public double powerCapacity; // 能源容量
    }
}