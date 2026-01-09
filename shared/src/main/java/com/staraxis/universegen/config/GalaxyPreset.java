package com.staraxis.universegen.config;

import com.staraxis.game.shared.net.worldgen.snapshot.HexCoordSnapshot;

import java.util.List;

/**
 * 星系预设（JSON 数据驱动），用于在生成顺序的第 1 步优先占用星区。
 *
 * <p>关键规则（见 015 spec Clarifications）：
 * - 预设之间若发生占用冲突，采用“后来者覆盖”（按 loadOrder/加载顺序）
 */
public class GalaxyPreset {

    /** 预设ID */
    private String presetId;

    /** 加载顺序（数值越大越“后加载”，覆盖优先） */
    private int loadOrder;

    /** 放置方式：fixed-hex / random-hex */
    private String placementType;

    /** fixed-hex 时使用 */
    private List<HexCoordSnapshot> fixedHexCoords;

    /** random-hex 时使用 */
    private int randomCount;

    /** 预设占用星区的类型ID（术语对齐.md，例如 star-system/deep_space/nebula） */
    private String contentTypeId;

    public String getPresetId() {
        return presetId;
    }

    public void setPresetId(String presetId) {
        this.presetId = presetId;
    }

    public int getLoadOrder() {
        return loadOrder;
    }

    public void setLoadOrder(int loadOrder) {
        this.loadOrder = loadOrder;
    }

    public String getPlacementType() {
        return placementType;
    }

    public void setPlacementType(String placementType) {
        this.placementType = placementType;
    }

    public List<HexCoordSnapshot> getFixedHexCoords() {
        return fixedHexCoords;
    }

    public void setFixedHexCoords(List<HexCoordSnapshot> fixedHexCoords) {
        this.fixedHexCoords = fixedHexCoords;
    }

    public int getRandomCount() {
        return randomCount;
    }

    public void setRandomCount(int randomCount) {
        this.randomCount = randomCount;
    }

    public String getContentTypeId() {
        return contentTypeId;
    }

    public void setContentTypeId(String contentTypeId) {
        this.contentTypeId = contentTypeId;
    }
}
