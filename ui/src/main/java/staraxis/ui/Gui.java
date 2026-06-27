package staraxis.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Disposable;

import java.util.function.Consumer;
import staraxis.game.StarAxisGameRuntime;
import staraxis.ui.effects.EffectRegistry;
import staraxis.ui.i18n.I18nService;
import staraxis.ui.json.UiFactory;
import staraxis.ui.json.UiParser;
import staraxis.ui.screens.InGameHudScreen;
import staraxis.ui.screens.JsonScreen;
import staraxis.ui.screens.LoadGameScreen;
import staraxis.ui.screens.MainMenuScreen;
import staraxis.ui.screens.NationSelectScreen;
import staraxis.ui.screens.SettingsScreen;
import staraxis.ui.screens.UiComponentsTestScreen;
import staraxis.ui.screens.WorldSettingsScreen;
import staraxis.ui.widgets.DevelopingDialog;

import java.util.HashMap;
import java.util.Map;

/**
 * UI 根容器（UI 层服务定位器）。
 *
 * 设计要点：
 * - 仅保存 UI 层可用的服务实例（Stage、Skin、I18n、UI 解析器/工厂等）。
 * - 通过 {@link #dispatchAction(String)} 以 actionId 的方式把交互意图上抛，避免 UI
 * 直接修改权威世界状态。
 * - 某些服务（例如 JSON UI）需要依赖 Skin 等资源，因此采用显式 init 方法，避免初始化顺序导致 NPE。
 * - 原生客户端与 game 运行时同进程，直接持有 StarAxisGameRuntime 引用，无需网络层。
 */
public class Gui {

    private final Stage stage;
    private final Consumer<Float> uiScaleApplier;
    private final Consumer<Float> fontScaleApplier;
    private final Map<Class<?>, Object> componentsByType = new HashMap<>();

    private StarAxisGameRuntime runtime;

    private static final String[] THEME_PATHS = {
        "ui/effects/default.json",
        "ui/effects/amethyst.json",
        "ui/effects/ember.json",
        "ui/effects/forest.json"
    };
    private int currentThemeIndex = 0;

    private void cycleTheme() {
        currentThemeIndex = (currentThemeIndex + 1) % THEME_PATHS.length;
        EffectRegistry reg = get(EffectRegistry.class);
        if (reg != null) {
            reg.clear();
            reg.load(THEME_PATHS[currentThemeIndex]);
        }
        UiFactory factory = get(UiFactory.class);
        if (factory != null) {
            factory.setEffectRegistry(reg);
        }
    }

    /** 当前正在显示的 Screen，用于切换时清理旧界面。 */
    private Disposable activeScreen;

    public Gui(Stage stage, Consumer<Float> uiScaleApplier, Consumer<Float> fontScaleApplier) {
        this.stage = stage;
        this.uiScaleApplier = uiScaleApplier;
        this.fontScaleApplier = fontScaleApplier;
    }

    public Stage getStage() {
        return stage;
    }

    public void registerRuntime(StarAxisGameRuntime runtime) {
        this.runtime = runtime;
    }

    public StarAxisGameRuntime getRuntime() {
        return runtime;
    }

    public void applyUiScale(float scale) {
        if (uiScaleApplier != null) {
            uiScaleApplier.accept(scale);
        }
    }

    public void applyFontScale(float scale) {
        if (fontScaleApplier != null) {
            fontScaleApplier.accept(scale);
        }
    }

    public <T> void register(Class<T> type, T instance) {
        componentsByType.put(type, instance);
    }

    @SuppressWarnings("unchecked")
    public <T> T tryGet(Class<T> type) {
        return (T) componentsByType.get(type);
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
        if (tryGet(UiParser.class) == null) {
            register(UiParser.class, new UiParser());
        }
        if (tryGet(UiFactory.class) == null) {
            register(UiFactory.class, new UiFactory(this));
        }
        if (tryGet(EffectRegistry.class) == null) {
            EffectRegistry reg = new EffectRegistry();
            reg.load("ui/effects/default.json");
            register(EffectRegistry.class, reg);
        }
    }

    public String i18n(String key) {
        I18nService svc = get(I18nService.class);
        return svc != null ? svc.get(key) : key;
    }

    /* ------------------ Screen 切换辅助 ------------------ */

    private void switchScreen(Disposable screen, Runnable showCallback) {
        if (activeScreen != null) {
            try {
                activeScreen.dispose();
            } catch (Exception ignored) {
            }
            activeScreen = null;
        }
        stage.clear();

        showCallback.run();
        activeScreen = screen;
    }

    public void showMainMenu() {
        JsonScreen screen = new JsonScreen(this, "ui/gameui/main-menu/main_menu.json");
        switchScreen(screen, screen::show);
    }

    public void showSettingsScreen() {
        SettingsScreen screen = get(SettingsScreen.class);
        if (screen != null) {
            switchScreen(screen, screen::show);
        }
    }

    public void showWorldSettings() {
        WorldSettingsScreen screen = get(WorldSettingsScreen.class);
        if (screen != null) {
            switchScreen(screen, screen::show);
        }
    }

    public void showNationSelect() {
        JsonScreen screen = new JsonScreen(this, "ui/gameui/nation-select/nation_select.json");
        switchScreen(screen, screen::show);
    }

    public void showLoadGame() {
        JsonScreen screen = new JsonScreen(this, "ui/gameui/load-game/load_game.json");
        switchScreen(screen, screen::show);
    }

    public void showInGameHud() {
        InGameHudScreen screen = get(InGameHudScreen.class);
        if (screen != null) {
            switchScreen(screen, screen::show);
        }
    }

    public void showUiComponentsTestScreen() {
        UiComponentsTestScreen screen = get(UiComponentsTestScreen.class);
        if (screen != null) {
            switchScreen(screen, screen::show);
        }
    }

    /**
     * 通用 UI 动作分发。
     */
    public void dispatchAction(String action) {
        if (action == null || action.isBlank())
            return;

        String actionId = action;
        String actionArg = null;
        int idx = action.indexOf(':');
        if (idx > 0) {
            actionId = action.substring(0, idx);
            actionArg = action.substring(idx + 1);
        }

        if ("TOGGLE_RESOLUTION_LIST".equals(actionId) || "TOGGLE_FPS_LIST".equals(actionId)) {
            Gdx.app.log("Gui", "Action fired: " + actionId);
        }

        switch (actionId) {
            case "EXIT_CLICK":
                Gdx.app.exit();
                return;
            case "TOGGLE_LANGUAGE": {
                I18nService i18n = get(I18nService.class);
                if (i18n != null) {
                    String current = i18n.getCurrentLanguage();
                    java.util.List<String> langs = i18n.listAvailableLanguages();
                    int langIdx = langs.indexOf(current);
                    String next = langs.get((langIdx + 1) % langs.size());
                    i18n.load(next);
                    showMainMenu();
                }
                return;
            }
            case "TOGGLE_THEME": {
                cycleTheme();
                showMainMenu();
                return;
            }
            case "SET_LANG_ZH":
                get(I18nService.class).load("zh");
                showSettingsScreen();
                return;
            case "SET_LANG_EN":
                get(I18nService.class).load("en");
                showSettingsScreen();
                return;
            case "OPEN_SETTINGS":
                showSettingsScreen();
                return;
            case "BACK_TO_MAIN_MENU":
                showMainMenu();
                return;
            case "NEW_GAME":
                showWorldSettings();
                return;
            case "LOAD_GAME":
                showLoadGame();
                return;
            case "NATION_SELECT":
                showNationSelect();
                return;
            case "SELECT_NATION": {
                WorldSettingsScreen ws = get(WorldSettingsScreen.class);
                if (ws != null && actionArg != null) {
                    ws.setSelectedNation(actionArg);
                }
                showWorldSettings();
                return;
            }
            case "START_GAME":
                if (runtime != null) {
                    runtime.start();
                    showInGameHud();
                }
                return;
            case "RETURN_TO_MAIN_MENU":
                runtime = null;
                showMainMenu();
                return;
            case "TOGGLE_VSYNC": {
                SettingsScreen s = get(SettingsScreen.class);
                if (s != null)
                    s.toggleVsync();
                return;
            }
            case "TOGGLE_RESOLUTION_LIST": {
                SettingsScreen s = get(SettingsScreen.class);
                if (s != null)
                    s.toggleResolutionList();
                return;
            }
            case "TOGGLE_FPS_LIST": {
                SettingsScreen s = get(SettingsScreen.class);
                if (s != null)
                    s.toggleFpsList();
                return;
            }
            case "SELECT_RESOLUTION": {
                SettingsScreen s = get(SettingsScreen.class);
                if (s != null)
                    s.selectResolution(actionArg);
                return;
            }
            case "INCREASE_UI_SCALE": {
                SettingsScreen s = get(SettingsScreen.class);
                if (s != null)
                    s.increaseUiScale();
                return;
            }
            case "DECREASE_UI_SCALE": {
                SettingsScreen s = get(SettingsScreen.class);
                if (s != null)
                    s.decreaseUiScale();
                return;
            }
            case "INCREASE_FONT_SCALE": {
                SettingsScreen s = get(SettingsScreen.class);
                if (s != null)
                    s.increaseFontScale();
                return;
            }
            case "DECREASE_FONT_SCALE": {
                SettingsScreen s = get(SettingsScreen.class);
                if (s != null)
                    s.decreaseFontScale();
                return;
            }
            case "SELECT_FPS_LIMIT": {
                SettingsScreen s = get(SettingsScreen.class);
                if (s != null)
                    s.selectFpsLimit(actionArg);
                return;
            }
            case "SET_MASTER_VOLUME": {
                if (actionArg != null) {
                    try {
                        float v = Float.parseFloat(actionArg);
                        SettingsScreen s = get(SettingsScreen.class);
                        if (s != null)
                            s.setMasterVolume(v);
                    } catch (Exception ignored) {
                    }
                }
                return;
            }
            case "OPEN_SETTINGS_TAB": {
                SettingsScreen s = get(SettingsScreen.class);
                if (s != null)
                    s.openTab(actionArg);
                return;
            }
            case "SAVE_SETTINGS": {
                SettingsScreen settingsScreen = get(SettingsScreen.class);
                if (settingsScreen != null) {
                    settingsScreen.saveSettings();
                }
                return;
            }
            case "OPEN_MOD_LIST":
            case "SHOW_DEVELOPING_DIALOG":
            case "DEVELOPING": {
                DevelopingDialog dialog = get(DevelopingDialog.class);
                if (dialog != null) {
                    dialog.show(stage);
                }
                return;
            }
            default:
                Gdx.app.log("Gui", "Unhandled action: " + action);
                break;
        }
    }
}
