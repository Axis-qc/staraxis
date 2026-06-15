package staraxis.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Disposable;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.RealTimeWorldState;
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

        if (root instanceof Group) {
            Group rootGroup = (Group) root;
            Actor timeActor = rootGroup.findActor("time_label");
            if (timeActor instanceof VectorLabel) {
                ((VectorLabel) timeActor).setText(String.format("Y%d M%d D%d %02d:%02d",
                        state.year, state.month, state.day, state.hour, state.minute));
            }

            Actor tickActor = rootGroup.findActor("tick_label");
            if (tickActor instanceof VectorLabel) {
                ((VectorLabel) tickActor).setText("Tick: " + state.simulationTick);
            }

            Actor entityActor = rootGroup.findActor("entity_count_label");
            if (entityActor instanceof VectorLabel) {
                ((VectorLabel) entityActor).setText("Entities: " + state.getEntitiesByIdView().size());
            }
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
