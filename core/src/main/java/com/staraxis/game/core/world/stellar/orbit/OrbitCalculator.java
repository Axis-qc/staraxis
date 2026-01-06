package com.staraxis.game.core.world.stellar.orbit;

import java.util.logging.Logger;

import com.badlogic.gdx.math.Vector2;
import com.staraxis.game.shared.world.stellar.orbit.Orbit;

/**
 * 轨道计算器（Orbit calculator）。
 * 
 * 作用（Purpose）：计算行星轨道的位置、周期、真近点角等物理量，支持完整的开普勒轨道参数。
 * 依赖（Dependencies）：Orbit, Vector2 (libGDX)。
 * 对外接口（Public API）：calculatePosition, calculatePeriod, calculateTrueAnomaly。
 */
public class OrbitCalculator {

    private static final Logger LOGGER = Logger.getLogger(OrbitCalculator.class.getName());
    private static final float G = 1.0f; // 引力常数（游戏单位，简化）
    private static final float EPSILON = 1e-6f; // 数值精度阈值
    private static final int MAX_ITERATIONS = 100; // 开普勒方程求解最大迭代次数

    /**
     * 计算轨道位置（使用开普勒方程）。
     * 
     * @param orbit 轨道参数
     * @param centerMass 中心质量
     * @param time 时间（游戏时间单位）
     * @return 位置向量
     */
    public Vector2 calculatePosition(Orbit orbit, float centerMass, float time) {
        // 数值保护：检测无效输入
        if (orbit == null || !Float.isFinite(centerMass) || centerMass <= 0 || !Float.isFinite(time)) {
            LOGGER.warning("无效的轨道计算参数，返回零向量");
            return new Vector2(0, 0);
        }

        try {
            float a = orbit.getEffectiveSemiMajorAxis();
            float e = orbit.getEccentricity();
            
            // 数值保护：检测异常值
            if (!Float.isFinite(a) || a <= 0 || !Float.isFinite(e) || e < 0 || e >= 1) {
                LOGGER.warning("轨道参数异常，使用降级策略");
                return calculatePositionSimplified(orbit, centerMass, time);
            }

            // 计算平均角速度
            float n = calculateMeanMotion(a, centerMass);
            if (!Float.isFinite(n) || n <= 0) {
                LOGGER.warning("平均角速度计算异常，使用降级策略");
                return calculatePositionSimplified(orbit, centerMass, time);
            }

            // 计算平均近点角
            float M = n * time;
            if (orbit.getTrueAnomaly() != null) {
                M = orbit.getTrueAnomaly(); // 如果已提供真近点角，使用它
            } else if (orbit.getPhase() != 0) {
                M += orbit.getPhase(); // 使用相位偏移
            }

            // 求解开普勒方程：M = E - e*sin(E)
            float E = solveKeplerEquation(M, e);
            if (!Float.isFinite(E)) {
                LOGGER.warning("开普勒方程求解失败，使用降级策略");
                return calculatePositionSimplified(orbit, centerMass, time);
            }

            // 计算真近点角
            float nu = calculateTrueAnomaly(E, e);
            if (!Float.isFinite(nu)) {
                LOGGER.warning("真近点角计算异常，使用降级策略");
                return calculatePositionSimplified(orbit, centerMass, time);
            }

            // 计算距离
            float r = a * (1 - e * e) / (1.0f + e * (float) Math.cos(nu));
            if (!Float.isFinite(r) || r <= 0) {
                LOGGER.warning("轨道距离计算异常，使用降级策略");
                return calculatePositionSimplified(orbit, centerMass, time);
            }

            // 计算位置（考虑倾角、升交点经度、近地点幅角）
            float omega = orbit.getArgumentOfPeriapsis() != null ? orbit.getArgumentOfPeriapsis() : 0.0f;
            float Omega = orbit.getLongitudeOfAscendingNode() != null ? orbit.getLongitudeOfAscendingNode() : 0.0f;
            float i = orbit.getInclination() != null ? orbit.getInclination() : 0.0f;

            // 轨道平面坐标
            float x = r * (float) Math.cos(nu + omega);
            float y = r * (float) Math.sin(nu + omega);

            // 转换到空间坐标（考虑倾角和升交点经度）
            float cosI = (float) Math.cos(i);
            // float sinI = (float) Math.sin(i); // 2D 投影中不使用
            float cosOmega = (float) Math.cos(Omega);
            float sinOmega = (float) Math.sin(Omega);

            float xSpace = x * cosOmega - y * sinOmega * cosI;
            float ySpace = x * sinOmega + y * cosOmega * cosI;
            // zSpace = y * sinI; // 2D 投影中不使用 z 轴

            // 返回 2D 投影（忽略 z 轴）
            return new Vector2(xSpace, ySpace);

        } catch (Exception ex) {
            LOGGER.severe("轨道位置计算异常: " + ex.getMessage());
            return calculatePositionSimplified(orbit, centerMass, time);
        }
    }

    /**
     * 降级策略：简化位置计算（圆形轨道近似）。
     */
    private Vector2 calculatePositionSimplified(Orbit orbit, float centerMass, float time) {
        float a = orbit.getEffectiveSemiMajorAxis();
        float phase = orbit.getPhase();
        float angle = phase + time * 0.1f; // 简化的角速度
        
        float x = a * (float) Math.cos(angle);
        float y = a * (float) Math.sin(angle);
        
        return new Vector2(x, y);
    }

    /**
     * 计算轨道周期（开普勒第三定律：T = 2π√(a³/GM)）。
     * 
     * @param orbit 轨道参数
     * @param centerMass 中心质量
     * @return 周期（时间单位）
     */
    public float calculatePeriod(Orbit orbit, float centerMass) {
        // 数值保护
        if (orbit == null || !Float.isFinite(centerMass) || centerMass <= 0) {
            LOGGER.warning("无效的轨道周期计算参数");
            return Float.NaN;
        }

        try {
            float a = orbit.getEffectiveSemiMajorAxis();
            
            if (!Float.isFinite(a) || a <= 0) {
                LOGGER.warning("半长轴异常，无法计算周期");
                return Float.NaN;
            }

            // T = 2π√(a³/GM)
            float period = (float) (2.0 * Math.PI * Math.sqrt(a * a * a / (G * centerMass)));
            
            if (!Float.isFinite(period) || period <= 0) {
                LOGGER.warning("轨道周期计算异常");
                return Float.NaN;
            }
            
            return period;
        } catch (Exception ex) {
            LOGGER.severe("轨道周期计算异常: " + ex.getMessage());
            return Float.NaN;
        }
    }

    /**
     * 计算真近点角（从偏近点角）。
     * 
     * @param E 偏近点角（弧度）
     * @param e 偏心率
     * @return 真近点角（弧度）
     */
    public float calculateTrueAnomaly(float E, float e) {
        // 数值保护
        if (!Float.isFinite(E) || !Float.isFinite(e) || e < 0 || e >= 1) {
            LOGGER.warning("无效的真近点角计算参数");
            return Float.NaN;
        }

        try {
            // tan(ν/2) = √((1+e)/(1-e)) * tan(E/2)
            float tanHalfE = (float) Math.tan(E / 2.0);
            float sqrtTerm = (float) Math.sqrt((1.0 + e) / (1.0 - e));
            float tanHalfNu = sqrtTerm * tanHalfE;
            float nu = 2.0f * (float) Math.atan(tanHalfNu);
            
            // 归一化到 [0, 2π)
            while (nu < 0) nu += 2.0f * (float) Math.PI;
            while (nu >= 2.0 * Math.PI) nu -= 2.0f * (float) Math.PI;
            
            if (!Float.isFinite(nu)) {
                LOGGER.warning("真近点角计算异常");
                return Float.NaN;
            }
            
            return nu;
        } catch (Exception ex) {
            LOGGER.severe("真近点角计算异常: " + ex.getMessage());
            return Float.NaN;
        }
    }

    /**
     * 计算平均角速度（平均运动）。
     */
    private float calculateMeanMotion(float a, float M) {
        // n = √(GM/a³)
        return (float) Math.sqrt(G * M / (a * a * a));
    }

    /**
     * 求解开普勒方程：M = E - e*sin(E)（使用牛顿迭代法）。
     */
    private float solveKeplerEquation(float M, float e) {
        if (e < EPSILON) {
            // 圆形轨道：E ≈ M
            return M;
        }

        // 初始猜测：E₀ = M
        float E = M;
        
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            float f = E - e * (float) Math.sin(E) - M;
            float df = 1.0f - e * (float) Math.cos(E);
            
            if (Math.abs(df) < EPSILON) {
                // 导数过小，无法继续迭代
                break;
            }
            
            float deltaE = f / df;
            E -= deltaE;
            
            if (Math.abs(deltaE) < EPSILON) {
                // 收敛
                break;
            }
            
            // 数值保护：检测异常值
            if (!Float.isFinite(E)) {
                LOGGER.warning("开普勒方程求解出现异常值");
                return Float.NaN;
            }
        }
        
        return E;
    }
}
