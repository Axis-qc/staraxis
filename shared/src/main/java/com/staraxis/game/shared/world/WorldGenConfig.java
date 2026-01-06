package com.staraxis.game.shared.world;

import java.io.Serializable;

import com.staraxis.game.shared.world.stellar.surface.MeshResolutionLevel;

/**
 * 世界生成配置 (World generation configuration).
 */
public class WorldGenConfig implements Serializable {

    private String mapSizePresetId;
    private float habitableRatio;
    private String seedText;
    private long seedValue;
    private float starDensity;
    private float planetComplexity;
    private float nebulaRatio;
    private MeshResolutionLevel surfaceMeshResolutionLevel;
    private int aiCount; // Placeholder
    private String techLevelPresetId; // Placeholder

    public WorldGenConfig() {
        this.starDensity = 0.6f;
        this.planetComplexity = 0.5f;
        this.nebulaRatio = 0.2f;
        this.surfaceMeshResolutionLevel = MeshResolutionLevel.LOW;
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

    public float getStarDensity() {
        return starDensity;
    }

    public void setStarDensity(float starDensity) {
        // Clamp to [0, 1]
        this.starDensity = Math.max(0.0f, Math.min(1.0f, starDensity));
    }

    public float getPlanetComplexity() {
        return planetComplexity;
    }

    public void setPlanetComplexity(float planetComplexity) {
        // Clamp to [0, 1]
        this.planetComplexity = Math.max(0.0f, Math.min(1.0f, planetComplexity));
    }

    public float getNebulaRatio() {
        return nebulaRatio;
    }

    public void setNebulaRatio(float nebulaRatio) {
        // Clamp to [0, 1]
        this.nebulaRatio = Math.max(0.0f, Math.min(1.0f, nebulaRatio));
    }

    public MeshResolutionLevel getSurfaceMeshResolutionLevel() {
        return surfaceMeshResolutionLevel;
    }

    public void setSurfaceMeshResolutionLevel(MeshResolutionLevel surfaceMeshResolutionLevel) {
        if (surfaceMeshResolutionLevel == null) {
            throw new IllegalArgumentException("surfaceMeshResolutionLevel 不能为空");
        }
        this.surfaceMeshResolutionLevel = surfaceMeshResolutionLevel;
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
                + ", starDensity=" + starDensity
                + ", planetComplexity=" + planetComplexity
                + ", nebulaRatio=" + nebulaRatio
                + ", surfaceMeshResolutionLevel=" + surfaceMeshResolutionLevel
                + ", aiCount=" + aiCount
                + ", techLevelPresetId='" + techLevelPresetId + '\''
                + '}';
    }
}
