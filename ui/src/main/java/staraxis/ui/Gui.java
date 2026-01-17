package staraxis.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import staraxis.ui.i18n.I18nService;
import staraxis.ui.widgets.DevelopingDialog;

import java.util.HashMap;
import java.util.Map;

public class Gui {

    private final Stage stage;
    private final Map<Class<?>, Object> componentsByType = new HashMap<>();

    public Gui(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    public <T> void register(Class<T> type, T instance) {
        componentsByType.put(type, instance);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        Object obj = componentsByType.get(type);
        if (obj == null) {
            Gdx.app.error("Gui", "Component not registered: " + type.getSimpleName());
            return null;
        }
        return (T) obj;
    }

    public void showMainMenu() {
        staraxis.ui.screens.MainMenuScreen screen = get(staraxis.ui.screens.MainMenuScreen.class);
        if (screen != null) {
            screen.show();
        }
    }

    public void dispatchMainMenuAction(String action) {
        if ("EXIT_CLICK".equals(action)) {
            Gdx.app.exit();
            return;
        }

        DevelopingDialog dialog = get(DevelopingDialog.class);
        if (dialog != null) {
            dialog.show(stage);
        }
    }
}
