package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Actor;
import staraxis.ui.effects.VectorProgressBarEffect;

/**
 * 矢量进度条控件。
 *
 * 使用 ShapeRenderer 绘制背景条和填充条，
 * 替代 Scene2D 中依赖 Skin 的 ProgressBar 控件。
 */
public class VectorProgressBar extends Actor {

    private static final VectorProgressBarEffect DEFAULT_EFFECT = VectorProgressBarEffect.fromMap("default", new java.util.HashMap<>());

    private final ShapeRenderer sr;
    private final VectorProgressBarEffect effect;
    private float min;
    private float max;
    private float value;
    private float animateSpeed;
    private float displayValue;

    public VectorProgressBar(ShapeRenderer sr, float min, float max, float step, boolean vertical) {
        this(sr, DEFAULT_EFFECT, min, max, step, vertical);
    }

    public VectorProgressBar(ShapeRenderer sr, VectorProgressBarEffect effect,
                             float min, float max, float step, boolean vertical) {
        this.sr = sr;
        this.effect = effect != null ? effect : DEFAULT_EFFECT;
        this.min = min;
        this.max = max;
        this.value = min;
        this.displayValue = min;
        this.animateSpeed = 0.1f;
        setSize(200, 24);
    }

    public void setValue(float value) {
        this.value = Math.max(min, Math.min(max, value));
    }

    public float getValue() {
        return value;
    }

    public void setAnimateDuration(float duration) {
        this.animateSpeed = duration > 0 ? 1f / duration : 0.1f;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        // 平滑动画
        float diff = value - displayValue;
        if (Math.abs(diff) > 0.001f) {
            displayValue += diff * Math.min(1f, animateSpeed * delta * 60f);
        } else {
            displayValue = value;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        float alpha = getColor().a * parentAlpha;
        float range = max - min;
        float fillRatio = range > 0 ? (displayValue - min) / range : 0;
        float fillW = w * fillRatio;

        batch.end();

        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.setTransformMatrix(batch.getTransformMatrix());

        // 背景
        Color bg = effect.background.color;
        sr.setColor(bg.r, bg.g, bg.b, bg.a * alpha);
        sr.begin(ShapeType.Filled);
        if (effect.background.cornerRadius > 0) {
            sr.rect(x, y, w, h);
        } else {
            sr.rect(x, y, w, h);
        }
        sr.end();

        // 填充
        if (fillRatio > 0) {
            Color fc = effect.fill.color;
            sr.setColor(fc.r, fc.g, fc.b, fc.a * alpha);
            sr.begin(ShapeType.Filled);
            if (effect.fill.cornerRadius > 0 && fillRatio < 1f) {
                sr.rect(x, y, fillW, h);
            } else {
                sr.rect(x, y, fillW, h);
            }
            sr.end();
        }

        // 边框
        if (effect.border.width > 0) {
            Color bc = effect.border.color;
            sr.setColor(bc.r, bc.g, bc.b, bc.a * alpha);
            sr.begin(ShapeType.Line);
            sr.rect(x, y, w, h);
            sr.end();
        }

        batch.begin();
    }
}
