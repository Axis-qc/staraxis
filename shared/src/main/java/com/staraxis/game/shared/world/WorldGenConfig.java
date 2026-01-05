package com.staraxis.game.shared.world;

import java.io.Serializable;

/**
 * 世界生成配置 (World generation configuration).
 */
public class WorldGenConfig implements Serializable {

    private String mapSizePresetId;
    private float habitableRatio;
    private String seedText;
    private long seedValue;
    private int aiCount; // Placeholder
    private String techLevelPresetId; // Placeholder

    public WorldGenConfig() {
    }

    public String getMapSizePresetId() {
        return mapSizePresetId;
    }

    public void setMapSizePresetId(String mapSizePresetId) {
        this.mapSizePresetId = mapSizePresetId;
    }

    public float getHabitableRatio() {
        return habitableRatio;
    }

    public void setHabitableRatio(float habitableRatio) {
        // Clamp to [0, 1]
        this.habitableRatio = Math.max(0.0f, Math.min(1.0f, habitableRatio));
    }

    public String getSeedText() {
        return seedText;
    }

    public void setSeedText(String seedText) {
        this.seedText = seedText;
    }

    public long getSeedValue() {
        return seedValue;
    }

    public void setSeedValue(long seedValue) {
        this.seedValue = seedValue;
    }

    public int getAiCount() {
        return aiCount;
    }

    public void setAiCount(int aiCount) {
        this.aiCount = aiCount;
    }

    public String getTechLevelPresetId() {
        return techLevelPresetId;
    }

    public void setTechLevelPresetId(String techLevelPresetId) {
        this.techLevelPresetId = techLevelPresetId;
    }

    @Override
    public String toString() {
        return "WorldGenConfig{"
                + "mapSizePresetId='" + mapSizePresetId + '\''
                + ", habitableRatio=" + habitableRatio
                + ", seedText='" + seedText + '\''
                + ", seedValue=" + seedValue
                + ", aiCount=" + aiCount
                + ", techLevelPresetId='" + techLevelPresetId + '\''
                + '}';
    }
}
