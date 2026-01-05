package com.staraxis.game.shared.model;

/**
 * 逻辑坐标点 (Logical Coordinate Vector)
 *
 * 使用的接口: 无 提供的接口: 供 C/S 两端进行位置表示与演算
 */
public class Vector2 /* 逻辑坐标 */ {

    public float x /* X轴坐标 */;
    public float y /* Y轴坐标 */;

    public Vector2() {
    }

    public Vector2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return "Vector2(" + x + ", " + y + ")";
    }
}
