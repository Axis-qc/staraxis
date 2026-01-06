package com.staraxis.game.shared.world.scale;

import java.io.Serializable;

/**
 * 空间范围（Space range）。
 * 
 * 作用（Purpose）：定义空间坐标的范围（最小/最大 X/Y 坐标）。
 * 依赖（Dependencies）：无。
 * 对外接口（Public API）：getMinX/setMinX/getMaxX/setMaxX/getMinY/setMinY/getMaxY/setMaxY。
 */
public class SpaceRange implements Serializable {

    private Float minX;
    private Float maxX;
    private Float minY;
    private Float maxY;

    public SpaceRange() {
    }

    public Float getMinX() {
        return minX;
    }

    public void setMinX(Float minX) {
        if (minX != null && !Float.isFinite(minX)) {
            throw new IllegalArgumentException("minX（最小 X 坐标）必须为有限数值");
        }
        this.minX = minX;
    }

    public Float getMaxX() {
        return maxX;
    }

    public void setMaxX(Float maxX) {
        if (maxX != null && !Float.isFinite(maxX)) {
            throw new IllegalArgumentException("maxX（最大 X 坐标）必须为有限数值");
        }
        if (minX != null && maxX != null && maxX < minX) {
            throw new IllegalArgumentException("maxX（最大 X 坐标）必须 >= minX");
        }
        this.maxX = maxX;
    }

    public Float getMinY() {
        return minY;
    }

    public void setMinY(Float minY) {
        if (minY != null && !Float.isFinite(minY)) {
            throw new IllegalArgumentException("minY（最小 Y 坐标）必须为有限数值");
        }
        this.minY = minY;
    }

    public Float getMaxY() {
        return maxY;
    }

    public void setMaxY(Float maxY) {
        if (maxY != null && !Float.isFinite(maxY)) {
            throw new IllegalArgumentException("maxY（最大 Y 坐标）必须为有限数值");
        }
        if (minY != null && maxY != null && maxY < minY) {
            throw new IllegalArgumentException("maxY（最大 Y 坐标）必须 >= minY");
        }
        this.maxY = maxY;
    }

    @Override
    public String toString() {
        return "SpaceRange{"
                + "minX=" + minX
                + ", maxX=" + maxX
                + ", minY=" + minY
                + ", maxY=" + maxY
                + '}';
    }
}
