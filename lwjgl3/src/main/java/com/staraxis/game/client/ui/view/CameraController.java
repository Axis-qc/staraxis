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
    private final Vector2 delta = new Vector2();

    private float minZoom = 0.1f;
    private float maxZoom = 2.0f;
    private float zoomSpeed = 0.1f;

    public CameraController(OrthographicCamera camera) {
        this.camera = camera;
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
        float oldZoom = camera.zoom;
        camera.zoom += amountY * zoomSpeed;
        camera.zoom = Math.max(minZoom, Math.min(maxZoom, camera.zoom));

        // 可选：以鼠标为中心缩放（这里暂时仅做基础中心缩放）
        return true;
    }

    public void update(float delta) {
        // 也可以添加键盘平移 (WASD)
        float speed = 500f * camera.zoom; // 平移速度与缩放成正比
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            camera.position.y += speed * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            camera.position.y -= speed * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            camera.position.x -= speed * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            camera.position.x += speed * delta;
        }
    }

    public void setZoomRange(float min, float max) {
        this.minZoom = min;
        this.maxZoom = max;
    }
}
