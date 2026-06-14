package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;

public class VectorButton extends Actor {

    private static final Color NORMAL = new Color(0.15f, 0.15f, 0.15f, 0.85f);
    private static final Color HOVER = new Color(0.25f, 0.25f, 0.25f, 0.90f);
    private static final Color PRESSED = new Color(0.35f, 0.12f, 0.12f, 0.95f);
    private static final Color TEXT_NORMAL = new Color(0.75f, 0.75f, 0.75f, 1f);
    private static final Color TEXT_HOVER = new Color(1f, 1f, 1f, 1f);
    private static final Color ACCENT = new Color(0.3f, 0.65f, 0.95f, 1f);

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private String text;
    private Runnable onClick;

    private boolean hovered;
    private boolean pressed;

    public VectorButton(ShapeRenderer sr, BitmapFont font, String text, Runnable onClick) {
        this.sr = sr;
        this.font = font;
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
            sr.setColor(PRESSED);
        } else if (hovered) {
            sr.setColor(HOVER);
        } else {
            sr.setColor(NORMAL);
        }
        sr.begin(ShapeType.Filled);
        sr.rect(x, y, w, h);
        sr.end();

        if (hovered || pressed) {
            sr.setColor(ACCENT);
            sr.begin(ShapeType.Filled);
            sr.rect(x, y, 3, h);
            sr.end();
        }

        batch.begin();

        Color textColor = hovered ? TEXT_HOVER : TEXT_NORMAL;
        font.setColor(textColor);
        font.draw(batch, text, x + 14, y + (h + font.getCapHeight()) / 2f);
        font.setColor(Color.WHITE);
    }
}
