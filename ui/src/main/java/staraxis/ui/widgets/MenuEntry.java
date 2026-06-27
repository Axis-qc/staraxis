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
    private boolean selected;
    private float hoverProgress;
    private float pulse;

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

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        pulse += delta;
        float target = hovered || selected ? 1f : 0f;
        float alpha = 1f - (float) Math.exp(-Math.max(1f, effect.hover.speed) * delta);
        hoverProgress += (target - hoverProgress) * alpha;
        if (Math.abs(hoverProgress - target) < 0.001f) {
            hoverProgress = target;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float p = Interpolation.smooth.apply(hoverProgress);
        float x = getX() + p * effect.hover.shiftX;
        float y = getY();
        float h = getHeight();
        float baseBulletSize = effect.bullet.size;
        float pulseScale = hovered || selected ? (0.04f + 0.04f * (float) Math.sin(pulse * 7f)) * p : 0f;
        float bulletSize = baseBulletSize * (1f + 0.42f * p + pulseScale);
        float bulletCenterX = x + baseBulletSize * 0.5f;
        float bulletCenterY = y + h * 0.5f;

        batch.end();

        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.setTransformMatrix(batch.getTransformMatrix());

        if (effect.bullet.glow && p > 0.001f) {
            float glowPulse = 0.85f + 0.15f * (float) Math.sin(pulse * 6f);
            float glowRadius = effect.bullet.glowRadius * p * glowPulse;
            sr.setColor(effect.bullet.hoverColor.r, effect.bullet.hoverColor.g, effect.bullet.hoverColor.b,
                    p * effect.bullet.glowAlpha);
            sr.begin(ShapeType.Filled);
            sr.circle(bulletCenterX, bulletCenterY, glowRadius);
            sr.end();
        }

        Color bulletColor = effect.bullet.color.cpy().lerp(effect.bullet.hoverColor, p);
        sr.setColor(bulletColor);
        sr.begin(ShapeType.Filled);
        sr.circle(bulletCenterX, bulletCenterY, bulletSize / 2f);
        sr.end();

        batch.begin();

        float oldScaleX = font.getData().scaleX;
        float oldScaleY = font.getData().scaleY;
        float scale = Math.max(0.1f, effect.text.size / 96f);
        font.getData().setScale(scale);

        Color textColor = effect.text.color.cpy().lerp(effect.text.hoverColor, p);
        font.setColor(textColor);
        float textX = x + baseBulletSize + 15f;
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
        font.getData().setScale(oldScaleX, oldScaleY);
    }
}
