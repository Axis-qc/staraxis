package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import staraxis.ui.FontProvider;
import staraxis.ui.effects.VectorLabelEffect;

public class VectorLabel extends Actor {

    private static final VectorLabelEffect DEFAULT_EFFECT = VectorLabelEffect.fromMap("default", new java.util.HashMap<>());

    private final BitmapFont font;
    private final VectorLabelEffect effect;
    private String text;
    private final GlyphLayout layout = new GlyphLayout();

    public VectorLabel(BitmapFont font, String text) {
        this(font, DEFAULT_EFFECT, text);
    }

    public VectorLabel(BitmapFont font, Color color) {
        this(font, VectorLabelEffect.fromMap("inline", new java.util.HashMap<>()), "");
        // 防御性拷贝：调用方传入的可能是 UiTheme 等共享颜色对象，
        // 直接引用会导致后续 setTextColor 的 .set() 就地修改污染共享对象喵
        this.effect.text.color = new Color(color);
    }

    public VectorLabel(BitmapFont font, String text, Color color) {
        this(font, VectorLabelEffect.fromMap("inline", new java.util.HashMap<>()), text);
        // 防御性拷贝，理由同上喵
        this.effect.text.color = new Color(color);
    }

    public VectorLabel(BitmapFont font, VectorLabelEffect effect, String text) {
        this.font = font;
        this.effect = effect != null ? effect : DEFAULT_EFFECT;
        this.text = text;
        setTouchable(Touchable.enabled);
        updateSize();
    }

    public void setText(String text) {
        this.text = text;
        updateSize();
    }

    public String getText() {
        return text;
    }

    public void setTextColor(Color color) {
        this.effect.text.color.set(color);
    }

    /**
     * 根据当前文本 + 字体缩放测算实际渲染尺寸，
     * 同步 Actor 的 width/height，使 Scene2D 命中测试（hit）可正确命中此 Actor。
     */
    private void updateSize() {
        if (text == null || text.isEmpty()) {
            setSize(0, 0);
            return;
        }
        float scale = Math.max(0.1f, effect.text.size / FontProvider.VECTOR_FONT_GEN_SIZE);
        float oldScaleX = font.getData().scaleX;
        float oldScaleY = font.getData().scaleY;
        font.getData().setScale(scale);
        layout.setText(font, text);
        font.getData().setScale(oldScaleX, oldScaleY);
        setSize(layout.width, layout.height);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float oldScaleX = font.getData().scaleX;
        float oldScaleY = font.getData().scaleY;
        float scale = Math.max(0.1f, effect.text.size / FontProvider.VECTOR_FONT_GEN_SIZE);
        font.getData().setScale(scale);
        font.setColor(effect.text.color);
        // 文本顶部 = Actor 顶部（y + height），文字向下填充 Actor 区域
        font.draw(batch, text, getX(), getY() + getHeight());
        font.setColor(Color.WHITE);
        font.getData().setScale(oldScaleX, oldScaleY);
    }
}
