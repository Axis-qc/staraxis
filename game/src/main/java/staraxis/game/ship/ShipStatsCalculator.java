/*
 * ShipStatsCalculator
 *
 * 文件作用：
 * - 舰船属性计算器，从 ShipBody.components 解析模块定义，聚合计算移动/机动参数。
 * - ShipBody 自身不携带任何硬编码机动数值，所有参数通过此计算器按需获取。
 *
 * 使用方式：
 * - ShipMovementSystem, FTLTravelSystem 等调用
 *   computeMovementStats(ship, moduleResolver, externalModifiers) 获取计算值。
 *
 * 外部加成机制：
 * - externalModifiers 是一个 Map<String, Double>，key 为加成属性名，value 为乘数因子。
 * - 例如 {"systemSpeed": 1.2} 表示将 systemSpeed 乘以 1.2。
 * - 当前无科技/国家加成系统时传 null。
 *
 * 注意事项：
 * - 纯计算，不修改任何状态。
 * - 当 ship.components 为空或 moduleResolver 为 null 时，返回合理默认值
 *   （开发阶段舰船无组件时仍可正常移动）。
 */

package staraxis.game.ship;

import java.util.Map;
import java.util.function.Function;

import staraxis.game.ship.def.ShipModuleDef;

/**
 * ShipStatsCalculator（舰船属性计算器）。
 *
 * 从 ShipBody.components 解析 ShipModuleDef，聚合计算移动/机动参数。
 */
public final class ShipStatsCalculator {

    /** 组件不存在时的默认值，保持游戏可运行。 */
    private static final double DEFAULT_SYSTEM_SPEED = 20.0;
    private static final double DEFAULT_FUEL_CONSUMPTION = 5.0;
    private static final double DEFAULT_MAX_FUEL = 100.0;
    private static final double DEFAULT_ACCELERATION = 5.0;

    private ShipStatsCalculator() {
    }

    /**
     * 计算舰船移动相关属性。
     *
     * @param ship              舰船
     * @param moduleResolver    模块ID->ShipModuleDef 解析函数（null = 无组件数据源）
     * @param externalModifiers 外部加成（null = 无加成）
     * @return 计算后的移动属性
     */
    public static ShipMovementStats computeMovementStats(
            ShipBody ship,
            Function<String, ShipModuleDef> moduleResolver,
            Map<String, Double> externalModifiers) {

        if (ship == null) {
            return ShipMovementStats.ZERO;
        }

        // ── 1. 从组件聚合基础值 ──
        double totalThrust = 0;
        double totalFuelEfficiency = 0;
        double totalFuelCapacity = 0;
        int engineCount = 0;
        int warpCount = 0;
        boolean hasAdvancedEngine = false;

        if (moduleResolver != null && ship.components != null) {
            for (ShipComponent comp : ship.components) {
                ShipModuleDef def = moduleResolver.apply(comp.moduleId);
                if (def == null || def.stats == null) continue;

                String cat = def.category;
                if ("ENGINE".equals(cat)) {
                    totalThrust += def.stats.thrust;
                    totalFuelEfficiency += def.stats.fuelEfficiency;
                    engineCount++;
                    if (def.stats.thrust > 100) {
                        hasAdvancedEngine = true; // 高推力引擎视为聚变/反物质级
                    }
                } else if ("WARP".equals(cat)) {
                    totalThrust += def.stats.thrust;
                    warpCount++;
                    hasAdvancedEngine = true;
                } else if ("FUEL_TANK".equals(cat) || "STRUCTURE".equals(cat)) {
                    totalFuelCapacity += def.stats.structureIntegrity;
                }
            }
        }

        // ── 2. 计算派生值 ──
        boolean hasComponents = engineCount > 0 || warpCount > 0;

        double systemSpeed;
        double galaxySpeed;
        boolean canFreeMove;
        double fuelConsumption;
        double fuelPerJump;
        double maxFuel;
        double maxSpeed;
        double baseAcceleration;

        if (!hasComponents) {
            // 无组件 → 开发默认值
            systemSpeed = DEFAULT_SYSTEM_SPEED;
            galaxySpeed = 0;
            // 检测测试标记：没有组件但有 TEST_FREE_MOVE flag 也允许直飞
            canFreeMove = ship.customFlags != null && ship.customFlags.contains("TEST_FREE_MOVE");
            fuelConsumption = DEFAULT_FUEL_CONSUMPTION;
            fuelPerJump = 0;
            maxFuel = DEFAULT_MAX_FUEL;
            maxSpeed = DEFAULT_SYSTEM_SPEED;
            baseAcceleration = DEFAULT_ACCELERATION;
        } else {
            // 系统速度：引擎推力 × 换算系数
            double avgThrust = engineCount > 0 ? totalThrust / engineCount : 0;
            systemSpeed = avgThrust * 100.0;
            if (systemSpeed < 1) systemSpeed = 1;

            // 星系速度：WARP 引擎提供 FTL 能力
            galaxySpeed = warpCount > 0 ? totalThrust : 0;

            // 自由移动：有高级引擎或 WARP 引擎
            canFreeMove = hasAdvancedEngine || warpCount > 0;

            // 燃料消耗率：基础消耗 / 平均燃料效率
            double avgEfficiency = engineCount > 0 ? totalFuelEfficiency / engineCount : 1.0;
            fuelConsumption = avgEfficiency > 0 ? DEFAULT_FUEL_CONSUMPTION / avgEfficiency : DEFAULT_FUEL_CONSUMPTION;

            // 跳跃消耗：有 WARP 引擎时每次跳跃消耗固定燃料
            fuelPerJump = warpCount > 0 ? 50.0 : 0;

            // 最大燃料容量
            maxFuel = totalFuelCapacity > 0 ? totalFuelCapacity : DEFAULT_MAX_FUEL;

            maxSpeed = systemSpeed;
            baseAcceleration = avgThrust * 10.0;
            if (baseAcceleration < 0.1) baseAcceleration = DEFAULT_ACCELERATION;
        }

        // ── 3. 应用外部加成 ──
        if (externalModifiers != null && !externalModifiers.isEmpty()) {
            systemSpeed = applyMultiplier(systemSpeed, externalModifiers, "systemSpeed");
            galaxySpeed = applyMultiplier(galaxySpeed, externalModifiers, "galaxySpeed");
            fuelConsumption = applyMultiplier(fuelConsumption, externalModifiers, "fuelConsumption");
            fuelPerJump = applyMultiplier(fuelPerJump, externalModifiers, "fuelPerJump");
            maxFuel = applyMultiplier(maxFuel, externalModifiers, "maxFuel");
            maxSpeed = applyMultiplier(maxSpeed, externalModifiers, "maxSpeed");
            baseAcceleration = applyMultiplier(baseAcceleration, externalModifiers, "acceleration");
        }

        return new ShipMovementStats(
                Math.max(0, systemSpeed),
                Math.max(0, galaxySpeed),
                canFreeMove,
                Math.max(0, fuelConsumption),
                Math.max(0, fuelPerJump),
                Math.max(0, maxFuel),
                Math.max(0, maxSpeed),
                Math.max(0, baseAcceleration)
        );
    }

    private static double applyMultiplier(double value, Map<String, Double> mods, String key) {
        Double mod = mods.get(key);
        return mod != null ? value * mod : value;
    }

    // ── 结果记录 ──

    /**
     * ShipMovementStats（舰船移动属性计算结果）。
     *
     * 所有值均为 >= 0 的计算结果，由 ShipStatsCalculator.computeMovementStats()
     * 返回给调用方使用。调用方不直接读 ShipBody 字段。
     */
    public record ShipMovementStats(
            /** 星系内移动速度（GU/游戏秒）。0 = 无法自行推进。 */
            double systemSpeedGUps,
            /** 跨星系移动速度（GU/游戏秒）。0 = 不能跨星系。 */
            double galaxySpeedGUps,
            /** 是否可直飞（true = 不用轨道机动，直接飞向目标）。 */
            boolean canFreeMove,
            /** 主动推进时燃料消耗率（t/游戏分钟）。 */
            double fuelConsumptionPerMin,
            /** 单次曲速跳跃消耗（t）。0 = 不能跳跃。 */
            double fuelPerJump,
            /** 最大推进剂容量（t），用于 UI 显示百分比。 */
            double maxFuelMass,
            /** 最大直飞速度（GU/游戏秒），供 ShipFullMovementSystem 使用。 */
            double maxSpeed,
            /** 基础加速度（GU/游戏秒²），供 ShipFullMovementSystem 使用。 */
            double baseAcceleration
    ) {
        /** 全零的默认值（用于 null ship 兜底）。 */
        public static final ShipMovementStats ZERO = new ShipMovementStats(0, 0, false, 0, 0, 0, 0, 0);
    }
}
