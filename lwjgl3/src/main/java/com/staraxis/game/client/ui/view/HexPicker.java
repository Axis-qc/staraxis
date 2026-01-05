package com.staraxis.game.client.ui.view;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.staraxis.game.shared.world.HexCoord;

/**
 * 六边形拾取器 (Hex Picker). 负责将屏幕坐标（或世界空间坐标）转换为六边形网格坐标。
 */
public class HexPicker {

    private final HexGridRenderer renderer;
    private final Vector3 tempVec = new Vector3();

    public HexPicker(HexGridRenderer renderer) {
        this.renderer = renderer;
    }

    /**
     * 将屏幕坐标转换为 HexCoord。
     *
     * @param screenX 屏幕 X
     * @param screenY 屏幕 Y
     * @param camera 世界摄像机
     * @return 对应的 HexCoord
     */
    public HexCoord screenToHex(int screenX, int screenY, Camera camera) {
        tempVec.set(screenX, screenY, 0);
        camera.unproject(tempVec);
        return worldToHex(tempVec.x, tempVec.y);
    }

    /**
     * 将世界坐标转换为 HexCoord (Pointy-top 布局)。
     */
    public HexCoord worldToHex(float worldX, float worldY) {
        float size = renderer.getHexRadius();

        // 这里的 worldY 需要根据 renderer 中的偏移进行反转 (renderer 中使用了 -y)
        float yVal = -worldY;

        float q = (float) ((Math.sqrt(3) / 3 * worldX - 1f / 3 * yVal) / size);
        float r = (float) (2f / 3 * yVal / size);

        return axialToCube(q, r);
    }

    private HexCoord axialToCube(float q, float r) {
        float x = q;
        float z = r;
        float y = -x - z;

        int rx = Math.round(x);
        int ry = Math.round(y);
        int rz = Math.round(z);

        float xDiff = Math.abs(rx - x);
        float yDiff = Math.abs(ry - y);
        float zDiff = Math.abs(rz - z);

        if (xDiff > yDiff && xDiff > zDiff) {
            rx = -ry - rz;
        } else if (yDiff > zDiff) {
            ry = -rx - rz;
        } else {
            rz = -rx - ry;
        }

        return HexCoord.of(rx, ry, rz);
    }
}
