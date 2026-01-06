package com.staraxis.game.shared.world.stellar.orbit;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.staraxis.game.shared.model.Vector2;

public class OrbitPath implements Serializable {

    private String orbitId;
    private OrbitPrecisionLevel precisionLevel;
    private List<Vector2> samples;

    public OrbitPath() {
        this.samples = new ArrayList<>();
    }

    public OrbitPath(String orbitId, OrbitPrecisionLevel precisionLevel, List<Vector2> samples) {
        this.orbitId = orbitId;
        setPrecisionLevel(precisionLevel);
        setSamples(samples);
    }

    public String getOrbitId() {
        return orbitId;
    }

    public void setOrbitId(String orbitId) {
        this.orbitId = orbitId;
    }

    public OrbitPrecisionLevel getPrecisionLevel() {
        return precisionLevel;
    }

    public void setPrecisionLevel(OrbitPrecisionLevel precisionLevel) {
        if (precisionLevel == null) {
            throw new IllegalArgumentException("precisionLevel 不能为空");
        }
        this.precisionLevel = precisionLevel;
    }

    public List<Vector2> getSamples() {
        return Collections.unmodifiableList(samples);
    }

    public void setSamples(List<Vector2> samples) {
        if (samples == null) {
            this.samples = new ArrayList<>();
            return;
        }
        this.samples = new ArrayList<>(samples);
    }
}
