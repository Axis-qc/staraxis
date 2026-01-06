package com.staraxis.game.shared.net.worldgen;

/**
 * 新游戏世界生成请求（StartNewGameRequest）。
 */
public class StartNewGameRequest {

    private String seedText;
    private String mapSizePresetId;
    private float habitableRatio;
    private float starDensity;
    private float planetComplexity;
    private float nebulaRatio;

    public StartNewGameRequest() {
    }

    public String getSeedText() {
        return seedText;
    }

    public void setSeedText(String seedText) {
        this.seedText = seedText;
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
