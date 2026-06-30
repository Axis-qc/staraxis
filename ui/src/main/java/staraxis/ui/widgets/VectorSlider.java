package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import staraxis.ui.effects.VectorSliderEffect;

import java.util.function.Consumer;

/**
 * 矢量滑块控件。
 *
 * 使用 ShapeRenderer 绘制轨道和旋钮，
 * 替代 Scene2D 中依赖 Skin 的 Slider 控件。
 */
public class VectorSlider extends Actor {

    private static final VectorSliderEffect DEFAULT_EFFECT = VectorSliderEffect.fromMap("default", new java.util.HashMap<>());

    private final ShapeRenderer sr;
    private final VectorSliderEffect effect;
    private float min;
    private float max;
    private float step;
    private float value;
    private boolean vertical;
    private boolean hovered;
    private boolean dragging;
    private Consumer<Float> onChange;

    public VectorSlider(ShapeRenderer sr, float min, float max, float step, boolean vertical) {
        this(sr, DEFAULT_EFFECT, min, max, step, vertical);
    }

    public VectorSlider(ShapeRenderer sr, VectorSliderEffect effect,
                        float min, float max, float step, boolean vertical) {
        this.sr = sr;
        this.effect = effect != null ? effect : DEFAULT_EFFECT;
        this.min = min;
        this.max = max;
        this.step = step;
        this.vertical = vertical;
        this.value = min;

        if (vertical) {
            setSize(this.effect.knob.size, 200);
        } else {
            setSize(200, this.effect.knob.size);
        }

        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                dragging = true;
                updateValue(x, y);
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                dragging = false;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (dragging) {
                    updateValue(x, y);
                }
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                hovered = true;
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (!dragging) hovered = false;
            }
        });
    }

    private void updateValue(float mx, float my) {
        float range = max - min;
        if (range <= 0) return;

        float ratio;
        if (vertical) {
            ratio = 1f - MathUtils.clamp(my / getHeight(), 0f, 1f);
        } else {
            ratio = MathUtils.clamp(mx / getWidth(), 0f, 1f);
        }

        float raw = min + ratio * range;
        if (step > 0) {
            raw = Math.round(raw / step) * step;
        }
        value = MathUtils.clamp(raw, min, max);
        if (onChange != null) onChange.accept(value);
    }

    public void setValue(float value) {
        this.value = MathUtils.clamp(value, min, max);
    }

    public float getValue() {
        return value;
    }

    public void setOnChange(Consumer<Float> onChange) {
        this.onChange = onChange;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        float alpha = getColor().a * parentAlpha;
        float range = max - min;
        float ratio = range > 0 ? (value - min) / range : 0;

        float trackH = effect.track.height;
        float knobSize = effect.knob.size;
        float knobHalf = knobSize / 2f;

        float trackX, trackY, trackW, trackHActual;
        float fillW, fillH;
        float knobCX, knobCY;

        if (vertical) {
            trackX = x + (w - trackH) / 2f;
            trackY = y;
            trackW = trackH;
            trackHActual = h;
            fillW = trackH;
            fillH = trackHActual * ratio;
            knobCX = x + w / 2f;
            knobCY = y + trackHActual * (1f - ratio);
        } else {
            trackX = x;
            trackY = y + (h - trackH) / 2f;
            trackW = w;
            trackHActual = trackH;
            fillW = trackW * ratio;
            fillH = trackH;
            knobCX = x + trackW * ratio;
            knobCY = y + h / 2f;
        }

        batch.end();

        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.setTransformMatrix(batch.getTransformMatrix());

        // 轨道背景
        Color tc = effect.track.color;
        sr.setColor(tc.r, tc.g, tc.b, tc.a * alpha);
        sr.begin(ShapeType.Filled);
        if (effect.track.cornerRadius > 0) {
            sr.rect(trackX, trackY, trackW, trackHActual);
        } else {
            sr.rect(trackX, trackY, trackW, trackHActual);
        }
        sr.end();

        // 填充部分
        if (ratio > 0) {
            Color fc = effect.track.fillColor;
            sr.setColor(fc.r, fc.g, fc.b, fc.a * alpha);
            sr.begin(ShapeType.Filled);
            if (vertical) {
                sr.rect(trackX, trackY, fillW, fillH);
            } else {
                sr.rect(trackX, trackY, fillW, fillH);
            }
            sr.end();
        }

        // 旋钮
        Color kc = hovered || dragging ? effect.knob.hoverColor : effect.knob.color;
        sr.setColor(kc.r, kc.g, kc.b, kc.a * alpha);
        sr.begin(ShapeType.Filled);
        if (effect.knob.cornerRadius > 0) {
            sr.circle(knobCX, knobCY, knobHalf);
        } else {
            sr.rect(knobCX - knobHalf, knobCY - knobHalf, knobSize, knobSize);
        }
        sr.end();

        batch.begin();
    }
}
