package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;

public class VectorLabel extends Actor {

    private final BitmapFont font;
    private String text;
    private Color textColor;

    public VectorLabel(BitmapFont font, String text) {
        this(font, text, new Color(0.75f, 0.75f, 0.75f, 1f));
    }

    public VectorLabel(BitmapFont font, String text, Color color) {
        this.font = font;
        this.text = text;
        this.textColor = color;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setTextColor(Color color) {
        this.textColor = color;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        font.setColor(textColor);
        font.draw(batch, text, getX(), getY() + getHeight());
        font.setColor(Color.WHITE);
    }
}
