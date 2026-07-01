package staraxis.ui.screens;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Disposable;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.ui.Gui;
import staraxis.ui.json.ComponentNode;
import staraxis.ui.json.UiFactory;
import staraxis.ui.json.UiParser;
import staraxis.ui.widgets.VectorLabel;

public class InGameHudScreen implements Disposable {

    private static final String UI_PATH = "ui/gameui/ingame-hud/ingame_hud.json";

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    public InGameHudScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    public void show() {
        dispose();

        UiParser parser = gui.get(UiParser.class);
        UiFactory factory = gui.get(UiFactory.class);
        if (parser == null || factory == null) return;

        ComponentNode node = parser.parseInternal(UI_PATH);
        if (node == null) return;

        root = factory.create(node);
        if (root != null) {
            stage.addActor(root);
        }

        refreshHud();
    }

    public void refreshHud() {
        StarAxisGameRuntime rt = gui.getRuntime();
        if (rt == null) return;

        RealTimeWorldState state = rt.getRealTimeWorldStateReadonly();
        if (state == null) return;

        if (root instanceof Group rootGroup) {
            Actor timeActor = rootGroup.findActor("time_label");
            if (timeActor instanceof VectorLabel vl) {
                vl.setText(String.format("Y%d M%d D%d %02d:%02d",
                        state.year, state.month, state.day, state.hour, state.minute));
            }
        }
    }

    /**
     * 更新视图标签（左上角），显示当前视图类型与缩放层级。
     *
     * @param text 视图标签文字，例如 "星系视图" 或 "恒星系视图 · 缩放 x4.0"
     */
    public void updateViewInfo(String text) {
        if (!(root instanceof Group)) return;
        Actor actor = ((Group) root).findActor("view_label");
        if (actor instanceof VectorLabel vl) {
            vl.setText(text != null ? text : "");
        }
    }

    /**
     * 设置悬停信息文字（左下角），Galaxy View 和 System View 共用。
     *
     * @param text 悬停信息文字，空或 null 时清空
     */
    public void setHoverInfoText(String text) {
        if (!(root instanceof Group)) return;
        Actor actor = ((Group) root).findActor("star_info_label");
        if (actor instanceof VectorLabel label) {
            label.setText(text != null ? text : "");
        }
    }

    /**
     * Galaxy View 悬停恒星信息更新（保留旧版兼容）。
     */
    public void updateStarInfo(long hoveredStarId, RealTimeWorldState state) {
        if (hoveredStarId < 0 || state == null) {
            setHoverInfoText("");
            return;
        }

        for (EntitySnapshot snap : state.getEntitySnapshotsView()) {
            if (snap != null && snap.entityId == hoveredStarId && snap.posWorldGU != null) {
                var pos = snap.posWorldGU;
                setHoverInfoText(String.format("(%.0f, %.0f, %.0f)", pos.x(), pos.y(), pos.z()));
                return;
            }
        }
        setHoverInfoText("");
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
