package com.staraxis.game.client.ui.view;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * 摄像机控制器 (Camera Controller). 支持右键拖拽平移和鼠标滚轮缩放。
 */
public class CameraController extends InputAdapter {

    private final OrthographicCamera camera;
    /** 自定义世界中心（km）。若为空则回退旧 camera.position 方案 */
    private final com.staraxis.game.core.coordinate.CameraWorld worldCenter;
    private final Vector3 lastMousePos = new Vector3();
    private final Vector3 currMousePos = new Vector3();

    // 物理状态（惯性平移）
    private final Vector2 velocity = new Vector2();
    private final Vector2 acceleration = new Vector2();
    private float friction = 8.0f;

    /** 像素基准移动速度：保持约 10 像素/秒，再乘 zoom 转为 km/s。 */
    private float moveSpeed = 100f;

    /** 移动速度上限（单位：世界单位/s；这里世界单位近似为 km）。 */
    private float maxMoveSpeed = 200_000_000f; // 提升 100 倍（原 200_000）

    /**
     * 移动按住加速：倍率在持续移动时线性增长。
     * 设计目标：换方向时不要“顿停”，只要仍在移动就保持倍率，不重置为 1。
     */
    private float moveSpeedMultiplier = 1.0f;
    private float moveSpeedMinMultiplier = 1.0f;
    private float moveSpeedMaxMultiplier = 25.0f;
    private float moveSpeedRampPerSec = 3.0f; // 每秒线性增长量
    private float moveRampTimeoutSec = 0.25f;
    private float timeSinceMoveInputSec = 999f;

    // 缩放状态
    private float targetZoom;
    private float zoomSmoothing = 10.0f;
    /** 最小缩放：更近可以看到恒星/行星细节。 */
    private float minZoom = 0.01f;
    /**
     * 最大缩放：支持光年级别（zoom 直接等于 km/px）。
     * 例如：
     * - zoom ~= 1e5 -> 1px ≈ 10^5 km
     * - zoom ~= 1.5e8 -> 1px ≈ 1 AU
     */
    // 最大缩放：支持光年级别；100px ≈ 1 ly
    private float maxZoom = 94_607_305_000f; // ≈9.46e10 km/px，对应 100px ≈ 1 光年

    /** 基础滚轮缩放步进（作为初始速度）。 */
    private float baseZoomSpeed = 0.1f;

    /** 连续滚轮加速：在窗口期内持续滚动会指数增长倍率。 */
    private float zoomSpeedMultiplier = 1.0f;
    private float zoomSpeedMinMultiplier = 1.0f;
    private float zoomSpeedMaxMultiplier = 500.0f;

    /** 指数增长速率（每秒），值越大加速越快。 */
    private float zoomSpeedExpPerSec = 2.5f;

    /** 认为“连续滚动”的时间窗口。 */
    private float zoomRampTimeoutSec = 0.25f;
    private float timeSinceLastScrollSec = 999f;

    // 焦点拦截
    private boolean isIntercepted = false;

    public CameraController(OrthographicCamera camera, com.staraxis.game.core.coordinate.CameraWorld worldCenter) {
        this.camera = camera;
        this.worldCenter = worldCenter;
        this.targetZoom = camera.zoom;

        // 固定相机像素位置在原点，防漂移。
        this.camera.position.set(0, 0, 20);
    }

    /** 兼容旧构造：不传 worldCenter 则沿用旧逻辑（即直接动 camera.position） */
    public CameraController(OrthographicCamera camera) {
        this(camera, null);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.RIGHT) {
            lastMousePos.set(screenX, screenY, 0);
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
            currMousePos.set(screenX, screenY, 0);

            Vector3 lastWorldPx = camera.unproject(new Vector3(lastMousePos));
            Vector3 currWorldPx = camera.unproject(new Vector3(currMousePos));
            float dxPx = currWorldPx.x - lastWorldPx.x;
            float dyPx = currWorldPx.y - lastWorldPx.y;

            if (worldCenter != null) {
                // 像素→km；camera.zoom == kmPerPixel
                double kmPerPixel = camera.zoom;
                worldCenter.add(dxPx * kmPerPixel * -1.0, dyPx * kmPerPixel * -1.0);
            } else {
                camera.position.sub(dxPx, dyPx, 0);
            }
            lastMousePos.set(screenX, screenY, 0);
            return true;
        }
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (isIntercepted) {
            return false;
        }

        // 连续滚动：若超时则重置倍率
        if (timeSinceLastScrollSec > zoomRampTimeoutSec) {
            zoomSpeedMultiplier = zoomSpeedMinMultiplier;
        }
        timeSinceLastScrollSec = 0f;

        float effectiveZoomSpeed = baseZoomSpeed * zoomSpeedMultiplier;

        // 在大尺度下采用“按当前 zoom 比例”的步进，保证能较快到达光年/天文单位级别。
        float scaleFactor = Math.max(1.0f, targetZoom * 0.1f);

        // 设置目标缩放（线性步进 * 指数倍率 * 与当前 zoom 成比例）
        targetZoom += amountY * effectiveZoomSpeed * scaleFactor;
        targetZoom = clamp(targetZoom, minZoom, maxZoom);

        return true;
    }

    public void update(float delta) {
        // --- 连续缩放加速更新（指数增长）---
        timeSinceLastScrollSec += delta;
        if (timeSinceLastScrollSec <= zoomRampTimeoutSec) {
            zoomSpeedMultiplier = (float) (zoomSpeedMultiplier * Math.exp(zoomSpeedExpPerSec * delta));
            zoomSpeedMultiplier = clamp(zoomSpeedMultiplier, zoomSpeedMinMultiplier, zoomSpeedMaxMultiplier);
        } else {
            zoomSpeedMultiplier = zoomSpeedMinMultiplier;
        }

        // 1. 缩放平滑过渡（保持鼠标对焦）
        if (!isIntercepted && Gdx.input != null && Math.abs(camera.zoom - targetZoom) > 0.001f) {
            Vector3 mouseAtOldZoom = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(mouseAtOldZoom);

            camera.zoom += (targetZoom - camera.zoom) * zoomSmoothing * delta;
            float afterZoom = camera.zoom;

            Vector3 mouseAtNewZoom = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(mouseAtNewZoom);

            float dxPx = mouseAtOldZoom.x - mouseAtNewZoom.x;
            float dyPx = mouseAtOldZoom.y - mouseAtNewZoom.y;
            if (worldCenter != null) {
                double kmPerPixel = afterZoom; // 新比例尺
                worldCenter.add(dxPx * kmPerPixel, dyPx * kmPerPixel);
            } else {
                camera.position.add(dxPx, dyPx, 0);
            }
        }

        // 2. 键盘输入（加速度） + 按住时间线性加速
        timeSinceMoveInputSec += delta;
        if (!isIntercepted && Gdx.input != null) {
            acceleration.setZero();
            if (Gdx.input.isKeyPressed(Input.Keys.W))
                acceleration.y += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.S))
                acceleration.y -= 1;
            if (Gdx.input.isKeyPressed(Input.Keys.A))
                acceleration.x -= 1;
            if (Gdx.input.isKeyPressed(Input.Keys.D))
                acceleration.x += 1;

            if (!acceleration.isZero()) {
                // 只要持续有移动输入，就线性累计倍率；即使换方向也不重置，避免“顿停感”
                if (timeSinceMoveInputSec <= moveRampTimeoutSec) {
                    moveSpeedMultiplier += moveSpeedRampPerSec * delta;
                } else {
                    moveSpeedMultiplier = moveSpeedMinMultiplier;
                }
                moveSpeedMultiplier = clamp(moveSpeedMultiplier, moveSpeedMinMultiplier, moveSpeedMaxMultiplier);
                timeSinceMoveInputSec = 0f;

                Vector2 dir = new Vector2(acceleration).nor();

                float base = moveSpeed * camera.zoom;
                float boosted = base * moveSpeedMultiplier;
                float capped = Math.min(maxMoveSpeed, boosted);

                acceleration.set(dir).scl(capped);
                velocity.add(acceleration.x * delta, acceleration.y * delta);
            } else {
                if (timeSinceMoveInputSec > moveRampTimeoutSec) {
                    moveSpeedMultiplier = moveSpeedMinMultiplier;
                }
            }
        } else if (isIntercepted) {
            velocity.setZero();
            moveSpeedMultiplier = moveSpeedMinMultiplier;
        }

        // 3. 惯性位移（按 velocity 真实积分）
        if (velocity.len() > 0.1f) {
            if (velocity.len() > maxMoveSpeed) {
                velocity.nor().scl(maxMoveSpeed);
            }
            if (worldCenter != null) {
                double kmPerPixel = camera.zoom;
                worldCenter.add(velocity.x * delta * kmPerPixel, velocity.y * delta * kmPerPixel);
            } else {
                camera.position.add(velocity.x * delta, velocity.y * delta, 0);
            }
            velocity.scl(1.0f - friction * delta);
        } else {
            velocity.setZero();
        }
    }

    public void setZoomRange(float min, float max) {
        this.minZoom = min;
        this.maxZoom = max;
    }

    public void setIntercepted(boolean intercepted) {
        this.isIntercepted = intercepted;
    }

    public boolean isIntercepted() {
        return isIntercepted;
    }

    public Vector2 getVelocity() {
        return velocity;
    }

    public void setMaxMoveSpeed(float maxMoveSpeed) {
        this.maxMoveSpeed = maxMoveSpeed;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
