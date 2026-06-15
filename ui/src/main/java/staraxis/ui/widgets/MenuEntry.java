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
import staraxis.ui.effects.MenuEntryEffect;

public class MenuEntry extends Actor {

    private static final MenuEntryEffect DEFAULT_EFFECT = MenuEntryEffect.fromMap("default", new java.util.HashMap<>());

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final GlyphLayout layout;
    private final String text;
    private final String tagText;
    private final Runnable onClick;
    private final MenuEntryEffect effect;

    private boolean hovered;
    private float hoverProgress;

    public MenuEntry(ShapeRenderer sr, BitmapFont font, String text, Runnable onClick) {
        this(sr, font, DEFAULT_EFFECT, text, null, onClick);
    }

    public MenuEntry(ShapeRenderer sr, BitmapFont font, String text, String tagText, Runnable onClick) {
        this(sr, font, DEFAULT_EFFECT, text, tagText, onClick);
    }

    public MenuEntry(ShapeRenderer sr, BitmapFont font, MenuEntryEffect effect, String text, String tagText, Runnable onClick) {
        this.sr = sr;
        this.font = font;
        this.layout = new GlyphLayout();
        this.effect = effect != null ? effect : DEFAULT_EFFECT;
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
            hoverProgress = Interpolation.fade.apply(hoverProgress, target, Math.min(1f, delta * effect.hover.speed));
        } else {
            hoverProgress = target;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX() + hoverProgress * effect.hover.shiftX;
        float y = getY();
        float h = getHeight();
        float bulletSize = effect.bullet.size;
        float bulletX = x;
        float bulletY = y + (h - bulletSize) / 2f;

        batch.end();

        sr.setProjectionMatrix(batch.getProjectionMatrix());

        Color bulletColor = effect.bullet.color.cpy().lerp(effect.bullet.hoverColor, hoverProgress);
        sr.setColor(bulletColor);
        sr.begin(ShapeType.Filled);
        sr.circle(bulletX + bulletSize / 2f, bulletY + bulletSize / 2f, bulletSize / 2f);
        sr.end();

        if (effect.bullet.glow && hoverProgress > 0.01f) {
            sr.setColor(effect.bullet.hoverColor.r, effect.bullet.hoverColor.g, effect.bullet.hoverColor.b,
                    hoverProgress * effect.bullet.glowAlpha);
            sr.begin(ShapeType.Filled);
            sr.circle(bulletX + bulletSize / 2f, bulletY + bulletSize / 2f, effect.bullet.glowRadius);
            sr.end();
        }

        batch.begin();

        Color textColor = effect.text.color.cpy().lerp(effect.text.hoverColor, hoverProgress);
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
            sr.setColor(effect.tag.bgColor);
            sr.begin(ShapeType.Filled);
            sr.rect(tagOffsetX, tagY_base, tagW, tagH);
            sr.end();
            batch.begin();

            font.setColor(effect.tag.textColor);
            float tagTextX = tagOffsetX + 6f;
            font.draw(batch, tagText, tagTextX, y + (h + font.getCapHeight()) / 2f);
        }

        font.setColor(Color.WHITE);
    }
}
