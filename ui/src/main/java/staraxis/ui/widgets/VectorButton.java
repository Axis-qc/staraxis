package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import staraxis.ui.effects.VectorButtonEffect;

public class VectorButton extends Actor {

    private static final VectorButtonEffect DEFAULT_EFFECT = VectorButtonEffect.fromMap("default", new java.util.HashMap<>());

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final VectorButtonEffect effect;
    private String text;
    private Runnable onClick;

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
        this.onClick = onClick;

        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                pressed = true;
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
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

        batch.end();

        sr.setProjectionMatrix(batch.getProjectionMatrix());
        if (pressed) {
            sr.setColor(effect.background.pressedColor);
        } else if (hovered) {
            sr.setColor(effect.background.hoverColor);
        } else {
            sr.setColor(effect.background.color);
        }
        sr.begin(ShapeType.Filled);
        sr.rect(x, y, w, h);
        sr.end();

        if (effect.border.width > 0) {
            sr.setColor(effect.border.color);
            sr.begin(ShapeType.Line);
            sr.rect(x, y, w, h);
            sr.end();
        }

        if (effect.accent.enabled && (hovered || pressed)) {
            sr.setColor(effect.accent.color);
            sr.begin(ShapeType.Filled);
            sr.rect(x, y, effect.accent.width, h);
            sr.end();
        }

        batch.begin();

        Color textColor = hovered ? effect.text.hoverColor : effect.text.color;
        font.setColor(textColor);
        font.draw(batch, text, x + 14, y + (h + font.getCapHeight()) / 2f);
        font.setColor(Color.WHITE);
    }
}
