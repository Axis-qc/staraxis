package com.staraxis.game.shared.world.scale;

import java.io.Serializable;

/**
 * 星系规模配置（Galaxy scale configuration）。
 * 
 * 作用（Purpose）：定义星系规模配置，支持预设档位或自定义范围两种方式。
 * 依赖（Dependencies）：GalaxyScaleRange, SpaceRange。
 * 对外接口（Public API）：getPresetId/setPresetId/getCustomRange/setCustomRange/getSpaceRange/setSpaceRange。
 */
public class GalaxyScaleConfig implements Serializable {

    private String presetId;
    private GalaxyScaleRange customRange;
    private SpaceRange spaceRange;

    public GalaxyScaleConfig() {
    }

    public String getPresetId() {
        return presetId;
    }

    public void setPresetId(String presetId) {
        if (presetId != null && customRange != null) {
            throw new IllegalArgumentException("presetId 和 customRange 不能同时设置");
        }
        this.presetId = presetId;
    }

    public GalaxyScaleRange getCustomRange() {
        return customRange;
    }

    public void setCustomRange(GalaxyScaleRange customRange) {
        if (customRange != null && presetId != null) {
            throw new IllegalArgumentException("presetId 和 customRange 不能同时设置");
        }
        this.customRange = customRange;
    }

    public SpaceRange getSpaceRange() {
        return spaceRange;
    }

    public void setSpaceRange(SpaceRange spaceRange) {
        this.spaceRange = spaceRange;
    }

    /**
     * 验证配置有效性：presetId 和 customRange 必须且仅能指定一个。
     */
    public void validate() {
        if (presetId == null && customRange == null) {
            throw new IllegalStateException("presetId 和 customRange 必须指定一个");
        }
        if (presetId != null && customRange != null) {
            throw new IllegalStateException("presetId 和 customRange 不能同时设置");
        }
    }

    @Override
    public String toString() {
        return "GalaxyScaleConfig{"
                + "presetId='" + presetId + '\''
                + ", customRange=" + customRange
                + ", spaceRange=" + spaceRange
                + '}';
    }
}
