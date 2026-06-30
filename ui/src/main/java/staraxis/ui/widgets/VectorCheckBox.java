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
import staraxis.ui.effects.VectorCheckBoxEffect;

/**
 * 矢量复选框控件。
 *
 * 使用 ShapeRenderer 绘制复选框方框和勾选标记，
 * 替代 Scene2D 中依赖 Skin 的 CheckBox 控件。
 */
public class VectorCheckBox extends Actor {

    private static final VectorCheckBoxEffect DEFAULT_EFFECT = VectorCheckBoxEffect.fromMap("default", new java.util.HashMap<>());

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final VectorCheckBoxEffect effect;
    private String label;
    private boolean checked;
    private boolean hovered;
    private Runnable onChange;

    public VectorCheckBox(ShapeRenderer sr, BitmapFont font, String label, boolean checked, Runnable onChange) {
        this(sr, font, DEFAULT_EFFECT, label, checked, onChange);
    }

    public VectorCheckBox(ShapeRenderer sr, BitmapFont font, VectorCheckBoxEffect effect,
                          String label, boolean checked, Runnable onChange) {
        this.sr = sr;
        this.font = font;
        this.effect = effect != null ? effect : DEFAULT_EFFECT;
        this.label = label != null ? label : "";
        this.checked = checked;
        this.onChange = onChange;

        float boxSize = this.effect.box.size;
        float textWidth = estimateTextWidth();
        setSize(boxSize + 8 + textWidth, Math.max(boxSize, this.effect.text.size));

        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (x >= 0 && x <= getWidth() && y >= 0 && y <= getHeight()) {
                    toggle();
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

    private void toggle() {
        checked = !checked;
        if (onChange != null) onChange.run();
    }



    private float estimateTextWidth() {
        if (label == null || label.isEmpty() || font == null) return 0;
        float oldScaleX = font.getData().scaleX;
        float oldScaleY = font.getData().scaleY;
        float scale = Math.max(0.1f, effect.text.size / FontProvider.VECTOR_FONT_GEN_SIZE);
        font.getData().setScale(scale);
        com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
        layout.setText(font, label);
        float w = layout.width;
        font.getData().setScale(oldScaleX, oldScaleY);
        return w;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float boxSize = effect.box.size;
        float alpha = getColor().a * parentAlpha;
        float cornerRadius = effect.box.cornerRadius;

        batch.end();

        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.setTransformMatrix(batch.getTransformMatrix());

        // 复选框背景
        Color bgColor;
        if (checked) {
            bgColor = effect.box.checkedColor;
        } else if (hovered) {
            bgColor = effect.box.hoverColor;
        } else {
            bgColor = effect.box.color;
        }
        sr.setColor(bgColor.r, bgColor.g, bgColor.b, bgColor.a * alpha);
        sr.begin(ShapeType.Filled);
        drawRoundedRect(sr, x, y, boxSize, boxSize, cornerRadius);
        sr.end();

        // 勾选标记
        if (checked) {
            sr.setColor(effect.check.color.r, effect.check.color.g, effect.check.color.b,
                    effect.check.color.a * alpha);
            sr.begin(ShapeType.Line);
            float cx = x + boxSize * 0.2f;
            float cy = y + boxSize * 0.25f;
            float cw = boxSize * 0.6f;
            float ch = boxSize * 0.5f;
            sr.line(cx, cy, cx + cw * 0.4f, cy + ch);
            sr.line(cx + cw * 0.4f, cy + ch, cx + cw, cy);
            sr.end();
        }

        batch.begin();

        // 标签文字
        if (label != null && !label.isEmpty() && font != null) {
            float oldScaleX = font.getData().scaleX;
            float oldScaleY = font.getData().scaleY;
            float scale = Math.max(0.1f, effect.text.size / FontProvider.VECTOR_FONT_GEN_SIZE);
            font.getData().setScale(scale);
            Color textColor = hovered ? effect.text.hoverColor : effect.text.color;
            font.setColor(textColor.r, textColor.g, textColor.b, textColor.a * alpha);
            font.draw(batch, label, x + boxSize + 8, y + (boxSize + font.getCapHeight()) / 2f);
            font.setColor(Color.WHITE);
            font.getData().setScale(oldScaleX, oldScaleY);
        }
    }

    private void drawRoundedRect(ShapeRenderer sr, float x, float y, float w, float h, float r) {
        if (r <= 0) {
            sr.rect(x, y, w, h);
            return;
        }
        // 简化实现：仅画矩形（完整圆角需要更多三角形）
        sr.rect(x, y, w, h);
    }
}
