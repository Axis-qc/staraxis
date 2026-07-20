package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;

/**
 * SelectHomeConfirmDialog（选择母星系确认弹窗）喵。
 *
 * 在 Galaxy View 选择母星系模式下，点击恒星后弹出。
 * 使用 VectorDialog 实现，不依赖 Skin。
 */
public class SelectHomeConfirmDialog {

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private VectorDialog dialog;
    private Runnable onConfirm;
    private Runnable onCancel;

    public SelectHomeConfirmDialog(ShapeRenderer sr, BitmapFont font) {
        this.sr = sr;
        this.font = font;
    }

    /**
     * 显示确认弹窗喵。
     *
     * @param stage         所属舞台
     * @param systemName    星系名称/描述
     * @param isRecommended 是否为推荐星系（有宜居行星）
     * @param onConfirm     确认回调
     * @param onCancel      取消回调
     */
    public void show(Stage stage, String systemName, boolean isRecommended,
            Runnable onConfirm, Runnable onCancel) {
        hide();

        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        String title = "\u5728\u6B64\u661F\u7CFB\u5EFA\u7ACB\u5BB6\u56ED\uFF1F"; // 在此星系建立家园？
        StringBuilder body = new StringBuilder(systemName);
        if (isRecommended) {
            body.append("\n\u68C0\u6D4B\u5230\u5B9C\u5C45\u884C\u661F"); // 检测到宜居行星
        }

        dialog = new VectorDialog(sr, font, title, body.toString());
        dialog.addButton("\u786E\u8BA4", () -> { // 确认
            hide();
            if (SelectHomeConfirmDialog.this.onConfirm != null)
                SelectHomeConfirmDialog.this.onConfirm.run();
        });
        dialog.addButton("\u53D6\u6D88", () -> { // 取消
            hide();
            if (SelectHomeConfirmDialog.this.onCancel != null)
                SelectHomeConfirmDialog.this.onCancel.run();
        });
        dialog.setSize(360, 200);
        dialog.show(stage);
    }

    public void hide() {
        if (dialog != null) {
            dialog.hide();
            dialog = null;
        }
    }

    public boolean isVisible() {
        return dialog != null;
    }
}
