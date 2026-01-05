package com.staraxis.game.client.ui.view;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 镜头惯性逻辑单元测试 (Input Inertia Unit Test). 注意：由于 CameraController 依赖
 * Gdx.input，这里的测试主要验证物理数学公式。
 */
public class InputInertiaTest {

    @Test
    public void testVelocityDecay() {
        OrthographicCamera camera = new OrthographicCamera();
        CameraController controller = new CameraController(camera);

        // 人为设置初始速度
        Vector2 velocity = controller.getVelocity();
        velocity.set(100f, 100f);

        float delta = 0.1f;
        float friction = 5.0f;

        // 模拟一帧更新 (不触发键盘输入)
        // 期望速度按照 v = v * (1 - friction * delta) 衰减
        controller.update(delta);

        float expectedX = 100f * (1.0f - friction * delta);
        float expectedY = 100f * (1.0f - friction * delta);

        assertEquals(expectedX, controller.getVelocity().x, 0.01f);
        assertEquals(expectedY, controller.getVelocity().y, 0.01f);
    }

    @Test
    public void testStopThreshold() {
        OrthographicCamera camera = new OrthographicCamera();
        CameraController controller = new CameraController(camera);

        // 设置极小速度
        controller.getVelocity().set(0.05f, 0.05f);

        // 更新后应该直接归零 (阈值 0.1f)
        controller.update(0.1f);

        assertEquals(0f, controller.getVelocity().x);
        assertEquals(0f, controller.getVelocity().y);
    }
}
