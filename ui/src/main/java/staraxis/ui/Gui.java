package staraxis.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import staraxis.ui.i18n.I18nService;
import staraxis.ui.json.UiFactory;
import staraxis.ui.json.UiParser;
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

    public void initJsonUi() {
        if (get(UiParser.class) == null) {
            register(UiParser.class, new UiParser());
        }
        if (get(UiFactory.class) == null) {
            register(UiFactory.class, new UiFactory(this));
        }
    }

    public String i18n(String key) {
        I18nService svc = get(I18nService.class);
        return svc != null ? svc.get(key) : key;
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

        if ("SHOW_DEVELOPING_DIALOG".equals(action) || "DEVELOPING".equals(action)) {
            DevelopingDialog dialog = get(DevelopingDialog.class);
            if (dialog != null) {
                dialog.show(stage);
            }
            return;
        }

        DevelopingDialog dialog = get(DevelopingDialog.class);
        if (dialog != null) {
            dialog.show(stage);
        }
    }
}
