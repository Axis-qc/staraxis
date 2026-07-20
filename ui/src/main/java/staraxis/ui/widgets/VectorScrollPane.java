package staraxis.ui.widgets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import staraxis.ui.effects.VectorScrollPaneEffect;

/**
 * 矢量滚动面板控件。
 *
 * 使用 ShapeRenderer 绘制滚动条，
 * 裁剪子 Actor 到可视区域，支持垂直/水平滚动。
 * 替代 Scene2D 中依赖 Skin 的 ScrollPane 控件。
 */
public class VectorScrollPane extends Group {

    private static final VectorScrollPaneEffect DEFAULT_EFFECT = VectorScrollPaneEffect.fromMap("default", new java.util.HashMap<>());

    private final ShapeRenderer sr;
    private final VectorScrollPaneEffect effect;
    private final Actor content;

    private boolean scrollX;
    private boolean scrollY;
    private float scrollXOffset;
    private float scrollYOffset;
    private float contentWidth;
    private float contentHeight;
    private boolean draggingScrollBar;
    private boolean hoverScrollBar;
    private float lastKnownWidth;
    private float lastKnownHeight;

    public VectorScrollPane(ShapeRenderer sr, Actor content) {
        this(sr, DEFAULT_EFFECT, content);
    }

    public VectorScrollPane(ShapeRenderer sr, VectorScrollPaneEffect effect, Actor content) {
        this.sr = sr;
        this.effect = effect != null ? effect : DEFAULT_EFFECT;
        this.content = content;
        this.scrollX = false;
        this.scrollY = true;
        setTouchable(Touchable.enabled);

        addActor(content);

        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (scrollY && isOverScrollBar(x, y)) {
                    draggingScrollBar = true;
                    return true;
                }
                return false;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                draggingScrollBar = false;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (draggingScrollBar && scrollY) {
                    float viewH = getHeight();
                    float contentH = getContentHeight();
                    float barHeight = computeScrollBarHeight();
                    float availableTrack = viewH - barHeight;
                    if (availableTrack > 0) {
                        float ratio = (y - barHeight / 2f) / availableTrack;
                        ratio = MathUtils.clamp(ratio, 0f, 1f);
                        scrollYOffset = ratio * (contentH - viewH);
                        clampScrollOffset();
                    }
                }
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                hoverScrollBar = scrollY && isOverScrollBar(x, y);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (!draggingScrollBar) hoverScrollBar = false;
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                if (scrollY) {
                    scrollYOffset += amountY * 30f;
                    clampScrollOffset();
                    return true;
                }
                return false;
            }
        });
    }

    public void setScrollingDisabled(boolean disableX, boolean disableY) {
        this.scrollX = !disableX;
        this.scrollY = !disableY;
    }

    public void setScrollbarsVisible(boolean visible) {
        // 始终显示滚动条（不隐藏）
    }

    private boolean isOverScrollBar(float mx, float my) {
        if (!scrollY) return false;
        float barW = computeScrollBarWidth();
        float barH = computeScrollBarHeight();
        float barX = getWidth() - barW - 2;
        float viewH = getHeight();
        float contentH = getContentHeight();
        float availableTrack = viewH - barH;
        float barY = availableTrack > 0 ? (scrollYOffset / (contentH - viewH)) * availableTrack : 0;
        return mx >= barX && mx <= barX + barW && my >= barY && my <= barY + barH;
    }

    private float computeScrollBarWidth() {
        return effect.scrollBar.width;
    }

    private float computeScrollBarHeight() {
        float viewH = getHeight();
        float contentH = getContentHeight();
        if (contentH <= viewH) return viewH;
        float ratio = viewH / contentH;
        return Math.max(viewH * ratio, effect.scrollBar.minHeight);
    }

    private float getContentWidth() {
        return Math.max(content.getWidth(), getWidth());
    }

    private float getContentHeight() {
        return Math.max(content.getHeight(), getHeight());
    }

    private void clampScrollOffset() {
        float viewH = getHeight();
        float contentH = getContentHeight();
        if (contentH > viewH) {
            scrollYOffset = MathUtils.clamp(scrollYOffset, 0f, contentH - viewH);
        } else {
            scrollYOffset = 0;
        }
    }

    public void updateLayout() {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 计算内容真实尺寸：
        // - Layout 控件（如 Table）取 validate 后的 prefWidth/prefHeight，反映子元素累加高度
        // - 非 Layout 控件用当前 width/height
        // 两者均不小于视图尺寸，避免内容小于视图时出现负偏移
        float contentW;
        float contentH;
        if (content instanceof Layout) {
            // 先给宽度提示，让 Table 能根据可用宽度计算换行后的 prefHeight
            content.setWidth(Math.max(content.getWidth(), w));
            ((Layout) content).validate();
            contentW = ((Layout) content).getPrefWidth();
            contentH = ((Layout) content).getPrefHeight();
        } else {
            contentW = content.getWidth();
            contentH = content.getHeight();
        }
        contentW = Math.max(contentW, w);
        contentH = Math.max(contentH, h);

        content.setSize(contentW, contentH);
        content.setPosition(0, h - contentH + scrollYOffset);

        lastKnownWidth = w;
        lastKnownHeight = h;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 每次绘制前更新内容布局（确保内容位置跟随偏移）
        updateLayout();

        // 裁剪子 Actor 到可视区域
        if (w > 0 && h > 0) {
            // 用舞台坐标做裁剪，不能直接用 getX()/getY()（那是父容器相对坐标）
            Vector2 stagePos = localToStageCoordinates(new Vector2(0, 0));
            float sx = stagePos.x;
            float sy = stagePos.y;

            batch.end();
            Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST);
            com.badlogic.gdx.graphics.glutils.HdpiUtils.glScissor(
                    (int) sx,
                    (int) sy,
                    (int) w,
                    (int) h
            );
            batch.begin();
        }

        // 绘制子 Actor
        super.draw(batch, parentAlpha);

        if (w > 0 && h > 0) {
            batch.end();
            Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST);
            batch.begin();
        }

        // 绘制滚动条
        if (scrollY && getContentHeight() > h) {
            batch.end();

            sr.setProjectionMatrix(batch.getProjectionMatrix());
            sr.setTransformMatrix(batch.getTransformMatrix());

            float barW = computeScrollBarWidth();
            float barH = computeScrollBarHeight();
            float barX = getX() + w - barW - 2;
            float viewH = h;
            float contentH = getContentHeight();
            float availableTrack = viewH - barH;
            float barY = getY() + (availableTrack > 0 ? (scrollYOffset / (contentH - viewH)) * availableTrack : 0);

            Color sc = hoverScrollBar || draggingScrollBar ? effect.scrollBar.hoverColor : effect.scrollBar.color;
            sr.setColor(sc.r, sc.g, sc.b, sc.a * parentAlpha);
            sr.begin(ShapeType.Filled);
            if (effect.scrollBar.cornerRadius > 0) {
                sr.rect(barX, barY, barW, barH);
            } else {
                sr.rect(barX, barY, barW, barH);
            }
            sr.end();

            batch.begin();
        }
    }

    @Override
    public Actor hit(float x, float y, boolean touchable) {
        if (!isVisible() || !isTouchable()) return null;
        if (x < 0 || y < 0 || x > getWidth() || y > getHeight()) return null;
        // 优先检查子 Actor
        Actor hit = super.hit(x, y, touchable);
        if (hit != null && hit.isDescendantOf(this)) return hit;
        // 滚动面板本身也接收事件（滚轮/拖动）
        return this;
    }
}
