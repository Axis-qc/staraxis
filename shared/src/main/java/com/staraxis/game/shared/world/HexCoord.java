package com.staraxis.game.shared.world;

import java.util.Objects;

/**
 * 六边形立方体坐标 (Cubic coordinates). 约束：x + y + z 必须为 0。
 */
public final class HexCoord {

    private final int x;
    private final int y;
    private final int z;

    public HexCoord(int x, int y, int z) {
        if (x + y + z != 0) {
            throw new IllegalArgumentException("Invalid cubic coordinate: x + y + z must equal 0");
        }
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static HexCoord of(int x, int y, int z) {
        return new HexCoord(x, y, z);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public HexCoord add(HexCoord other) {
        return of(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HexCoord)) {
            return false;
        }
        HexCoord hexCoord = (HexCoord) o;
        return x == hexCoord.x && y == hexCoord.y && z == hexCoord.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "HexCoord(" + x + ", " + y + ", " + z + ")";
    }
}
