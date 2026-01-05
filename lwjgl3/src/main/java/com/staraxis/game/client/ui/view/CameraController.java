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
    private final Vector3 lastMousePos = new Vector3();
    private final Vector3 currMousePos = new Vector3();

    // 物理状态 (T009)
    private final Vector2 velocity = new Vector2();
    private final Vector2 acceleration = new Vector2();
    private float friction = 5.0f; // 摩擦系数
    private float moveSpeed = 1000f; // 基础移动速度

    // 缩放状态 (T013)
    private float targetZoom;
    private float zoomSmoothing = 10.0f;
    private float minZoom = 0.1f;
    private float maxZoom = 2.0f;
    private float zoomSpeed = 0.1f;

    // 焦点拦截 (T014)
    private boolean isIntercepted = false;

    public CameraController(OrthographicCamera camera) {
        this.camera = camera;
        this.targetZoom = camera.zoom;
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

            // 转换为世界空间位移
            Vector3 lastWorld = camera.unproject(new Vector3(lastMousePos));
            Vector3 currWorld = camera.unproject(new Vector3(currMousePos));

            camera.position.sub(currWorld.x - lastWorld.x, currWorld.y - lastWorld.y, 0);
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

        // 记录缩放前的鼠标世界坐标 (T012)
        Vector3 mouseBefore = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseBefore);

        // 设置目标缩放 (T013)
        targetZoom += amountY * zoomSpeed;
        targetZoom = Math.max(minZoom, Math.min(maxZoom, targetZoom));

        // 立即应用一部分缩放以计算位置修正，或者在 update 中平滑处理
        // 这里为了精确对焦，我们先计算出缩放后的理想位置
        float zoomRatio = targetZoom / camera.zoom;

        // 我们不在这里直接改 camera.zoom，而是在 update 中平滑过渡
        return true;
    }

    public void update(float delta) {
        // 1. 缩放平滑过渡 (T013)
        // 只有在非拦截状态且有输入环境时才进行缩放插值（因为需要鼠标位置）
        if (!isIntercepted && Gdx.input != null && Math.abs(camera.zoom - targetZoom) > 0.001f) {
            // 记录缩放前的鼠标世界位置
            Vector3 mouseAtOldZoom = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(mouseAtOldZoom);

            // 插值缩放
            camera.zoom += (targetZoom - camera.zoom) * zoomSmoothing * delta;

            // 重新计算世界位置并平移相机以保持鼠标指向点不变 (T012)
            Vector3 mouseAtNewZoom = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(mouseAtNewZoom);

            camera.position.add(mouseAtOldZoom.x - mouseAtNewZoom.x, mouseAtOldZoom.y - mouseAtNewZoom.y, 0);
        }

        // 2. 处理键盘输入加速度 (T010)
        // 只有在非拦截状态且有输入环境时才读取按键
        if (!isIntercepted && Gdx.input != null) {
            acceleration.setZero();
            if (Gdx.input.isKeyPressed(Input.Keys.W)) {
                acceleration.y += 1;
            }
            if (Gdx.input.isKeyPressed(Input.Keys.S)) {
                acceleration.y -= 1;
            }
            if (Gdx.input.isKeyPressed(Input.Keys.A)) {
                acceleration.x -= 1;
            }
            if (Gdx.input.isKeyPressed(Input.Keys.D)) {
                acceleration.x += 1;
            }

            if (!acceleration.isZero()) {
                acceleration.nor().scl(moveSpeed);
                velocity.add(acceleration.x * delta, acceleration.y * delta);
            }
        } else if (isIntercepted) {
            // 被拦截时强制停止
            velocity.setZero();
        }

        // 3. 应用惯性物理 (T011) - 即使没有 Gdx.input 也应执行（用于单元测试）
        if (velocity.len() > 0.1f) {
            camera.position.add(velocity.x * camera.zoom, velocity.y * camera.zoom, 0);
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
}
