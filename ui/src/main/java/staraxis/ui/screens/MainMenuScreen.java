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
 * 通过 JSON 描述加载主菜单界面。
 */
public class MainMenuScreen implements Disposable {

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    public MainMenuScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    public void show() {
        dispose();
        UiParser parser = gui.get(UiParser.class);
        UiFactory factory = gui.get(UiFactory.class);

        ComponentNode node = parser.parseInternal("ui/gameui/main-menu/main_menu.json");
        if (node == null) {
            Gdx.app.error("MainMenuScreen", "Failed to parse ui/gameui/main-menu/main_menu.json");
            return;
        }

        root = factory.create(node);
        stage.addActor(root);
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
