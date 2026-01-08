package com.staraxis.render.universe;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector3;
import com.staraxis.universegen.SectorLocatorService;
import com.staraxis.universegen.model.Sector;

/**
 * SectorCameraController 负责在 <1 s 内将相机平滑移动至指定星区中心。
 * 使用插值曲线避免瞬移造成眩晕感。
 */
public final class SectorCameraController {

    private final OrthographicCamera camera;
    private final SectorLocatorService locatorService;
    private float animDurationSec = 0.8f; // 默认动画时长 0.8 秒 (<1 s)

    // 动画状态
    private boolean animating;
    private float elapsed;
    private Vector3 startPos = new Vector3();
    private Vector3 targetPos = new Vector3();

    public SectorCameraController(OrthographicCamera camera, SectorLocatorService locatorService) {
        this.camera = camera;
        this.locatorService = locatorService;
    }

    /**
     * 触发跳转动画。
     */
    public void jumpTo(Sector sector) {
        var coord = locatorService.locateCenter(sector);
        jumpTo((float) coord.getXKm(), (float) coord.getYKm());
    }

    public void jumpTo(float worldXKm, float worldYKm) {
        startPos.set(camera.position);
        targetPos.set(worldXKm, worldYKm, 0);
        animating = true;
        elapsed = 0f;
    }

    /**
     * 在游戏循环中调用，用于更新动画。
     */
    public void update(float deltaSec) {
        if (!animating) return;
        elapsed += deltaSec;
        float alpha = Math.min(1f, elapsed / animDurationSec);
        // 使用 SineOut 插值（先快后慢）
        float interp = Interpolation.sineOut.apply(alpha);
        camera.position.set(
                startPos.x + (targetPos.x - startPos.x) * interp,
                startPos.y + (targetPos.y - startPos.y) * interp,
                0f);
        camera.update();
        if (alpha >= 1f) animating = false;
    }

    public boolean isAnimating() {
        return animating;
    }

    public void setAnimDurationSec(float animDurationSec) {
        this.animDurationSec = animDurationSec;
    }
}
