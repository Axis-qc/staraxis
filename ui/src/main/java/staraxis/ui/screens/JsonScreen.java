package staraxis.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Disposable;

import staraxis.ui.Gui;
import staraxis.ui.json.ComponentNode;
import staraxis.ui.json.UiFactory;
import staraxis.ui.json.UiParser;

public class JsonScreen implements Disposable {

    private final Gui gui;
    private final Stage stage;
    private final String jsonPath;
    private Actor root;

    public JsonScreen(Gui gui, String jsonPath) {
        this.gui = gui;
        this.stage = gui.getStage();
        this.jsonPath = jsonPath;
    }

    public void show() {
        dispose();

        UiParser parser = gui.get(UiParser.class);
        UiFactory factory = gui.get(UiFactory.class);
        if (parser == null || factory == null) {
            Gdx.app.error("JsonScreen", "UiParser or UiFactory not registered");
            return;
        }

        ComponentNode node = parser.parseInternal(jsonPath);
        if (node == null) {
            Gdx.app.error("JsonScreen", "Failed to parse: " + jsonPath);
            return;
        }

        root = factory.create(node);
        if (root != null) {
            stage.addActor(root);
        }
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
