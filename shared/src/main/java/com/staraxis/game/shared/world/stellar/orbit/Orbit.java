package com.staraxis.game.shared.world.stellar.orbit;

import java.io.Serializable;

/**
 * 轨道（Orbit）。
 * 
 * 作用（Purpose）：定义行星（或恒星）轨道的抽象描述，支持完整的开普勒轨道参数。
 * 依赖（Dependencies）：OrbitCenterRef。
 * 对外接口（Public API）：完整的轨道参数 getter/setter 方法。
 */
public class Orbit implements Serializable {

    private OrbitCenterRef centerRef;
    private float eccentricity;
    private float phase; // 相位（用于简化计算，向后兼容）
    private float scale; // 轨道尺度（用于向后兼容，等价于 semiMajorAxis）
    private Float inclination; // 倾角（弧度）
    
    // 完整的开普勒轨道参数（新增）
    private Float semiMajorAxis; // 半长轴（单位：游戏单位）
    private Float longitudeOfAscendingNode; // 升交点经度（弧度）
    private Float argumentOfPeriapsis; // 近地点幅角（弧度）
    private Float trueAnomaly; // 真近点角（弧度）

    public Orbit() {
    }

    public Orbit(OrbitCenterRef centerRef, float eccentricity, float phase, float scale, Float inclination) {
        setCenterRef(centerRef);
        setEccentricity(eccentricity);
        setPhase(phase);
        setScale(scale);
        setInclination(inclination);
    }

    public OrbitCenterRef getCenterRef() {
        return centerRef;
    }

    public void setCenterRef(OrbitCenterRef centerRef) {
        if (centerRef == null) {
            throw new IllegalArgumentException("centerRef（轨道中心）不能为空");
        }
        this.centerRef = centerRef;
    }

    public float getEccentricity() {
        return eccentricity;
    }

    public void setEccentricity(float eccentricity) {
        if (!Float.isFinite(eccentricity) || eccentricity < 0.0f || eccentricity >= 1.0f) {
            throw new IllegalArgumentException("eccentricity（偏心率）必须满足 0 <= e < 1 且为有限数值");
        }
        this.eccentricity = eccentricity;
    }

    public float getPhase() {
        return phase;
    }

    public void setPhase(float phase) {
        if (!Float.isFinite(phase)) {
            throw new IllegalArgumentException("phase（相位）必须为有限数值");
        }
        this.phase = phase;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0.0f) {
            throw new IllegalArgumentException("scale（轨道尺度）必须 > 0 且为有限数值");
        }
        this.scale = scale;
    }

    public Float getInclination() {
        return inclination;
    }

    public void setInclination(Float inclination) {
        if (inclination != null && !Float.isFinite(inclination)) {
            throw new IllegalArgumentException("inclination（倾角）必须为有限数值");
        }
        // 验证倾角范围：-π/2 <= i <= π/2
        if (inclination != null && (inclination < -Math.PI / 2.0f || inclination > Math.PI / 2.0f)) {
            throw new IllegalArgumentException("inclination（倾角）必须在 [-π/2, π/2] 范围内");
        }
        this.inclination = inclination;
    }

    public Float getSemiMajorAxis() {
        return semiMajorAxis;
    }

    public void setSemiMajorAxis(Float semiMajorAxis) {
        if (semiMajorAxis != null && (!Float.isFinite(semiMajorAxis) || semiMajorAxis <= 0.0f)) {
            throw new IllegalArgumentException("semiMajorAxis（半长轴）必须 > 0 且为有限数值");
        }
        this.semiMajorAxis = semiMajorAxis;
    }

    public Float getLongitudeOfAscendingNode() {
        return longitudeOfAscendingNode;
    }

    public void setLongitudeOfAscendingNode(Float longitudeOfAscendingNode) {
        if (longitudeOfAscendingNode != null && !Float.isFinite(longitudeOfAscendingNode)) {
            throw new IllegalArgumentException("longitudeOfAscendingNode（升交点经度）必须为有限数值");
        }
        // 验证范围：0 <= Ω < 2π
        if (longitudeOfAscendingNode != null && (longitudeOfAscendingNode < 0.0f || longitudeOfAscendingNode >= 2.0f * (float) Math.PI)) {
            throw new IllegalArgumentException("longitudeOfAscendingNode（升交点经度）必须在 [0, 2π) 范围内");
        }
        this.longitudeOfAscendingNode = longitudeOfAscendingNode;
    }

    public Float getArgumentOfPeriapsis() {
        return argumentOfPeriapsis;
    }

    public void setArgumentOfPeriapsis(Float argumentOfPeriapsis) {
        if (argumentOfPeriapsis != null && !Float.isFinite(argumentOfPeriapsis)) {
            throw new IllegalArgumentException("argumentOfPeriapsis（近地点幅角）必须为有限数值");
        }
        // 验证范围：0 <= ω < 2π
        if (argumentOfPeriapsis != null && (argumentOfPeriapsis < 0.0f || argumentOfPeriapsis >= 2.0f * (float) Math.PI)) {
            throw new IllegalArgumentException("argumentOfPeriapsis（近地点幅角）必须在 [0, 2π) 范围内");
        }
        this.argumentOfPeriapsis = argumentOfPeriapsis;
    }

    public Float getTrueAnomaly() {
        return trueAnomaly;
    }

    public void setTrueAnomaly(Float trueAnomaly) {
        if (trueAnomaly != null && !Float.isFinite(trueAnomaly)) {
            throw new IllegalArgumentException("trueAnomaly（真近点角）必须为有限数值");
        }
        // 验证范围：0 <= ν < 2π
        if (trueAnomaly != null && (trueAnomaly < 0.0f || trueAnomaly >= 2.0f * (float) Math.PI)) {
            throw new IllegalArgumentException("trueAnomaly（真近点角）必须在 [0, 2π) 范围内");
        }
        this.trueAnomaly = trueAnomaly;
    }

    /**
     * 获取有效的半长轴值（优先使用 semiMajorAxis，如果为空则使用 scale 作为后备）。
     * 
     * @return 半长轴值
     */
    public float getEffectiveSemiMajorAxis() {
        return semiMajorAxis != null ? semiMajorAxis : scale;
    }
}
