package staraxis.ui.widgets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import staraxis.ui.theme.UiTheme;

/**
 * HoverTooltipBinder（通用悬停提示绑定器）喵。
 *
 * 为 3D 世界拾取驱动的悬停提示提供统一状态机——3D 拾取不产生 scene2d 事件，
 * 由调用方每帧喂入当前悬停实体 key：
 * - 持续悬停 {@link #HOVER_DELAY} 秒后显示 tooltip（锚定在传入的屏幕锚点旁）
 * - tooltip 显示满 {@link #PIN_DELAY} 秒自动触发 {@link PinListener#onPin(long)}
 *   钉住回调（2026-07-23 修正：原为"鼠标移入 tooltip 内停留 3 秒"，但鼠标移开
 *   实体 tooltip 即消失、tooltip 又锚定在实体旁，鼠标永远来不及移入，故改为
 *   显示即计时，期间右下角显示圆圈进度环）
 * - 钉住后对同一实体不再显示 tooltip，直到悬停 key 离开该实体后才解除封锁
 *   （否则钉住后鼠标未移开会立即重新显示 tooltip 形成死循环）
 *
 * 用法：每帧调用 {@link #updateHover(long, String, float, float)}，
 * hoveredKey &lt; 0 或 content 为空表示无悬停。
 */
public class HoverTooltipBinder {

    /** 悬停延迟（秒）：持续悬停该时长后才显示 tooltip，避免快速扫过时闪烁喵 */
    private static final float HOVER_DELAY = 0.3f;
    /** 钉住延迟（秒）：tooltip 显示满该时长后自动钉住喵 */
    private static final float PIN_DELAY = 3.0f;
    /** tooltip 相对锚点的 X 偏移（px），避免遮挡目标喵 */
    private static final float ANCHOR_OFFSET_X = 20f;
    /** tooltip 相对锚点的 Y 偏移（px）喵 */
    private static final float ANCHOR_OFFSET_Y = 10f;

    /** 钉住回调：tooltip 钉住时触发，参数为被钉住的实体 key 喵 */
    public interface PinListener {
        void onPin(long entityKey);
    }

    /** 状态机：隐藏 → 悬停计时中 → 显示中喵 */
    private enum State { HIDDEN, PENDING, VISIBLE }

    private final Stage stage;
    private final TooltipPanel tooltip;
    private PinListener pinListener;

    private State state = State.HIDDEN;
    /** 当前悬停/显示的实体 key，-1 表示无喵 */
    private long currentKey = -1;
    /** 悬停计时（PENDING 状态用）喵 */
    private float hoverTimer = 0f;
    /** 钉住计时（VISIBLE 状态的累计显示时长）喵 */
    private float pinTimer = 0f;
    /** 已钉住的实体 key：同 key 不再显示 tooltip，-1 表示无喵 */
    private long pinnedKey = -1;
    /** 当前显示的 tooltip 文本，用于内容变化时刷新喵 */
    private String lastContent = "";

    public HoverTooltipBinder(Stage stage, ShapeRenderer sr, BitmapFont font, UiTheme theme) {
        this.stage = stage;
        this.tooltip = new TooltipPanel(sr, font, theme);
        this.tooltip.setVisible(false);
    }

    /** 设置钉住回调喵 */
    public void setPinListener(PinListener listener) {
        this.pinListener = listener;
    }

    /**
     * 每帧更新悬停状态喵。
     *
     * @param hoveredKey 当前悬停实体 key，&lt; 0 表示无悬停
     * @param content    tooltip 文本（hoveredKey &gt;= 0 时有效；null/空按无悬停处理）
     * @param anchorX    屏幕锚点 X（OpenGL 屏幕坐标，左下原点）
     * @param anchorY    屏幕锚点 Y（OpenGL 屏幕坐标，左下原点）
     */
    public void updateHover(long hoveredKey, String content, float anchorX, float anchorY) {
        ensureAttached();
        float dt = Gdx.graphics.getDeltaTime();

        // 悬停 key 离开被钉住实体后解除封锁（包括移到空处或别的实体）喵
        if (pinnedKey >= 0 && hoveredKey != pinnedKey) {
            pinnedKey = -1;
        }

        boolean hasHover = hoveredKey >= 0 && content != null && !content.isEmpty();
        if (hoveredKey == pinnedKey) {
            hasHover = false; // 已钉住的实体不再显示 tooltip
        }

        switch (state) {
            case HIDDEN -> {
                if (hasHover) {
                    currentKey = hoveredKey;
                    hoverTimer = 0f;
                    state = State.PENDING;
                }
            }
            case PENDING -> {
                if (hasHover && hoveredKey == currentKey) {
                    hoverTimer += dt;
                    if (hoverTimer >= HOVER_DELAY) {
                        showTooltip(content, anchorX, anchorY);
                        state = State.VISIBLE;
                    }
                } else if (hasHover) {
                    // 换了个实体，重新计时喵
                    currentKey = hoveredKey;
                    hoverTimer = 0f;
                } else {
                    state = State.HIDDEN;
                }
            }
            case VISIBLE -> {
                if (hasHover && hoveredKey == currentKey) {
                    // 持续悬停原实体：内容变化时刷新，跟随锚点（相机可能移动），
                    // 并累计钉住计时、驱动进度环喵
                    if (!content.equals(lastContent)) {
                        tooltip.setContent(content);
                        lastContent = content;
                    }
                    positionTooltip(anchorX, anchorY);
                    pinTimer += dt;
                    tooltip.setPinProgress(pinTimer / PIN_DELAY);
                    if (pinTimer >= PIN_DELAY) {
                        firePin();
                    }
                } else if (hasHover) {
                    // 换实体：隐藏后重新走 PENDING 喵
                    hideTooltip();
                    currentKey = hoveredKey;
                    hoverTimer = 0f;
                    pinTimer = 0f;
                    state = State.PENDING;
                } else {
                    hideTooltip();
                    state = State.HIDDEN;
                }
            }
        }
    }

    /** 强制隐藏并重置状态（HUD 切换 screen 时调用）喵 */
    public void forceHide() {
        hideTooltip();
        state = State.HIDDEN;
        currentKey = -1;
        hoverTimer = 0f;
        pinTimer = 0f;
        tooltip.setPinProgress(0f);
    }

    /** 从舞台移除 tooltip（HUD dispose 时调用）喵 */
    public void dispose() {
        tooltip.remove();
    }

    // ===== private helpers =====

    /** 触发钉住：记录钉住 key、隐藏 tooltip、回调外部喵 */
    private void firePin() {
        long key = currentKey;
        pinnedKey = key;
        pinTimer = 0f;
        hideTooltip();
        state = State.HIDDEN;
        if (pinListener != null) {
            pinListener.onPin(key);
        }
    }

    private void showTooltip(String content, float anchorX, float anchorY) {
        tooltip.setContent(content);
        lastContent = content;
        positionTooltip(anchorX, anchorY);
        tooltip.setVisible(true);
        tooltip.toFront();
    }

    private void hideTooltip() {
        tooltip.setVisible(false);
        tooltip.setPinProgress(0f);
    }

    /**
     * 定位 tooltip 到锚点旁（带边界裁剪）。
     * 锚点是 camera.project 输出的 OpenGL 屏幕坐标（左下原点），与 stage 默认
     * 坐标系一致，无需翻转 Y（2026-07-23 修复：旧实现误加 stageH - anchorY 翻转，
     * 导致 tooltip 显示在锚点的上下镜像位置）喵。
     */
    private void positionTooltip(float anchorX, float anchorY) {
        float x = anchorX + ANCHOR_OFFSET_X;
        float y = anchorY + ANCHOR_OFFSET_Y;

        // 右边界溢出时翻转到锚点左侧喵
        float pw = tooltip.getWidth();
        if (x + pw > stage.getWidth()) {
            x = anchorX - pw - ANCHOR_OFFSET_X;
        }
        // 上边界溢出时翻转到锚点下方喵
        float ph = tooltip.getHeight();
        if (y + ph > stage.getHeight()) {
            y = anchorY - ph - ANCHOR_OFFSET_Y;
        }
        if (y < 0) {
            y = ANCHOR_OFFSET_Y;
        }
        tooltip.setPosition(x, y);
    }

    /** tooltip 懒附加到舞台（stage.clear() 切屏后自动恢复）喵 */
    private void ensureAttached() {
        if (tooltip.getStage() == null) {
            stage.addActor(tooltip);
        }
    }
}
