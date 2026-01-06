package com.staraxis.game.shared.world.stellar.orbit;

import java.io.Serializable;

public class Orbit implements Serializable {

    private OrbitCenterRef centerRef;
    private float eccentricity;
    private float phase;
    private float scale;
    private Float inclination;

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
        this.inclination = inclination;
    }
}
