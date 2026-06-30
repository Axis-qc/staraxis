package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import staraxis.ui.i18n.I18nService;

/**
 * 开发中提示对话框（矢量控件版）。
 *
 * 使用 VectorDialog 实现，不依赖 Skin。
 */
public class DevelopingDialog {

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final I18nService i18n;

    public DevelopingDialog(ShapeRenderer sr, BitmapFont font, I18nService i18n) {
        this.sr = sr;
        this.font = font;
        this.i18n = i18n;
    }

    public void show(Stage stage) {
        VectorDialog dlg = new VectorDialog(sr, font,
                i18n.get("dialog.developing.title"),
                i18n.get("dialog.developing.text"));
        dlg.setButton(i18n.get("dialog.developing.confirm"), dlg::hide);
        dlg.setSize(320, 180);
        dlg.show(stage);
    }
}
