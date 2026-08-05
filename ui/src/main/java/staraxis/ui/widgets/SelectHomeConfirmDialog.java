package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;

import staraxis.ui.UiPointerService;

/**
 * SelectHomeConfirmDialog（选择母星系确认弹窗）喵。
 *
 * 在 Galaxy View 选择母星系模式下，点击恒星后弹出。
 * 使用 VectorDialog 实现，不依赖 Skin。
 * 弹窗显示期间注册 bounds 到 UiPointerService（点击弹窗内只触发 UI，不触发 3D），
 * 关闭时注销。
 */
public class SelectHomeConfirmDialog {

    private final ShapeRenderer sr;
    private final BitmapFont font;
    /** 统一 UI 命中守卫服务：弹窗显示/关闭时动态注册/注销 bounds 喵 */
    private final UiPointerService pointerService;
    private VectorDialog dialog;
    private Runnable onConfirm;
    private Runnable onCancel;

    public SelectHomeConfirmDialog(ShapeRenderer sr, BitmapFont font, UiPointerService pointerService) {
        this.sr = sr;
        this.font = font;
        this.pointerService = pointerService;
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
        // 弹窗显示期间注册 bounds 到统一守卫：点击弹窗内只触发 UI，不触发 3D 选中喵
        if (pointerService != null) {
            pointerService.register(this::isPointerOverDialog);
        }
    }

    public void hide() {
        // 注销守卫注册，弹窗关闭后 3D 交互恢复正常喵
        if (pointerService != null) {
            pointerService.unregister(this::isPointerOverDialog);
        }
        if (dialog != null) {
            dialog.hide();
            dialog = null;
        }
    }

    /**
     * 弹窗的区域命中判定（注册到 UiPointerService 的守卫入口）喵。
     * 弹窗已关闭或不在舞台时守卫自动失效。
     */
    private boolean isPointerOverDialog(float x, float y) {
        if (dialog == null || dialog.getStage() == null) {
            return false;
        }
        float dx = dialog.getX();
        float dy = dialog.getY();
        return x >= dx && x <= dx + dialog.getWidth()
                && y >= dy && y <= dy + dialog.getHeight();
    }

    public boolean isVisible() {
        return dialog != null;
    }
}
