package com.staraxis.game.core.world.stellar.orbit;

import java.util.List;
import java.util.logging.Logger;

import com.staraxis.game.shared.world.stellar.orbit.Orbit;
import com.staraxis.game.shared.world.stellar.orbit.OrbitStabilityCheckResult;

/**
 * 轨道稳定性检查器（Orbit stability checker）。
 * 
 * 作用（Purpose）：基于物理约束检查轨道稳定性，包括碰撞检测、轨道能量检查等。
 * 依赖（Dependencies）：Orbit, OrbitStabilityCheckResult, OrbitCalculator。
 * 对外接口（Public API）：checkStability, calculateMinDistance, calculateOrbitalEnergy。
 */
public class OrbitStabilityChecker {

    private static final Logger LOGGER = Logger.getLogger(OrbitStabilityChecker.class.getName());
    private static final float G = 1.0f; // 引力常数（与 OrbitCalculator 一致）
    private static final float MIN_DISTANCE_MULTIPLIER = 2.0f; // 最小距离倍数（相对于碰撞半径）

    public OrbitStabilityChecker() {
    }

    /**
     * 检查轨道稳定性。
     * 
     * @param orbit 要检查的轨道
     * @param centerMass 中心质量
     * @param otherOrbits 其他轨道（用于多体系统检查）
     * @param collisionRadius 碰撞半径
     * @return 稳定性检查结果
     */
    public OrbitStabilityCheckResult checkStability(Orbit orbit, float centerMass, 
            List<Orbit> otherOrbits, float collisionRadius) {
        OrbitStabilityCheckResult result = new OrbitStabilityCheckResult(true);

        if (orbit == null) {
            result.setStable(false);
            result.addMessage("轨道参数为空");
            return result;
        }

        if (!Float.isFinite(centerMass) || centerMass <= 0) {
            result.setStable(false);
            result.addMessage("中心质量无效");
            return result;
        }

        if (!Float.isFinite(collisionRadius) || collisionRadius <= 0) {
            result.setStable(false);
            result.addMessage("碰撞半径无效");
            return result;
        }

        try {
            // 检查最小距离
            float minDistance = calculateMinDistance(orbit, otherOrbits, collisionRadius);
            result.setMinDistance(minDistance);
            
            if (minDistance < collisionRadius * MIN_DISTANCE_MULTIPLIER) {
                result.setStable(false);
                result.setCollisionRisk(true);
                result.addMessage(String.format("最小距离 %.2f 小于安全阈值 %.2f", 
                        minDistance, collisionRadius * MIN_DISTANCE_MULTIPLIER));
            }

            // 检查轨道能量
            float orbitalEnergy = calculateOrbitalEnergy(orbit, centerMass);
            result.setOrbitalEnergy(orbitalEnergy);
            
            if (!Float.isFinite(orbitalEnergy)) {
                result.setStable(false);
                result.addMessage("轨道能量计算异常");
            } else if (orbitalEnergy >= 0) {
                result.setStable(false);
                result.setEscapeRisk(true);
                result.addMessage("轨道能量 >= 0，行星可能逃逸");
            }

            // 检查轨道参数有效性
            float a = orbit.getEffectiveSemiMajorAxis();
            float e = orbit.getEccentricity();
            
            if (!Float.isFinite(a) || a <= 0) {
                result.setStable(false);
                result.addMessage("半长轴无效");
            }
            
            if (!Float.isFinite(e) || e < 0 || e >= 1) {
                result.setStable(false);
                result.addMessage("偏心率无效");
            }

        } catch (Exception e) {
            LOGGER.severe("轨道稳定性检查异常: " + e.getMessage());
            result.setStable(false);
            result.addMessage("稳定性检查过程异常: " + e.getMessage());
        }

        return result;
    }

    /**
     * 计算最小距离（用于碰撞检测）。
     */
    public float calculateMinDistance(Orbit orbit, List<Orbit> otherOrbits, float collisionRadius) {
        if (orbit == null || !Float.isFinite(collisionRadius) || collisionRadius <= 0) {
            return Float.NaN;
        }

        float a = orbit.getEffectiveSemiMajorAxis();
        float e = orbit.getEccentricity();
        
        // 计算近地点距离（最小距离）
        float periapsis = a * (1.0f - e);
        
        if (otherOrbits != null && !otherOrbits.isEmpty()) {
            // 多体系统：检查与其他轨道的最小距离
            float minDist = Float.MAX_VALUE;
            
            for (Orbit other : otherOrbits) {
                if (other == null || other == orbit) {
                    continue;
                }
                
                float otherA = other.getEffectiveSemiMajorAxis();
                float otherE = other.getEccentricity();
                float otherPeriapsis = otherA * (1.0f - otherE);
                
                // 简化：使用近地点距离的差值作为最小距离
                float distance = Math.abs(periapsis - otherPeriapsis);
                minDist = Math.min(minDist, distance);
            }
            
            return Math.min(periapsis, minDist);
        }
        
        return periapsis;
    }

    /**
     * 计算轨道能量。
     * 
     * @param orbit 轨道参数
     * @param centerMass 中心质量
     * @return 轨道能量（负值表示束缚轨道，正值表示逃逸轨道）
     */
    public float calculateOrbitalEnergy(Orbit orbit, float centerMass) {
        if (orbit == null || !Float.isFinite(centerMass) || centerMass <= 0) {
            return Float.NaN;
        }

        try {
            float a = orbit.getEffectiveSemiMajorAxis();
            
            if (!Float.isFinite(a) || a <= 0) {
                return Float.NaN;
            }

            // 轨道能量：E = -GM/(2a)
            // 负值表示束缚轨道，正值表示逃逸轨道
            float energy = -G * centerMass / (2.0f * a);
            
            if (!Float.isFinite(energy)) {
                return Float.NaN;
            }
            
            return energy;
        } catch (Exception e) {
            LOGGER.warning("轨道能量计算异常: " + e.getMessage());
            return Float.NaN;
        }
    }
}
