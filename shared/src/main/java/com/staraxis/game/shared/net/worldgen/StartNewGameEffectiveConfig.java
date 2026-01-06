package com.staraxis.game.shared.net.worldgen;

/**
 * 新游戏有效配置回填（StartNewGameEffectiveConfig）。
 */
public class StartNewGameEffectiveConfig {

    private String mapSizePresetId;
    private String seedText;
    private long seedValue;
    private float habitableRatio;
    private float starDensity;
    private float planetComplexity;
    private float nebulaRatio;

    public StartNewGameEffectiveConfig() {
    }

    public String getMapSizePresetId() {
        return mapSizePresetId;
    }

    public void setMapSizePresetId(String mapSizePresetId) {
        this.mapSizePresetId = mapSizePresetId;
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

    public float getHabitableRatio() {
        return habitableRatio;
    }

    public void setHabitableRatio(float habitableRatio) {
        this.habitableRatio = habitableRatio;
    }

    public float getStarDensity() {
        return starDensity;
    }

    public void setStarDensity(float starDensity) {
        this.starDensity = starDensity;
    }

    public float getPlanetComplexity() {
        return planetComplexity;
    }

    public void setPlanetComplexity(float planetComplexity) {
        this.planetComplexity = planetComplexity;
    }

    public float getNebulaRatio() {
        return nebulaRatio;
    }

    public void setNebulaRatio(float nebulaRatio) {
        this.nebulaRatio = nebulaRatio;
    }
}
