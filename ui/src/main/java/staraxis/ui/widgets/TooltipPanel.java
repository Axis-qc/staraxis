package staraxis.ui.widgets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import staraxis.ui.theme.UiTheme;

/**
 * TooltipPanel（浮动提示面板）喵。
 *
 * 暗色半透明背景 + 主题色边框，颜色跟随 UiTheme。
 * 内含 VectorLabel 子标签，文字颜色跟随 theme.text。
 */
public class TooltipPanel extends Group {

    private static final float PAD_X = 10f;
    private static final float PAD_Y = 6f;
    private static final float BORDER_WIDTH = 1.5f;
    /** 钉住进度环半径（px），位于面板右下角喵 */
    private static final float RING_RADIUS = 7f;
    /** 钉住进度环距面板边缘的边距（px）喵 */
    private static final float RING_MARGIN = 5f;
    /** 钉住进度环线宽（px）喵 */
    private static final float RING_WIDTH = 2.5f;

    private final ShapeRenderer sr;
    private final UiTheme theme;
    private final VectorLabel label;

    /** 钉住进度（0~1），> 0 时在右下角显示圆圈进度环，<= 0 时隐藏喵 */
    private float pinProgress;

    public TooltipPanel(ShapeRenderer sr, BitmapFont font, UiTheme theme) {
        this.sr = sr;
        this.theme = theme;
        setTouchable(Touchable.disabled);

        label = new VectorLabel(font, "", theme.text);
        label.setTouchable(Touchable.disabled);
        addActor(label);
    }

    public void setContent(String text) {
        label.setText(text);
        float w = label.getWidth() + PAD_X * 2f;
        float h = label.getHeight() + PAD_Y * 2f;
        setSize(w, h);
        label.setPosition(PAD_X, PAD_Y);
    }

    /** 设置钉住进度（0~1），由 HoverTooltipBinder 每帧驱动喵 */
    public void setPinProgress(float progress) {
        this.pinProgress = progress;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        float a = getColor().a * parentAlpha;

        batch.end();

        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.setTransformMatrix(batch.getTransformMatrix());

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // 背景 — 跟随 theme.panelBg
        Color bg = theme.panelBg;
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(bg.r, bg.g, bg.b, bg.a * a);
        sr.rect(x, y, w, h);
        sr.end();

        // 边框 — 跟随 theme.panelBorder
        Color border = theme.panelBorder;
        sr.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(BORDER_WIDTH);
        sr.setColor(border.r, border.g, border.b, border.a * a);
        sr.rect(x, y, w, h);
        sr.end();

        // 钉住进度环 — 右下角圆圈，从 12 点方向顺时针增长喵
        if (pinProgress > 0f) {
            float cx = x + w - RING_RADIUS - RING_MARGIN;
            float cy = y + RING_RADIUS + RING_MARGIN;
            Gdx.gl.glLineWidth(RING_WIDTH);
            sr.begin(ShapeRenderer.ShapeType.Line);
            // 背景圆环（主题色低透明度）喵
            sr.setColor(border.r, border.g, border.b, border.a * a * 0.25f);
            sr.circle(cx, cy, RING_RADIUS);
            // 前景进度弧（主题色）喵
            sr.setColor(border.r, border.g, border.b, border.a * a);
            sr.arc(cx, cy, RING_RADIUS, 90f, -360f * Math.min(pinProgress, 1f));
            sr.end();
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();

        super.draw(batch, parentAlpha);
    }
}
