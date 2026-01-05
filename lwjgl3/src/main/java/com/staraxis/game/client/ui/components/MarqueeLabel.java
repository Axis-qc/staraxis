package com.staraxis.game.client.ui.components;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.Align;

/**
 * 自动缩放与跑马灯滚动文本组件 (Auto-scaling & Marquee Label)
 * 当文本超出宽度时，首先尝试缩小字号，若达到阈值仍溢出，则启动跑马灯滚动效果。
 */
public class MarqueeLabel extends Label {

    private float minScale = 0.7f;
    private float scrollSpeed = 50f; // 像素/秒
    private float scrollOffset = 0f;
    private boolean isScrolling = false;
    private float pauseTimer = 0f;
    private final float pauseDuration = 2.0f; // 滚动到头后停留时间

    private final Rectangle area = new Rectangle();
    private final Rectangle scissor = new Rectangle();

    public MarqueeLabel(CharSequence text, Skin skin) {
        super(text, skin);
        setAlignment(Align.center);
    }

    public MarqueeLabel(CharSequence text, Skin skin, String styleName) {
        super(text, skin, styleName);
        setAlignment(Align.center);
    }

    @Override
    public void act(float delta) {
        super.act(delta);

        float textWidth = getGlyphLayout().width;
        float availableWidth = getWidth();

        if (textWidth > availableWidth) {
            float scale = availableWidth / textWidth;
            if (scale >= minScale) {
                // 缩放即可容纳
                setFontScale(scale);
                isScrolling = false;
                scrollOffset = 0;
            } else {
                // 需要滚动
                setFontScale(minScale);
                isScrolling = true;
                updateScrolling(delta, textWidth * minScale, availableWidth);
            }
        } else {
            setFontScale(1.0f);
            isScrolling = false;
            scrollOffset = 0;
        }
    }

    private void updateScrolling(float delta, float scaledTextWidth, float availableWidth) {
        if (pauseTimer > 0) {
            pauseTimer -= delta;
            return;
        }

        scrollOffset += scrollSpeed * delta;
        float maxScroll = scaledTextWidth - availableWidth;

        if (scrollOffset > maxScroll + 50) { // 额外留白 50 像素
            scrollOffset = 0;
            pauseTimer = pauseDuration;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!isScrolling) {
            super.draw(batch, parentAlpha);
            return;
        }

        getStage().calculateScissors(area.set(getX(), getY(), getWidth(), getHeight()), scissor);
        batch.flush();
        if (ScissorStack.pushScissors(scissor)) {
            float originalX = getX();
            setX(originalX - scrollOffset);
            super.draw(batch, parentAlpha);
            setX(originalX);
            batch.flush();
            ScissorStack.popScissors();
        }
    }

    public void setMinScale(float minScale) {
        this.minScale = minScale;
    }

    public void setScrollSpeed(float scrollSpeed) {
        this.scrollSpeed = scrollSpeed;
    }
}
