package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import staraxis.ui.effects.VectorLabelEffect;

public class VectorLabel extends Actor {

    private static final VectorLabelEffect DEFAULT_EFFECT = VectorLabelEffect.fromMap("default", new java.util.HashMap<>());

    private final BitmapFont font;
    private final VectorLabelEffect effect;
    private String text;

    public VectorLabel(BitmapFont font, String text) {
        this(font, DEFAULT_EFFECT, text);
    }

    public VectorLabel(BitmapFont font, Color color) {
        this(font, VectorLabelEffect.fromMap("inline", new java.util.HashMap<>()), "");
        this.effect.text.color = color;
    }

    public VectorLabel(BitmapFont font, String text, Color color) {
        this(font, VectorLabelEffect.fromMap("inline", new java.util.HashMap<>()), text);
        this.effect.text.color = color;
    }

    public VectorLabel(BitmapFont font, VectorLabelEffect effect, String text) {
        this.font = font;
        this.effect = effect != null ? effect : DEFAULT_EFFECT;
        this.text = text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setTextColor(Color color) {
        this.effect.text.color.set(color);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        font.setColor(effect.text.color);
        font.draw(batch, text, getX(), getY() + getHeight());
        font.setColor(Color.WHITE);
    }
}
