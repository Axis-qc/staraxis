package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Actor;
import staraxis.ui.effects.VectorImageEffect;

/**
 * 矢量图像控件。
 *
 * 使用 ShapeRenderer 绘制纯色矩形/圆角矩形等基本图形，
 * 替代 Scene2D 中依赖 Skin Drawable 的 Image 控件。
 */
public class VectorImage extends Actor {

    private static final VectorImageEffect DEFAULT_EFFECT = VectorImageEffect.fromMap("default", new java.util.HashMap<>());

    private final ShapeRenderer sr;
    private final VectorImageEffect effect;
    private Color tintColor;

    public VectorImage(ShapeRenderer sr) {
        this(sr, DEFAULT_EFFECT);
    }

    public VectorImage(ShapeRenderer sr, VectorImageEffect effect) {
        this.sr = sr;
        this.effect = effect != null ? effect : DEFAULT_EFFECT;
        setSize(16, 16);
    }

    public void setTint(Color color) {
        this.tintColor = color;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        float alpha = getColor().a * parentAlpha;

        batch.end();

        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.setTransformMatrix(batch.getTransformMatrix());

        Color bg = tintColor != null ? tintColor : effect.background.color;
        sr.setColor(bg.r, bg.g, bg.b, bg.a * alpha);
        sr.begin(ShapeType.Filled);

        if (effect.border.radius > 0) {
            // 简单圆角矩形近似：直接画矩形（不精确圆角但性能好）
            sr.rect(x, y, w, h);
        } else {
            sr.rect(x, y, w, h);
        }
        sr.end();

        if (effect.border.width > 0) {
            Color bc = effect.border.color;
            sr.setColor(bc.r, bc.g, bc.b, bc.a * alpha);
            sr.begin(ShapeType.Line);
            if (effect.border.radius > 0) {
                sr.rect(x, y, w, h);
            } else {
                sr.rect(x, y, w, h);
            }
            sr.end();
        }

        batch.begin();
    }
}
