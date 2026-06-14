package staraxis;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import staraxis.ui.FontProvider;
import staraxis.ui.Gui;
import staraxis.ui.UiSkinLoader;
import staraxis.ui.i18n.I18nService;
import staraxis.ui.screens.InGameHudScreen;
import staraxis.ui.screens.LoadGameScreen;
import staraxis.ui.screens.MainMenuScreen;
import staraxis.ui.screens.NationSelectScreen;
import staraxis.ui.screens.SettingsScreen;
import staraxis.ui.screens.WorldSettingsScreen;
import staraxis.ui.widgets.DevelopingDialog;

public class UiPreviewApp implements ApplicationListener {

    private Stage stage;
    private Gui gui;

    @Override
    public void create() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(new InputMultiplexer(stage));

        I18nService i18nService = new I18nService();
        i18nService.load("zh");

        gui = new Gui(stage, s -> {
        }, s -> {
        });
        gui.register(I18nService.class, i18nService);

        Skin skin = UiSkinLoader.loadDefault("ui/uiskin/uiskin.json");

        BitmapFont defaultFont = FontProvider.createDefaultFont();
        BitmapFont ttfFont = FontProvider.tryCreateFontFromTtfOrNull("fonts/chinese/AlibabaPuHuiTi-3-65-Medium.ttf",
                28);
        BitmapFont finalFont = (ttfFont != null) ? ttfFont : defaultFont;

        skin.add("default-font", finalFont, BitmapFont.class);

        TextButton.TextButtonStyle textButtonStyle = skin.get(TextButton.TextButtonStyle.class);
        textButtonStyle.font = finalFont;

        Label.LabelStyle labelStyle = skin.get(Label.LabelStyle.class);
        labelStyle.font = finalFont;

        gui.register(Skin.class, skin);
        gui.initJsonUi();

        ShapeRenderer sr = new ShapeRenderer();
        gui.register(ShapeRenderer.class, sr);

        MainMenuScreen mainMenuScreen = new MainMenuScreen(gui, sr, finalFont);
        WorldSettingsScreen worldSettingsScreen = new WorldSettingsScreen(gui);
        NationSelectScreen nationSelectScreen = new NationSelectScreen(gui);
        LoadGameScreen loadGameScreen = new LoadGameScreen(gui);
        InGameHudScreen inGameHudScreen = new InGameHudScreen(gui);
        SettingsScreen settingsScreen = new SettingsScreen(gui);
        DevelopingDialog developingDialog = new DevelopingDialog(skin, i18nService);

        gui.register(MainMenuScreen.class, mainMenuScreen);
        gui.register(WorldSettingsScreen.class, worldSettingsScreen);
        gui.register(NationSelectScreen.class, nationSelectScreen);
        gui.register(LoadGameScreen.class, loadGameScreen);
        gui.register(InGameHudScreen.class, inGameHudScreen);
        gui.register(SettingsScreen.class, settingsScreen);
        gui.register(DevelopingDialog.class, developingDialog);

        gui.showMainMenu();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        if (gui != null) {
            Skin skin = gui.get(Skin.class);
            if (skin != null) {
                skin.dispose();
            }
            ShapeRenderer sr = gui.get(ShapeRenderer.class);
            if (sr != null) {
                sr.dispose();
            }
        }
        if (stage != null) {
            stage.dispose();
        }
    }
}
