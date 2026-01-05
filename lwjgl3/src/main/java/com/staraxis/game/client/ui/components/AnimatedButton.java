package com.staraxis.game.client.ui.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

/**
 * 带有动态反馈的按钮 (Animated Button) 支持悬停缩放、颜色渐变等交互动画。
 */
public class AnimatedButton extends TextButton {

    private float hoverScale = 1.1f;
    private float animationDuration = 0.15f;
    private boolean isHovered = false;

    public AnimatedButton(String text, Skin skin) {
        super(text, skin);
        init();
    }

    public AnimatedButton(String text, Skin skin, String styleName) {
        super(text, skin, styleName);
        init();
    }

    private void init() {
        setTransform(true); // 允许缩放和旋转
        setOrigin(getWidth() / 2, getHeight() / 2);

        addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (pointer == -1) { // 仅限鼠标悬停
                    isHovered = true;
                    clearActions();
                    addAction(Actions.parallel(
                            Actions.scaleTo(hoverScale, hoverScale, animationDuration, Interpolation.pow2Out),
                            Actions.color(Color.CYAN, animationDuration, Interpolation.pow2Out)
                    ));
                }
                super.enter(event, x, y, pointer, fromActor);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (pointer == -1) {
                    isHovered = false;
                    clearActions();
                    addAction(Actions.parallel(
                            Actions.scaleTo(1.0f, 1.0f, animationDuration, Interpolation.pow2In),
                            Actions.color(Color.WHITE, animationDuration, Interpolation.pow2In)
                    ));
                }
                super.exit(event, x, y, pointer, toActor);
            }
        });
    }

    @Override
    protected void sizeChanged() {
        super.sizeChanged();
        setOrigin(getWidth() / 2, getHeight() / 2);
    }

    public void setHoverScale(float hoverScale) {
        this.hoverScale = hoverScale;
    }

    public void setAnimationDuration(float animationDuration) {
        this.animationDuration = animationDuration;
    }
}
