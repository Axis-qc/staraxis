package com.staraxis.game.shared.world.stellar.orbit;

import java.io.Serializable;

public class OrbitCenterRef implements Serializable {

    private String starId;
    private String barycenterId;

    public OrbitCenterRef() {
    }

    public OrbitCenterRef(String starId, String barycenterId) {
        this.starId = starId;
        this.barycenterId = barycenterId;
        validate();
    }

    public String getStarId() {
        return starId;
    }

    public void setStarId(String starId) {
        this.starId = starId;
        validate();
    }

    public String getBarycenterId() {
        return barycenterId;
    }

    public void setBarycenterId(String barycenterId) {
        this.barycenterId = barycenterId;
        validate();
    }

    private void validate() {
        boolean hasStar = starId != null && !starId.isBlank();
        boolean hasBarycenter = barycenterId != null && !barycenterId.isBlank();
        if (hasStar == hasBarycenter) {
            throw new IllegalArgumentException("OrbitCenterRef 必须且只能设置 starId 或 barycenterId 之一");
        }
    }
}
