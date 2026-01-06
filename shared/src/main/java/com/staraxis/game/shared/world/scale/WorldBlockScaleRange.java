package com.staraxis.game.shared.world.scale;

import java.io.Serializable;

/**
 * 世界区块规模范围（World block scale range）。
 * 
 * 作用（Purpose）：定义世界区块规模的范围（宽度、高度、区块大小）。
 * 依赖（Dependencies）：无。
 * 对外接口（Public
 * API）：getWidth/setWidth/getHeight/setHeight/getBlockSize/setBlockSize。
 */
public class WorldBlockScaleRange implements Serializable {

    private int width;
    private int height;
    private Float blockSize;

    public WorldBlockScaleRange() {
    }

    public WorldBlockScaleRange(int width, int height) {
        setWidth(width);
        setHeight(height);
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        if (width < 1) {
            throw new IllegalArgumentException("width（区块宽度）必须 >= 1");
        }
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        if (height < 1) {
            throw new IllegalArgumentException("height（区块高度）必须 >= 1");
        }
        this.height = height;
    }

    public Float getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(Float blockSize) {
        if (blockSize != null && (!Float.isFinite(blockSize) || blockSize <= 0.0f)) {
            throw new IllegalArgumentException("blockSize（区块大小）必须 > 0 且为有限数值");
        }
        this.blockSize = blockSize;
    }

    @Override
    public String toString() {
        return "WorldBlockScaleRange{"
                + "width=" + width
                + ", height=" + height
                + ", blockSize=" + blockSize
                + '}';
    }
}
