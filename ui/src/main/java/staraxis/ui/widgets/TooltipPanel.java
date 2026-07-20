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

    private final ShapeRenderer sr;
    private final UiTheme theme;
    private final VectorLabel label;

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

        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();

        super.draw(batch, parentAlpha);
    }
}
