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
