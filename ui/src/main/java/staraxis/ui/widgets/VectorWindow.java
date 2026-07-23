package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import staraxis.ui.FontProvider;
import staraxis.ui.effects.VectorWindowEffect;

/**
 * 矢量窗口控件。
 *
 * 使用 ShapeRenderer 绘制标题栏和内容区域，
 * 替代 Scene2D 中依赖 Skin 的 Window 控件。
 */
public class VectorWindow extends Group {

    private static final VectorWindowEffect DEFAULT_EFFECT = VectorWindowEffect.fromMap("default", new java.util.HashMap<>());

    /** 关闭按钮边长（px）喵 */
    private static final float CLOSE_BTN_SIZE = 14f;
    /** 关闭按钮距标题栏右上角的边距（px）喵 */
    private static final float CLOSE_BTN_MARGIN = 4f;
    /** 关闭按钮 X 线条内缩（px），让 X 图形小于按钮命中区域喵 */
    private static final float CLOSE_BTN_INSET = 3f;

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final VectorWindowEffect effect;
    private String titleText;
    private Group contentGroup;
    private boolean dragging;
    private float dragStartX, dragStartY;
    private float winStartX, winStartY;
    private boolean movable;
    private boolean resizable; // TODO: 调整大小功能尚未实现
    /** 是否显示标题栏关闭按钮（默认不显示，不破坏既有用法）喵 */
    private boolean closeButtonVisible;
    /** 关闭按钮回调，点击 X 时触发（通常由 UiWindowManager 挂接）喵 */
    private Runnable onClose;
    public VectorWindow(ShapeRenderer sr, BitmapFont font, String title) {
        this(sr, font, DEFAULT_EFFECT, title);
    }

    public VectorWindow(ShapeRenderer sr, BitmapFont font, VectorWindowEffect effect, String title) {
        this.sr = sr;
        this.font = font;
        this.effect = effect != null ? effect : DEFAULT_EFFECT;
        this.titleText = title != null ? title : "";
        this.movable = true;
        this.resizable = false;
        setTouchable(Touchable.enabled);
        setSize(300, 200);

        contentGroup = new Group();
        contentGroup.setTouchable(Touchable.enabled);
        addActor(contentGroup);

        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                // 关闭按钮命中检测必须在标题栏拖动检测之前（区域重叠）喵
                if (closeButtonVisible && onClose != null && isOnCloseButton(x, y)) {
                    onClose.run();
                    return true;
                }
                if (movable && y >= getHeight() - effect.titleBar.height) {
                    dragging = true;
                    dragStartX = event.getStageX();
                    dragStartY = event.getStageY();
                    winStartX = getX();
                    winStartY = getY();
                    toFront();
                    return true;
                }
                return false;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                dragging = false;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (dragging && movable) {
                    float dx = event.getStageX() - dragStartX;
                    float dy = event.getStageY() - dragStartY;
                    setPosition(winStartX + dx, winStartY + dy);
                }
            }
        });
    }

    public void setMovable(boolean movable) {
        this.movable = movable;
    }

    /** 设置是否显示标题栏关闭按钮喵 */
    public void setCloseButtonVisible(boolean visible) {
        this.closeButtonVisible = visible;
    }

    /** 设置关闭按钮回调喵 */
    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    /** 判断窗口局部坐标是否命中关闭按钮区域（标题栏右上角）喵 */
    private boolean isOnCloseButton(float x, float y) {
        float bx = getWidth() - CLOSE_BTN_MARGIN - CLOSE_BTN_SIZE;
        float by = getHeight() - CLOSE_BTN_MARGIN - CLOSE_BTN_SIZE;
        return x >= bx && x <= bx + CLOSE_BTN_SIZE && y >= by && y <= by + CLOSE_BTN_SIZE;
    }

    public void setResizable(boolean resizable) {
        this.resizable = resizable;
    }

    public void setTitle(String title) {
        this.titleText = title != null ? title : "";
    }

    public Group getContentGroup() {
        return contentGroup;
    }

    public void addContent(Actor actor) {
        contentGroup.addActor(actor);
    }

    public void pack() {
        // 调整大小以容纳内容
        float maxW = getWidth();
        float maxH = getHeight();
        for (Actor child : contentGroup.getChildren()) {
            maxW = Math.max(maxW, child.getX() + child.getWidth() + 10);
            maxH = Math.max(maxH, child.getY() + child.getHeight() + 10);
        }
        setSize(maxW, maxH + effect.titleBar.height);
    }

    /**
     * 按内容区尺寸调整窗口大小（自动加上标题栏高度与边距），并刷新布局喵。
     * 调用方无需感知标题栏高度，适用于内容行数动态计算的场景（如 EntityInfoPanel）。
     */
    public void setContentSize(float contentWidth, float contentHeight) {
        setSize(contentWidth + 8, contentHeight + effect.titleBar.height + 8);
        updateLayout();
    }

    public void updateLayout() {
        // 内容区域位置：底部起始
        contentGroup.setPosition(4, 4);
        contentGroup.setSize(getWidth() - 8, getHeight() - effect.titleBar.height - 8);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        float alpha = getColor().a * parentAlpha;
        float titleH = effect.titleBar.height;

        batch.end();

        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.setTransformMatrix(batch.getTransformMatrix());

        // 内容区背景
        Color ct = effect.content.color;
        sr.setColor(ct.r, ct.g, ct.b, ct.a * alpha);
        sr.begin(ShapeType.Filled);
        sr.rect(x, y, w, h);
        sr.end();

        // 标题栏背景
        Color tb = effect.titleBar.color;
        sr.setColor(tb.r, tb.g, tb.b, tb.a * alpha);
        sr.begin(ShapeType.Filled);
        sr.rect(x, y + h - titleH, w, titleH);
        sr.end();

        // 边框
        if (effect.border.width > 0) {
            Color bc = effect.border.color;
            sr.setColor(bc.r, bc.g, bc.b, bc.a * alpha);
            sr.begin(ShapeType.Line);
            sr.rect(x, y, w, h);
            sr.end();
        }

        // 关闭按钮 X 图形（标题栏右上角，颜色跟随标题文字）喵
        if (closeButtonVisible) {
            float bx = x + w - CLOSE_BTN_MARGIN - CLOSE_BTN_SIZE;
            float by = y + h - CLOSE_BTN_MARGIN - CLOSE_BTN_SIZE;
            Color tc = effect.titleBar.textColor;
            sr.begin(ShapeType.Line);
            sr.setColor(tc.r, tc.g, tc.b, tc.a * alpha);
            sr.line(bx + CLOSE_BTN_INSET, by + CLOSE_BTN_INSET,
                    bx + CLOSE_BTN_SIZE - CLOSE_BTN_INSET, by + CLOSE_BTN_SIZE - CLOSE_BTN_INSET);
            sr.line(bx + CLOSE_BTN_INSET, by + CLOSE_BTN_SIZE - CLOSE_BTN_INSET,
                    bx + CLOSE_BTN_SIZE - CLOSE_BTN_INSET, by + CLOSE_BTN_INSET);
            sr.end();
        }

        batch.begin();

        // 标题文字
        if (font != null) {
            float oldScaleX = font.getData().scaleX;
            float oldScaleY = font.getData().scaleY;
            float scale = Math.max(0.1f, effect.titleBar.textSize / FontProvider.VECTOR_FONT_GEN_SIZE);
            font.getData().setScale(scale);

            font.setColor(effect.titleBar.textColor);
            font.draw(batch, titleText, x + 10, y + h - titleH + (titleH + font.getCapHeight()) / 2f);

            font.setColor(Color.WHITE);
            font.getData().setScale(oldScaleX, oldScaleY);
        }

        // 绘制子 Actor（内容区）
        super.draw(batch, parentAlpha);
    }
}
