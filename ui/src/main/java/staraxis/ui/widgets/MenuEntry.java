package staraxis.ui.widgets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import staraxis.ui.FontProvider;
import staraxis.ui.effects.MenuEntryEffect;

public class MenuEntry extends Actor {

    private static final MenuEntryEffect DEFAULT_EFFECT = MenuEntryEffect.fromMap("default", new java.util.HashMap<>());

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final GlyphLayout layout;
    private final String text;
    private final String tagText;
    private final MenuEntryEffect effect;

    private boolean hovered;
    private boolean selected;
    private float hoverProgress;
    private float moveProgress;

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
        float target = hovered || selected ? 1f : 0f;

        // 边框：1 秒完成渐变
        if (hoverProgress != target) {
            float step = delta / effect.hover.speed;
            if (target > hoverProgress) {
                hoverProgress = Math.min(hoverProgress + step, 1f);
            } else {
                hoverProgress = Math.max(hoverProgress - step, 0f);
            }
        }

        // 位移/变色：0.25 秒完成
        float moveDuration = 0.25f;
        if (moveProgress != target) {
            float step = delta / moveDuration;
            if (target > moveProgress) {
                moveProgress = Math.min(moveProgress + step, 1f);
            } else {
                moveProgress = Math.max(moveProgress - step, 0f);
            }
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float pm = Interpolation.smooth.apply(moveProgress);   // 位移/变色用（快）
        float pb = Interpolation.smooth.apply(hoverProgress);  // 边框用（慢）
        float x = getX() + pm * effect.hover.shiftX;
        float y = getY();
        float h = getHeight();
        float baseBulletSize = effect.bullet.size;
        // 固定大小，取消脉冲动画和悬停缩放
        float bulletSize = baseBulletSize;
        float bulletCenterX = x + baseBulletSize * 0.5f;
        float bulletCenterY = y + h * 0.5f;

        batch.end();

        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.setTransformMatrix(batch.getTransformMatrix());

        // 显式启用混合，使 ShapeRenderer.Line 的 alpha 生效
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // 边框：p*p 平方缓入使初始帧极低 alpha，产生肉眼可见的渐入渐出过程
        Color bc = effect.bullet.hoverColor;
        sr.setColor(bc.r, bc.g, bc.b, pb * pb * parentAlpha);
        sr.begin(ShapeType.Line);
        sr.rect(getX() + 1.5f, getY() + 1.5f, getWidth() - 3f, getHeight() - 3f);
        sr.end();

        // 圆点固定大小，悬停只变色
        Color bulletColor = effect.bullet.color.cpy().lerp(effect.bullet.hoverColor, pm);
        sr.setColor(bulletColor);
        sr.begin(ShapeType.Filled);
        sr.circle(bulletCenterX, bulletCenterY, bulletSize / 2f);
        sr.end();

        batch.begin();

        float oldScaleX = font.getData().scaleX;
        float oldScaleY = font.getData().scaleY;
        float scale = Math.max(0.1f, effect.text.size / FontProvider.VECTOR_FONT_GEN_SIZE);
        font.getData().setScale(scale);

        Color textColor = effect.text.color.cpy().lerp(effect.text.hoverColor, pm);
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
