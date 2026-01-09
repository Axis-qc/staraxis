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
    private final com.staraxis.game.core.coordinate.CameraWorld camWorld;
    
    public HexPicker(HexGridRenderer renderer, com.staraxis.game.core.coordinate.CameraWorld camWorld) {
        this.renderer = renderer;
        this.camWorld = camWorld;
    }

    /** 保留旧构造（停止使用） */
    @Deprecated
    public HexPicker(HexGridRenderer renderer) {
        this(renderer, null);
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
        if (camWorld != null && camera instanceof com.badlogic.gdx.graphics.OrthographicCamera oc) {
            double kmPerPixel = oc.zoom;
            double worldXKm = tempVec.x * kmPerPixel + camWorld.getXKm();
            double worldYKm = tempVec.y * kmPerPixel + camWorld.getYKm();
            return worldKmToHex(worldXKm, worldYKm, kmPerPixel);
        }
        return worldToHex(tempVec.x, tempVec.y);
    }

    private HexCoord worldKmToHex(double worldXKm, double worldYKm, double kmPerPixel) {
        double worldXPx = (worldXKm - camWorld.getXKm()) / kmPerPixel;
        double worldYPx = (worldYKm - camWorld.getYKm()) / kmPerPixel;
        return worldToHex((float)worldXPx, (float)worldYPx);
    }

    /**
     * 将世界坐标转换为 HexCoord (Pointy-top 布局)。
     */
    public HexCoord worldToHex(float worldX, float worldY) {
        float size = renderer.getHexRadius();

        // 移除 Y 轴反转，直接使用世界坐标 Y (与 HexGridRenderer 保持一致)
        float q = (float) ((Math.sqrt(3) / 3 * worldX - 1f / 3 * worldY) / size);
        float r = (float) (2f / 3 * worldY / size);

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
