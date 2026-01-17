package staraxis.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Disposable;
import staraxis.ui.Gui;
import staraxis.ui.json.ComponentNode;
import staraxis.ui.json.UiFactory;
import staraxis.ui.json.UiParser;

/**
 * 设置界面 Screen。
 *
 * 设计要点：
 * - 由声明式 UI (`settings.json`) 驱动，本类只负责加载、数据绑定与交互逻辑。
 * - 设置项的读写通过 SettingsRepository 完成，本类不直接操作文件。
 * - 交互通过 actionId 上抛给 Gui 分发。
 */
public class SettingsScreen implements Disposable {

    private static final String UI_PATH = "ui/gameui/settings/settings.json";

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    public SettingsScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    public void show() {
        dispose();

        UiParser parser = gui.get(UiParser.class);
        UiFactory factory = gui.get(UiFactory.class);

        ComponentNode node = parser.parseInternal(UI_PATH);
        if (node == null) {
            Gdx.app.error("SettingsScreen", "Failed to parse " + UI_PATH);
            return;
        }

        root = factory.create(node);
        stage.addActor(root);

        // TODO: 在这里加载 GameSettings 并将其值绑定到 UI 控件上
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
