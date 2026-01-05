package com.staraxis.game.client.ui.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;

/**
 * UI 皮肤辅助工具 (Skin Utilities). 提供程序化生成 NinePatch 的功能，用于创建科技感边框。
 */
public class SkinUtils {

    /**
     * 创建一个简单的矩形 NinePatch。
     *
     * @param borderWidth 边框粗细
     * @param borderColor 边框颜色
     * @param fillColor 填充颜色
     * @return 程序化生成的 NinePatch
     */
    public static NinePatch createNinePatch(int borderWidth, Color borderColor, Color fillColor) {
        int size = 12; // 基础尺寸
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // 1. 填充背景
        pixmap.setColor(fillColor);
        pixmap.fillRectangle(borderWidth, borderWidth, size - borderWidth * 2, size - borderWidth * 2);

        // 2. 绘制边框
        pixmap.setColor(borderColor);
        pixmap.fillRectangle(0, 0, size, borderWidth); // 上
        pixmap.fillRectangle(0, size - borderWidth, size, borderWidth); // 下
        pixmap.fillRectangle(0, 0, borderWidth, size); // 左
        pixmap.fillRectangle(size - borderWidth, 0, borderWidth, size); // 右

        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        // 创建 NinePatch，拉伸中心区域
        return new NinePatch(texture, borderWidth, borderWidth, borderWidth, borderWidth);
    }
}
