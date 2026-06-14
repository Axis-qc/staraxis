package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;

public class MenuEntry extends Actor {

    private static final Color TEXT_COLOR = new Color(0.53f, 0.53f, 0.53f, 1f);
    private static final Color TEXT_HOVER = new Color(1f, 1f, 1f, 1f);
    private static final Color BULLET_COLOR = new Color(0.53f, 0.53f, 0.53f, 1f);
    private static final Color BULLET_HOVER = new Color(0.3f, 0.65f, 0.95f, 1f);
    private static final Color TAG_BG = new Color(0.3f, 0.65f, 0.95f, 0.24f);
    private static final Color TAG_TEXT = new Color(1f, 1f, 1f, 1f);

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final GlyphLayout layout;
    private final String text;
    private final String tagText;
    private final Runnable onClick;

    private boolean hovered;
    private float hoverProgress;
    private static final float HOVER_SHIFT = 10f;
    private static final float HOVER_SPEED = 8f;

    public MenuEntry(ShapeRenderer sr, BitmapFont font, String text, Runnable onClick) {
        this(sr, font, text, null, onClick);
    }

    public MenuEntry(ShapeRenderer sr, BitmapFont font, String text, String tagText, Runnable onClick) {
        this.sr = sr;
        this.font = font;
        this.layout = new GlyphLayout();
        this.text = text;
        this.tagText = tagText;
        this.onClick = onClick;

        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
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
            }
        });
    }

    @Override
    public void act(float delta) {
        float target = hovered ? 1f : 0f;
        if (Math.abs(hoverProgress - target) > 0.001f) {
            hoverProgress = Interpolation.fade.apply(hoverProgress, target, Math.min(1f, delta * HOVER_SPEED));
        } else {
            hoverProgress = target;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX() + hoverProgress * HOVER_SHIFT;
        float y = getY();
        float h = getHeight();
        float bulletSize = 8f;
        float bulletX = x;
        float bulletY = y + (h - bulletSize) / 2f;

        batch.end();

        sr.setProjectionMatrix(batch.getProjectionMatrix());

        Color bulletColor = BULLET_COLOR.cpy().lerp(BULLET_HOVER, hoverProgress);
        sr.setColor(bulletColor);
        sr.begin(ShapeType.Filled);
        sr.circle(bulletX + bulletSize / 2f, bulletY + bulletSize / 2f, bulletSize / 2f);
        sr.end();

        if (hoverProgress > 0.01f) {
            sr.setColor(BULLET_HOVER.r, BULLET_HOVER.g, BULLET_HOVER.b, hoverProgress * 0.3f);
            sr.begin(ShapeType.Filled);
            sr.circle(bulletX + bulletSize / 2f, bulletY + bulletSize / 2f, bulletSize * 1.5f);
            sr.end();
        }

        batch.begin();

        Color textColor = TEXT_COLOR.cpy().lerp(TEXT_HOVER, hoverProgress);
        font.setColor(textColor);
        float textX = bulletX + bulletSize + 15f;
        float textY = y + (h + font.getCapHeight()) / 2f;
        layout.setText(font, text);
        font.draw(batch, layout, textX, textY);

        if (tagText != null) {
            float tagOffsetX = textX + layout.width + 12f;
            layout.setText(font, tagText);
            float tagW = layout.width + 12f;
            float tagH = font.getCapHeight() + 6f;
            float tagY_base = y + (h - tagH) / 2f;

            batch.end();
            sr.setColor(TAG_BG);
            sr.begin(ShapeType.Filled);
            sr.rect(tagOffsetX, tagY_base, tagW, tagH);
            sr.end();
            batch.begin();

            font.setColor(TAG_TEXT);
            float tagTextX = tagOffsetX + 6f;
            font.draw(batch, tagText, tagTextX, y + (h + font.getCapHeight()) / 2f);
        }

        font.setColor(Color.WHITE);
    }
}
