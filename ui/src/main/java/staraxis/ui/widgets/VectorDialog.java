package staraxis.ui.widgets;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;

import staraxis.ui.FontProvider;
import staraxis.ui.effects.VectorWindowEffect;

/**
 * 矢量对话框控件。
 *
 * 基于 VectorWindow 的模态对话框实现，
 * 替代 Scene2D 中依赖 Skin 的 Dialog 控件。
 */
public class VectorDialog extends Group {

    private static final VectorWindowEffect DEFAULT_EFFECT = VectorWindowEffect.fromMap("default",
            new java.util.HashMap<>());

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final VectorWindowEffect effect;
    private String titleText;
    private String bodyText;
    private Group contentGroup;
    private Group buttonGroup;

    /** 按钮定义列表（支持多个按钮并排显示）。 */
    private final List<ButtonEntry> buttons = new ArrayList<>();

    /** 按钮条目。 */
    private static class ButtonEntry {
        final String text;
        final Runnable onClick;

        ButtonEntry(String text, Runnable onClick) {
            this.text = text;
            this.onClick = onClick;
        }
    }

    // 模态阻挡层
    private Actor blocker;

    // 关闭按钮尺寸
    private static final float CLOSE_BTN_SIZE = 18f;
    private static final float CLOSE_BTN_PADDING = 5f;
    private boolean closeHovered;

    public VectorDialog(ShapeRenderer sr, BitmapFont font, String title, String body) {
        this(sr, font, DEFAULT_EFFECT, title, body);
    }

    public VectorDialog(ShapeRenderer sr, BitmapFont font, VectorWindowEffect effect, String title, String body) {
        this.sr = sr;
        this.font = font;
        this.effect = effect != null ? effect : DEFAULT_EFFECT;
        this.titleText = title != null ? title : "";
        this.bodyText = body != null ? body : "";
        setTouchable(Touchable.enabled);
        setSize(320, 180);

        contentGroup = new Group();
        contentGroup.setTouchable(Touchable.enabled);
        addActor(contentGroup);

        buttonGroup = new Group();
        buttonGroup.setTouchable(Touchable.enabled);
        addActor(buttonGroup);

        // 关闭按钮交互
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (isOverCloseBtn(x, y)) {
                    hide();
                    return true;
                }
                return false;
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                closeHovered = isOverCloseBtn(x, y);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                closeHovered = false;
            }
        });
    }

    /**
     * 判断坐标是否在关闭按钮区域内。
     */
    private boolean isOverCloseBtn(float localX, float localY) {
        float titleH = effect.titleBar.height;
        float btnX = getWidth() - CLOSE_BTN_SIZE - CLOSE_BTN_PADDING;
        float btnY = getHeight() - titleH + (titleH - CLOSE_BTN_SIZE) / 2f;
        return localX >= btnX && localX <= btnX + CLOSE_BTN_SIZE
                && localY >= btnY && localY <= btnY + CLOSE_BTN_SIZE;
    }

    public void setBody(String body) {
        this.bodyText = body != null ? body : "";
    }

    /**
     * 设置单个确认按钮（覆盖之前添加的所有按钮）。
     * 保留用于向后兼容。
     */
    public void setButton(String buttonText, Runnable onClick) {
        buttons.clear();
        if (onClick != null) {
            buttons.add(new ButtonEntry(buttonText != null ? buttonText : "OK", onClick));
        }
    }

    /**
     * 添加一个按钮。可多次调用来添加多个按钮，它们会并排显示。
     */
    public void addButton(String text, Runnable onClick) {
        if (text != null && onClick != null) {
            buttons.add(new ButtonEntry(text, onClick));
        }
    }

    /**
     * 创建并添加按钮作为子 Actor。
     * 支持多个按钮并排显示。
     */
    private void createButtonActors() {
        if (buttons.isEmpty() || sr == null || font == null) {
            // 兜底：没有按钮时点阻挡层关闭
            if (blocker != null) {
                blocker.clearListeners();
                blocker.addListener(new InputListener() {
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                        hide();
                        return true;
                    }
                });
            }
            return;
        }

        float bw = 120f;
        float bh = 36f;
        float gap = 12f;
        int count = buttons.size();
        float totalW = count * bw + (count - 1) * gap;
        float startX = (getWidth() - totalW) / 2f;

        for (int i = 0; i < count; i++) {
            ButtonEntry entry = buttons.get(i);
            VectorButton btn = new VectorButton(sr, font, entry.text, entry.onClick);
            btn.setSize(bw, bh);
            btn.setPosition(startX + i * (bw + gap), 16);
            addActor(btn);
        }
    }

    /**
     * 显示对话框（添加到 Stage，创建模态阻挡层）。
     */
    public void show(Stage stage) {
        if (stage == null)
            return;

        // 创建模态阻挡层
        blocker = new Actor();
        blocker.setTouchable(Touchable.enabled);
        blocker.setSize(stage.getWidth(), stage.getHeight());
        blocker.setName("dialog_blocker");
        blocker.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true; // 阻挡事件穿透
            }
        });

        // 居中
        setPosition((stage.getWidth() - getWidth()) / 2f, (stage.getHeight() - getHeight()) / 2f);

        // 创建按钮 Actor
        createButtonActors();

        // 添加到舞台（阻挡层先添加，对话框后添加显示在上层）
        stage.addActor(blocker);
        stage.addActor(this);
        toFront();
    }

    /**
     * 隐藏对话框。
     */
    public void hide() {
        if (blocker != null) {
            blocker.remove();
            blocker = null;
        }
        remove();
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
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.rect(x, y, w, h);
        sr.end();

        // 标题栏背景
        Color tb = effect.titleBar.color;
        sr.setColor(tb.r, tb.g, tb.b, tb.a * alpha);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.rect(x, y + h - titleH, w, titleH);
        sr.end();

        // 边框
        if (effect.border.width > 0) {
            Color bc = effect.border.color;
            sr.setColor(bc.r, bc.g, bc.b, bc.a * alpha);
            sr.begin(ShapeRenderer.ShapeType.Line);
            sr.rect(x, y, w, h);
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

            // 正文
            float bodyScale = Math.max(0.1f, 18f / FontProvider.VECTOR_FONT_GEN_SIZE);
            font.getData().setScale(bodyScale);
            font.setColor(new Color(0.8f, 0.8f, 0.8f, 1f));
            font.draw(batch, bodyText, x + 20, y + h - titleH - 20);

            font.setColor(Color.WHITE);
            font.getData().setScale(oldScaleX, oldScaleY);
        }

        // 绘制关闭按钮（X）
        batch.end();
        float btnSize = CLOSE_BTN_SIZE;
        float btnX2 = x + w - btnSize - CLOSE_BTN_PADDING;
        float btnY2 = y + h - titleH + (titleH - btnSize) / 2f;
        float pad = 4f;
        if (closeHovered) {
            // 悬停时绘制背景圆
            sr.setColor(0.8f, 0.2f, 0.2f, 0.6f * alpha);
            sr.begin(ShapeType.Filled);
            sr.rect(btnX2, btnY2, btnSize, btnSize);
            sr.end();
            sr.setColor(1f, 1f, 1f, 1f * alpha);
        } else {
            sr.setColor(0.7f, 0.7f, 0.7f, 0.8f * alpha);
        }
        sr.begin(ShapeType.Line);
        sr.line(btnX2 + pad, btnY2 + pad, btnX2 + btnSize - pad, btnY2 + btnSize - pad);
        sr.line(btnX2 + btnSize - pad, btnY2 + pad, btnX2 + pad, btnY2 + btnSize - pad);
        sr.end();
        batch.begin();

        // 绘制子 Actor（按钮等）
        super.draw(batch, parentAlpha);
    }
}
