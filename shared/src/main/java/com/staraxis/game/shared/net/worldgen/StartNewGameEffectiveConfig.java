package com.staraxis.game.shared.net.worldgen;

/**
 * 服务端对 StartNewGameRequest 的规范化/生效配置回显。
 */
public class StartNewGameEffectiveConfig {

    private String seedText;
    private long seedValue;
    private String mapSizePresetId;

    /** 恒星系星区（galaxy）比例 */
    private float galaxyRatio;
    /** 星云比例 */
    private float nebulaRatio;
    /** 深空比例 */
    private float deepSpaceRatio;

    /** 行星复杂度（保留给后续实现） */
    private float planetComplexity;

    public StartNewGameEffectiveConfig() {
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

    public String getMapSizePresetId() {
        return mapSizePresetId;
    }

    public void setMapSizePresetId(String mapSizePresetId) {
        this.mapSizePresetId = mapSizePresetId;
    }

    public float getGalaxyRatio() {
        return galaxyRatio;
    }

    public void setGalaxyRatio(float galaxyRatio) {
        this.galaxyRatio = galaxyRatio;
    }

    public float getNebulaRatio() {
        return nebulaRatio;
    }

    public void setNebulaRatio(float nebulaRatio) {
        this.nebulaRatio = nebulaRatio;
    }

    public float getDeepSpaceRatio() {
        return deepSpaceRatio;
    }

    public void setDeepSpaceRatio(float deepSpaceRatio) {
        this.deepSpaceRatio = deepSpaceRatio;
    }

    public float getPlanetComplexity() {
        return planetComplexity;
    }

    public void setPlanetComplexity(float planetComplexity) {
        this.planetComplexity = planetComplexity;
    }
}
