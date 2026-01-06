package com.staraxis.game.shared.world.scale;

import java.io.Serializable;

/**
 * 世界区块规模配置（World block scale configuration）。
 * 
 * 作用（Purpose）：定义世界区块规模配置，支持预设档位或自定义范围两种方式。
 * 依赖（Dependencies）：WorldBlockScaleRange。
 * 对外接口（Public API）：getPresetId/setPresetId/getCustomRange/setCustomRange。
 */
public class WorldBlockScaleConfig implements Serializable {

    private String presetId;
    private WorldBlockScaleRange customRange;

    public WorldBlockScaleConfig() {
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

    public WorldBlockScaleRange getCustomRange() {
        return customRange;
    }

    public void setCustomRange(WorldBlockScaleRange customRange) {
        if (customRange != null && presetId != null) {
            throw new IllegalArgumentException("presetId 和 customRange 不能同时设置");
        }
        this.customRange = customRange;
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
        return "WorldBlockScaleConfig{"
                + "presetId='" + presetId + '\''
                + ", customRange=" + customRange
                + '}';
    }
}
