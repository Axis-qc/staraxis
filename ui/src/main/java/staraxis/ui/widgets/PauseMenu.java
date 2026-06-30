package staraxis.ui.widgets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import staraxis.ui.Gui;

/**
 * PauseMenu（暂停菜单）— 半透明遮罩 + 功能按钮列表。
 *
 * 在游戏运行时按 ESC 弹出，覆盖在游戏画面上。提供：
 * 返回游戏、保存游戏、加载游戏、设置、返回主界面、退出游戏。
 */
public class PauseMenu extends Group {

    private static final float BTN_WIDTH = 240f;
    private static final float BTN_HEIGHT = 44f;
    private static final float BTN_GAP = 8f;
    private static final float OVERLAY_ALPHA = 0.55f;

    private final Gui gui;
    private final ShapeRenderer sr;
    private final BitmapFont font;
    private float viewW, viewH;
    private boolean menuVisible;

    public PauseMenu(Gui gui, ShapeRenderer sr, BitmapFont font) {
        this.gui = gui;
        this.sr = sr;
        this.font = font;
        setTouchable(Touchable.enabled);
    }

    /** 切换暂停菜单显示/隐藏 */
    public void toggle() {
        if (menuVisible) {
            hide();
        } else {
            show();
        }
    }

    public boolean isMenuVisible() {
        return menuVisible;
    }

    public void show() {
        if (menuVisible) return;
        menuVisible = true;
        rebuild();
    }

    public void hide() {
        if (!menuVisible) return;
        menuVisible = false;
        clear();
    }

    /** 重建界面（在窗口尺寸变化时也需调用） */
    private void rebuild() {
        clear();
        viewW = gui.getStage().getWidth();
        viewH = gui.getStage().getHeight();

        // 按钮文字与动作
        String[][] items = {
            { "返回游戏", "RESUME" },
            { "保存游戏", "SAVE_GAME" },
            { "加载游戏", "LOAD_GAME" },
            { "设置", "OPEN_SETTINGS" },
            { "返回主界面", "RETURN_TO_MAIN_MENU" },
            { "退出游戏", "EXIT_CLICK" },
        };

        float startY = (viewH - items.length * (BTN_HEIGHT + BTN_GAP)) / 2f + BTN_HEIGHT;

        for (int i = 0; i < items.length; i++) {
            final String action = items[i][1];
            VectorButton btn = new VectorButton(sr, font, items[i][0], () -> {
                hide();
                if ("RESUME".equals(action)) {
                    // hide() 已处理
                } else if ("SAVE_GAME".equals(action)) {
                    // TODO: 保存游戏功能尚未实现
                } else {
                    gui.dispatchAction(action);
                }
            });
            btn.setSize(BTN_WIDTH, BTN_HEIGHT);
            btn.setPosition((viewW - BTN_WIDTH) / 2f, startY - i * (BTN_HEIGHT + BTN_GAP));
            addActor(btn);
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!menuVisible) return;

        // 半透明遮罩
        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.setTransformMatrix(batch.getTransformMatrix());
        sr.setColor(0f, 0f, 0f, OVERLAY_ALPHA);
        sr.begin(ShapeType.Filled);
        sr.rect(0, 0, viewW, viewH);
        sr.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
        batch.begin();

        // 绘制按钮
        super.draw(batch, parentAlpha);
    }
}