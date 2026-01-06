package com.staraxis.game.shared.world.scale;

import java.io.Serializable;

/**
 * 规模预设档位定义（Scale preset definition）。
 * 
 * 作用（Purpose）：定义规模预设档位（如 small/medium/large），包含显示名和规模范围。
 * 依赖（Dependencies）：GalaxyScaleRange, SpaceRange。
 * 对外接口（Public API）：getId/setId/getDisplayName/setDisplayName/getStarSystemRange/setStarSystemRange/getSpaceRange/setSpaceRange。
 */
public class ScalePreset implements Serializable {

    private String id;
    private String displayName;
    private GalaxyScaleRange starSystemRange;
    private SpaceRange spaceRange;

    public ScalePreset() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id（预设档位 ID）不能为空");
        }
        this.id = id.trim();
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public GalaxyScaleRange getStarSystemRange() {
        return starSystemRange;
    }

    public void setStarSystemRange(GalaxyScaleRange starSystemRange) {
        if (starSystemRange == null) {
            throw new IllegalArgumentException("starSystemRange（恒星系统数量范围）不能为空");
        }
        this.starSystemRange = starSystemRange;
    }

    public SpaceRange getSpaceRange() {
        return spaceRange;
    }

    public void setSpaceRange(SpaceRange spaceRange) {
        this.spaceRange = spaceRange;
    }

    @Override
    public String toString() {
        return "ScalePreset{"
                + "id='" + id + '\''
                + ", displayName='" + displayName + '\''
                + ", starSystemRange=" + starSystemRange
                + ", spaceRange=" + spaceRange
                + '}';
    }
}
