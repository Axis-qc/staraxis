package staraxis.ui.utils;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;

/**
 * 一个通用的拖动监听器，可将目标 Actor 的位置限制在 Stage 屏幕范围内。
 *
 * 设计要点：
 * - 可复用：可附加到任何 Actor（如标题栏），并拖动另一个指定的 Actor（如整个窗口）。
 * - 边界限制：拖动时自动 clamp，防止 UI 被拖出屏幕。
 * - 缩放适应：提供 onResize() 方法，在窗口大小变化时重新夹紧位置，避免 UI 跑出边界。
 */
public class DragAndClampListener extends InputListener {

    private final Actor target;
    private float startX, startY;

    /**
     * @param target 要拖动的目标 Actor（通常是整个窗口）
     */
    public DragAndClampListener(Actor target) {
        this.target = target;
    }

    @Override
    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        // 记录鼠标按下时窗口坐标与触点差值
        startX = target.getX() - event.getStageX();
        startY = target.getY() - event.getStageY();
        return true;
    }

    @Override
    public void touchDragged(InputEvent event, float x, float y, int pointer) {
        float newX = startX + event.getStageX();
        float newY = startY + event.getStageY();
        clampPosition(newX, newY);
    }

    /**
     * 在窗口 resize 后调用，确保目标仍保持在屏幕内。
     */
    public void onResize() {
        clampPosition(target.getX(), target.getY());
    }

    private void clampPosition(float newX, float newY) {
        Stage stage = target.getStage();
        if (stage == null)
            return;

        float clampedX = MathUtils.clamp(newX, 0, stage.getWidth() - target.getWidth());
        float clampedY = MathUtils.clamp(newY, 0, stage.getHeight() - target.getHeight());

        target.setPosition(clampedX, clampedY);
    }
}
