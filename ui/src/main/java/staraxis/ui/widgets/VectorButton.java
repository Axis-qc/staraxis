package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import staraxis.ui.FontProvider;
import staraxis.ui.effects.VectorButtonEffect;

public class VectorButton extends Actor {

    private static final VectorButtonEffect DEFAULT_EFFECT = VectorButtonEffect.fromMap("default", new java.util.HashMap<>());

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final VectorButtonEffect effect;
    private String text;

    private boolean hovered;
    private boolean pressed;

    public VectorButton(ShapeRenderer sr, BitmapFont font, String text, Runnable onClick) {
        this(sr, font, DEFAULT_EFFECT, text, onClick);
    }

    public VectorButton(ShapeRenderer sr, BitmapFont font, VectorButtonEffect effect, String text, Runnable onClick) {
        this.sr = sr;
        this.font = font;
        this.effect = effect != null ? effect : DEFAULT_EFFECT;
        this.text = text;

        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                // 只响应左键，右键返回 false 让事件穿透（否则右键点按钮会误触发 onClick）喵
                if (button != com.badlogic.gdx.Input.Buttons.LEFT) {
                    return false;
                }
                pressed = true;
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (button != com.badlogic.gdx.Input.Buttons.LEFT) {
                    return;
                }
                pressed = false;
                if (x >= 0 && x <= getWidth() && y >= 0 && y <= getHeight() && onClick != null) {
                    onClick.run();
                }
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                hovered = true;
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                hovered = false;
                pressed = false;
            }
        });
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();

        // 兼容 Scene2D setColor alpha（用于 Tab 高亮等场景）
        float alpha = getColor().a * parentAlpha;

        batch.end();

        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.setTransformMatrix(batch.getTransformMatrix());
        Color bg;
        if (pressed) {
            bg = effect.background.pressedColor;
        } else if (hovered) {
            bg = effect.background.hoverColor;
        } else {
            bg = effect.background.color;
        }
        sr.setColor(bg.r, bg.g, bg.b, bg.a * alpha);
        sr.begin(ShapeType.Filled);
        sr.rect(x, y, w, h);
        sr.end();

        if (effect.border.width > 0) {
            Color bc = effect.border.color;
            sr.setColor(bc.r, bc.g, bc.b, bc.a * alpha);
            sr.begin(ShapeType.Line);
            sr.rect(x, y, w, h);
            sr.end();
        }

        if (effect.accent.enabled && (hovered || pressed)) {
            Color ac = effect.accent.color;
            sr.setColor(ac.r, ac.g, ac.b, ac.a * alpha);
            sr.begin(ShapeType.Filled);
            sr.rect(x, y, effect.accent.width, h);
            sr.end();
        }

        batch.begin();

        float oldScaleX = font.getData().scaleX;
        float oldScaleY = font.getData().scaleY;
        font.getData().setScale(effect.text.size / FontProvider.VECTOR_FONT_GEN_SIZE);
        Color textColor = hovered ? effect.text.hoverColor : effect.text.color;
        font.setColor(textColor.r, textColor.g, textColor.b, textColor.a * alpha);
        font.draw(batch, text, x + 14, y + (h + font.getCapHeight()) / 2f);
        font.setColor(Color.WHITE);
        font.getData().setScale(oldScaleX, oldScaleY);
    }
}
