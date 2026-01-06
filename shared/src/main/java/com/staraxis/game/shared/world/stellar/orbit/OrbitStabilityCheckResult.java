package com.staraxis.game.shared.world.stellar.orbit;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 轨道稳定性检查结果（Orbit stability check result）。
 * 
 * 作用（Purpose）：存储轨道稳定性检查的结果（是否稳定、最小距离、轨道能量、碰撞风险、逃逸风险、检查消息）。
 * 依赖（Dependencies）：无。
 * 对外接口（Public API）：isStable/setStable/getMinDistance/setMinDistance/getOrbitalEnergy/setOrbitalEnergy/getCollisionRisk/setCollisionRisk/getEscapeRisk/setEscapeRisk/getMessages/addMessage。
 */
public class OrbitStabilityCheckResult implements Serializable {

    private boolean isStable;
    private Float minDistance;
    private Float orbitalEnergy;
    private Boolean collisionRisk;
    private Boolean escapeRisk;
    private List<String> messages;

    public OrbitStabilityCheckResult() {
        this.isStable = true;
        this.messages = new ArrayList<>();
    }

    public OrbitStabilityCheckResult(boolean isStable) {
        this.isStable = isStable;
        this.messages = new ArrayList<>();
    }

    public boolean isStable() {
        return isStable;
    }

    public void setStable(boolean stable) {
        isStable = stable;
    }

    public Float getMinDistance() {
        return minDistance;
    }

    public void setMinDistance(Float minDistance) {
        if (minDistance != null && (!Float.isFinite(minDistance) || minDistance <= 0.0f)) {
            throw new IllegalArgumentException("minDistance（最小距离）必须 > 0 且为有限数值");
        }
        this.minDistance = minDistance;
    }

    public Float getOrbitalEnergy() {
        return orbitalEnergy;
    }

    public void setOrbitalEnergy(Float orbitalEnergy) {
        if (orbitalEnergy != null && !Float.isFinite(orbitalEnergy)) {
            throw new IllegalArgumentException("orbitalEnergy（轨道能量）必须为有限数值");
        }
        this.orbitalEnergy = orbitalEnergy;
    }

    public Boolean getCollisionRisk() {
        return collisionRisk;
    }

    public void setCollisionRisk(Boolean collisionRisk) {
        this.collisionRisk = collisionRisk;
    }

    public Boolean getEscapeRisk() {
        return escapeRisk;
    }

    public void setEscapeRisk(Boolean escapeRisk) {
        this.escapeRisk = escapeRisk;
    }

    public List<String> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public void addMessage(String message) {
        if (message != null && !message.trim().isEmpty()) {
            messages.add(message.trim());
        }
    }

    public void addMessages(List<String> messages) {
        if (messages != null) {
            for (String message : messages) {
                addMessage(message);
            }
        }
    }

    public boolean hasMessages() {
        return !messages.isEmpty();
    }

    @Override
    public String toString() {
        return "OrbitStabilityCheckResult{"
                + "isStable=" + isStable
                + ", minDistance=" + minDistance
                + ", orbitalEnergy=" + orbitalEnergy
                + ", collisionRisk=" + collisionRisk
                + ", escapeRisk=" + escapeRisk
                + ", messages=" + messages.size()
                + '}';
    }
}
