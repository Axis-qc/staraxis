package com.staraxis.game.shared.world.scale;

import java.io.Serializable;

/**
 * 区块规模限制（Block scale limits）。
 * 
 * 作用（Purpose）：定义区块规模的验证限制（最小/最大宽度和高度、最大生成时间）。
 * 依赖（Dependencies）：无。
 * 对外接口（Public
 * API）：getMinWidth/setMinWidth/getMaxWidth/setMaxWidth/getMinHeight/setMinHeight/getMaxHeight/setMaxHeight/getMaxGenerationTimeMs/setMaxGenerationTimeMs。
 */
public class BlockScaleLimits implements Serializable {

    private int minWidth;
    private int maxWidth;
    private int minHeight;
    private int maxHeight;
    private Long maxGenerationTimeMs;

    public BlockScaleLimits() {
    }

    public int getMinWidth() {
        return minWidth;
    }

    public void setMinWidth(int minWidth) {
        if (minWidth < 1) {
            throw new IllegalArgumentException("minWidth（最小宽度）必须 >= 1");
        }
        this.minWidth = minWidth;
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(int maxWidth) {
        if (maxWidth < minWidth) {
            throw new IllegalArgumentException("maxWidth（最大宽度）必须 >= minWidth");
        }
        this.maxWidth = maxWidth;
    }

    public int getMinHeight() {
        return minHeight;
    }

    public void setMinHeight(int minHeight) {
        if (minHeight < 1) {
            throw new IllegalArgumentException("minHeight（最小高度）必须 >= 1");
        }
        this.minHeight = minHeight;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public void setMaxHeight(int maxHeight) {
        if (maxHeight < minHeight) {
            throw new IllegalArgumentException("maxHeight（最大高度）必须 >= minHeight");
        }
        this.maxHeight = maxHeight;
    }

    public Long getMaxGenerationTimeMs() {
        return maxGenerationTimeMs;
    }

    public void setMaxGenerationTimeMs(Long maxGenerationTimeMs) {
        if (maxGenerationTimeMs != null && maxGenerationTimeMs <= 0) {
            throw new IllegalArgumentException("maxGenerationTimeMs（最大生成时间）必须 > 0");
        }
        this.maxGenerationTimeMs = maxGenerationTimeMs;
    }

    @Override
    public String toString() {
        return "BlockScaleLimits{"
                + "minWidth=" + minWidth
                + ", maxWidth=" + maxWidth
                + ", minHeight=" + minHeight
                + ", maxHeight=" + maxHeight
                + ", maxGenerationTimeMs=" + maxGenerationTimeMs
                + '}';
    }
}
