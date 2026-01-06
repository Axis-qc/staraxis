package com.staraxis.game.shared.world.scale;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 星系与区块协调结果（Galaxy-block coordination result）。
 * 
 * 作用（Purpose）：存储星系与区块规模协调的结果（是否调整、原始密度、调整后密度、警告信息）。
 * 依赖（Dependencies）：无。
 * 对外接口（Public API）：isAdjusted/setAdjusted/getOriginalDensity/setOriginalDensity/getAdjustedDensity/setAdjustedDensity/getWarnings/addWarning。
 */
public class GalaxyBlockCoordinationResult implements Serializable {

    private boolean isAdjusted;
    private Float originalDensity;
    private Float adjustedDensity;
    private List<String> warnings;

    public GalaxyBlockCoordinationResult() {
        this.isAdjusted = false;
        this.warnings = new ArrayList<>();
    }

    public boolean isAdjusted() {
        return isAdjusted;
    }

    public void setAdjusted(boolean adjusted) {
        isAdjusted = adjusted;
    }

    public Float getOriginalDensity() {
        return originalDensity;
    }

    public void setOriginalDensity(Float originalDensity) {
        if (originalDensity != null && !Float.isFinite(originalDensity)) {
            throw new IllegalArgumentException("originalDensity（原始密度）必须为有限数值");
        }
        this.originalDensity = originalDensity;
    }

    public Float getAdjustedDensity() {
        return adjustedDensity;
    }

    public void setAdjustedDensity(Float adjustedDensity) {
        if (adjustedDensity != null && !Float.isFinite(adjustedDensity)) {
            throw new IllegalArgumentException("adjustedDensity（调整后密度）必须为有限数值");
        }
        this.adjustedDensity = adjustedDensity;
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public void addWarning(String warning) {
        if (warning != null && !warning.trim().isEmpty()) {
            warnings.add(warning.trim());
        }
    }

    public void addWarnings(List<String> warnings) {
        if (warnings != null) {
            for (String warning : warnings) {
                addWarning(warning);
            }
        }
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    @Override
    public String toString() {
        return "GalaxyBlockCoordinationResult{"
                + "isAdjusted=" + isAdjusted
                + ", originalDensity=" + originalDensity
                + ", adjustedDensity=" + adjustedDensity
                + ", warnings=" + warnings.size()
                + '}';
    }
}
