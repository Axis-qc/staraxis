package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Array;
import staraxis.ui.FontProvider;
import staraxis.ui.effects.VectorSelectBoxEffect;

import java.util.function.Consumer;

/**
 * 矢量下拉选择框控件。
 *
 * 使用 ShapeRenderer 绘制按钮和下拉列表，
 * 替代 Scene2D 中依赖 Skin 的 SelectBox 控件。
 */
public class VectorSelectBox extends Actor {

    private static final VectorSelectBoxEffect DEFAULT_EFFECT = VectorSelectBoxEffect.fromMap("default", new java.util.HashMap<>());

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private final VectorSelectBoxEffect effect;
    private Array<String> items = new Array<>();
    private int selectedIndex;
    private boolean hovered;
    private boolean open;
    private Consumer<String> onChange;

    // 下拉列表浮层
    private Actor overlayBlocker;
    private com.badlogic.gdx.scenes.scene2d.ui.Table popupTable;
    private float popupWidth = 220;
    private float popupHeight = 150;

    public VectorSelectBox(ShapeRenderer sr, BitmapFont font) {
        this(sr, font, DEFAULT_EFFECT);
    }

    public VectorSelectBox(ShapeRenderer sr, BitmapFont font, VectorSelectBoxEffect effect) {
        this.sr = sr;
        this.font = font;
        this.effect = effect != null ? effect : DEFAULT_EFFECT;
        setSize(220, 32);

        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (x >= 0 && x <= getWidth() && y >= 0 && y <= getHeight()) {
                    togglePopup();
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

    public void setItems(String... items) {
        this.items.clear();
        for (String s : items) this.items.add(s);
        if (selectedIndex >= this.items.size) selectedIndex = 0;
    }

    public void setSelected(String item) {
        for (int i = 0; i < items.size; i++) {
            if (items.get(i).equals(item)) {
                selectedIndex = i;
                return;
            }
        }
    }

    public String getSelected() {
        return items.size > selectedIndex ? items.get(selectedIndex) : "";
    }

    public void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange;
    }

    private void togglePopup() {
        open = !open;
        if (open) {
            showPopup();
        } else {
            hidePopup();
        }
    }

    private void showPopup() {
        Stage stage = getStage();
        if (stage == null) return;

        popupWidth = getWidth();
        float itemH = 28f;
        popupHeight = Math.min(items.size * itemH + 8, 200);

        // 创建阻挡层，点击外部关闭
        overlayBlocker = new Actor();
        overlayBlocker.setTouchable(Touchable.enabled);
        overlayBlocker.setSize(stage.getWidth(), stage.getHeight());
        overlayBlocker.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                hidePopup();
            }
        });
        stage.addActor(overlayBlocker);

        // 创建弹出列表 — 使用舞台坐标定位
        Vector2 stagePos = localToStageCoordinates(new Vector2(0, 0));
        popupTable = new com.badlogic.gdx.scenes.scene2d.ui.Table();
        popupTable.setTouchable(Touchable.enabled);
        float sx = stagePos.x;
        float sy = stagePos.y - popupHeight;
        popupTable.setPosition(sx, sy);
        popupTable.setSize(popupWidth, popupHeight);

        for (int i = 0; i < items.size; i++) {
            final int idx = i;
            String item = items.get(i);
            VectorButton btn = new VectorButton(sr, font, item, () -> {
                selectedIndex = idx;
                if (onChange != null) onChange.accept(getSelected());
                hidePopup();
            });
            btn.setSize(popupWidth - 8, itemH);
            popupTable.add(btn).width(popupWidth - 8).height(itemH).padTop(2).row();
        }

        stage.addActor(popupTable);
    }

    private void hidePopup() {
        open = false;
        if (overlayBlocker != null) {
            overlayBlocker.remove();
            overlayBlocker = null;
        }
        if (popupTable != null) {
            popupTable.remove();
            popupTable = null;
        }
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

        // 按钮背景
        Color bgColor;
        if (open) bgColor = effect.button.openColor;
        else if (hovered) bgColor = effect.button.hoverColor;
        else bgColor = effect.button.color;
        sr.setColor(bgColor.r, bgColor.g, bgColor.b, bgColor.a * alpha);
        sr.begin(ShapeType.Filled);
        if (effect.button.cornerRadius > 0) {
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

        // 下拉箭头
        sr.setColor(effect.text.color.r, effect.text.color.g, effect.text.color.b, effect.text.color.a * alpha);
        sr.begin(ShapeType.Filled);
        float arrowX = x + w - 16;
        float arrowY = y + h / 2f;
        float arrowSize = 5;
        sr.triangle(arrowX - arrowSize, arrowY - arrowSize / 2f,
                arrowX + arrowSize, arrowY - arrowSize / 2f,
                arrowX, arrowY + arrowSize / 2f);
        sr.end();

        batch.begin();

        // 选中文字
        if (font != null && items.size > 0 && selectedIndex < items.size) {
            float oldScaleX = font.getData().scaleX;
            float oldScaleY = font.getData().scaleY;
            float scale = Math.max(0.1f, effect.text.size / FontProvider.VECTOR_FONT_GEN_SIZE);
            font.getData().setScale(scale);

            font.setColor(effect.text.color.r, effect.text.color.g, effect.text.color.b, effect.text.color.a * alpha);
            String displayText = items.get(selectedIndex);
            layout.setText(font, displayText);
            float textX = x + 10;
            float textY = y + (h + font.getCapHeight()) / 2f;
            font.draw(batch, displayText, textX, textY);

            font.setColor(Color.WHITE);
            font.getData().setScale(oldScaleX, oldScaleY);
        }
    }
}
