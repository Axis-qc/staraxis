package com.staraxis.game.shared.net.worldgen;

/**
 * 新游戏世界生成请求（StartNewGameRequest）。
 * 
 * 013 特性：采用三滑条比例（恒星系/星云/深空），破坏性替换旧字段。
 */
public class StartNewGameRequest {

    private String seedText;
    private String mapSizePresetId;

    /** 恒星系星区（galaxy）比例 */
    private float galaxyRatio;
    /** 星云比例 */
    private float nebulaRatio;
    /** 深空比例 */
    private float deepSpaceRatio;

    /** 行星复杂度（预留） */
    private float planetComplexity;

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
