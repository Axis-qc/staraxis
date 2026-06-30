package staraxis.ui.widgets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import staraxis.ui.FontProvider;
import staraxis.ui.effects.VectorTextFieldEffect;

import java.util.function.Consumer;

/**
 * 矢量文本输入框控件。
 *
 * 使用 ShapeRenderer 绘制背景和光标，BitmapFont 渲染文字，
 * 替代 Scene2D 中依赖 Skin 的 TextField 控件。
 */
public class VectorTextField extends Actor {

    private static final VectorTextFieldEffect DEFAULT_EFFECT = VectorTextFieldEffect.fromMap("default", new java.util.HashMap<>());

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final VectorTextFieldEffect effect;
    private final GlyphLayout layout = new GlyphLayout();

    private String text = "";
    private String messageText = "";
    private int cursorPos;
    private boolean focused;
    private boolean passwordMode;
    private float blinkTimer;
    private boolean cursorVisible = true;
    private Consumer<String> onChange;

    public VectorTextField(ShapeRenderer sr, BitmapFont font, String text) {
        this(sr, font, DEFAULT_EFFECT, text);
    }

    public VectorTextField(ShapeRenderer sr, BitmapFont font, VectorTextFieldEffect effect, String text) {
        this.sr = sr;
        this.font = font;
        this.effect = effect != null ? effect : DEFAULT_EFFECT;
        this.text = text != null ? text : "";
        this.cursorPos = this.text.length();

        setSize(200, 32);

        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                Gdx.input.setOnscreenKeyboardVisible(true);
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                getStage().setKeyboardFocus(VectorTextField.this);
            }
        });

        addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                VectorTextField.this.focused = focused;
                if (!focused) {
                    Gdx.input.setOnscreenKeyboardVisible(false);
                }
                cursorVisible = focused;
                blinkTimer = 0;
            }
        });
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
        this.cursorPos = this.text.length();
    }

    public String getText() {
        return text;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText != null ? messageText : "";
    }

    public void setPasswordMode(boolean passwordMode) {
        this.passwordMode = passwordMode;
    }

    public void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (focused) {
            blinkTimer += delta;
            if (blinkTimer >= effect.cursor.blinkRate) {
                blinkTimer -= effect.cursor.blinkRate;
                cursorVisible = !cursorVisible;
            }
        }
    }

    public boolean handleKeyDown(int keycode) {
        if (!focused) return false;

        if (keycode == Input.Keys.LEFT) {
            if (cursorPos > 0) cursorPos--;
            return true;
        }
        if (keycode == Input.Keys.RIGHT) {
            if (cursorPos < text.length()) cursorPos++;
            return true;
        }
        if (keycode == Input.Keys.HOME) {
            cursorPos = 0;
            return true;
        }
        if (keycode == Input.Keys.END) {
            cursorPos = text.length();
            return true;
        }
        if (keycode == Input.Keys.DEL || keycode == Input.Keys.FORWARD_DEL) {
            if (cursorPos < text.length()) {
                text = text.substring(0, cursorPos) + text.substring(cursorPos + 1);
                fireOnChange();
            }
            return true;
        }

        return false;
    }

    public boolean handleKeyTyped(char character) {
        if (!focused) return false;

        if (character == '\b') {
            if (cursorPos > 0) {
                text = text.substring(0, cursorPos - 1) + text.substring(cursorPos);
                cursorPos--;
                fireOnChange();
            }
            return true;
        }

        if (character == '\r' || character == '\n') {
            return true;
        }

        if (Character.isISOControl(character)) return true;

        text = text.substring(0, cursorPos) + character + text.substring(cursorPos);
        cursorPos++;
        fireOnChange();
        return true;
    }

    private void fireOnChange() {
        if (onChange != null) onChange.accept(text);
    }

    private String getDisplayText() {
        if (passwordMode) {
            return "*".repeat(text.length());
        }
        return text;
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

        // 背景
        Color bgColor = focused ? effect.background.focusedColor : effect.background.color;
        sr.setColor(bgColor.r, bgColor.g, bgColor.b, bgColor.a * alpha);
        sr.begin(ShapeType.Filled);
        if (effect.background.cornerRadius > 0) {
            sr.rect(x, y, w, h);
        } else {
            sr.rect(x, y, w, h);
        }
        sr.end();

        // 边框
        if (effect.border.width > 0) {
            Color bc = effect.border.color;
            sr.setColor(bc.r, bc.g, bc.b, bc.a * alpha);
            sr.begin(ShapeType.Line);
            sr.rect(x, y, w, h);
            sr.end();
        }

        batch.begin();

        // 文字
        float oldScaleX = font.getData().scaleX;
        float oldScaleY = font.getData().scaleY;
        float scale = Math.max(0.1f, effect.text.size / FontProvider.VECTOR_FONT_GEN_SIZE);
        font.getData().setScale(scale);

        String displayText = getDisplayText();
        boolean empty = displayText.isEmpty();
        String drawText = empty ? messageText : displayText;
        Color textColor;
        if (empty) {
            textColor = effect.text.placeholderColor;
        } else if (focused) {
            textColor = effect.text.focusedColor;
        } else {
            textColor = effect.text.color;
        }

        font.setColor(textColor.r, textColor.g, textColor.b, textColor.a * alpha);
        float textX = x + 8;
        float textY = y + (h + font.getCapHeight()) / 2f;
        layout.setText(font, drawText);

        // 裁剪文字到输入框范围
        float maxTextW = w - 16;
        String clipText = drawText;
        if (layout.width > maxTextW) {
            // 简单裁剪：取末尾可见字符
            for (int i = drawText.length(); i > 0; i--) {
                layout.setText(font, drawText.substring(drawText.length() - i));
                if (layout.width <= maxTextW) {
                    clipText = drawText.substring(drawText.length() - i);
                    break;
                }
            }
        }

        font.draw(batch, clipText, textX, textY);

        // 光标
        if (focused && cursorVisible && !empty) {
            String beforeCursor = displayText.substring(0, Math.min(cursorPos, displayText.length()));
            layout.setText(font, beforeCursor);
            float cursorX = textX + Math.min(layout.width, maxTextW);
            font.setColor(effect.cursor.color);
            batch.end();
            sr.setColor(effect.cursor.color.r, effect.cursor.color.g, effect.cursor.color.b,
                    effect.cursor.color.a * alpha);
            sr.begin(ShapeType.Filled);
            sr.rect(cursorX, y + 4, effect.cursor.width, h - 8);
            sr.end();
            batch.begin();
        }

        font.setColor(Color.WHITE);
        font.getData().setScale(oldScaleX, oldScaleY);
    }
}
