/**
 * 文件作用：提供一个双精度浮点二维向量，用于精确的位置和速度计算。
 * 使用的接口：无
 * 提供的接口：
 *  - Vector2d(double x, double y): 构造函数
 *  - add(Vector2d other): 向量加法
 *  - sub(Vector2d other): 向量减法
 *  - scale(double factor): 向量缩放
 */
package com.staraxis.game.shared.model;

import java.util.Objects;

/**
 * 双精度二维向量 (Double-precision 2D Vector)。
 * 用于需要高精度的逻辑坐标计算。
 */
public final class Vector2d {

    public static final Vector2d ZERO = new Vector2d(0, 0);

    public final double x;
    public final double y;

    public Vector2d(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Vector2d add(Vector2d other) {
        return new Vector2d(this.x + other.x, this.y + other.y);
    }

    public Vector2d sub(Vector2d other) {
        return new Vector2d(this.x - other.x, this.y - other.y);
    }

    public Vector2d scale(double factor) {
        return new Vector2d(this.x * factor, this.y * factor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vector2d vector2d = (Vector2d) o;
        return Double.compare(vector2d.x, x) == 0 && Double.compare(vector2d.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "Vector2d(" + x + ", " + y + ")";
    }
}
