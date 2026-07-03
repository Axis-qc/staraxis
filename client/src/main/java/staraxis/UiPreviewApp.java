package staraxis;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import staraxis.ui.FontProvider;
import staraxis.ui.Gui;
import staraxis.ui.effects.EffectRegistry;
import staraxis.ui.i18n.I18nService;
import staraxis.ui.json.GameDataProvider;
import staraxis.ui.json.UiFactory;
import staraxis.ui.settings.SettingsRepository;
import staraxis.ui.screens.InGameHudScreen;
import staraxis.ui.screens.SettingsScreen;
import staraxis.ui.screens.WorldSettingsScreen;
import staraxis.render.util.MenuBackgroundLoader;
import staraxis.ui.widgets.DevelopingDialog;
import staraxis.ui.widgets.StarfieldBackground;

public class UiPreviewApp implements ApplicationListener {

    private Stage stage;
    private Gui gui;
    private StarfieldBackground starfield;

    @Override
    public void create() {
        BaseUiInit base = BaseUiInit.init();
        stage = base.stage;
        gui = base.gui;
        starfield = base.starfield;

        Gdx.input.setInputProcessor(new InputMultiplexer(stage));

        gui.showMainMenu();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
        if (starfield != null) {
            starfield.resize(width, height);
        }
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float dt = Gdx.graphics.getDeltaTime();

        if (starfield != null) {
            starfield.act(dt);
            starfield.render();
        }

        stage.act(dt);
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
            ShapeRenderer sr = gui.get(ShapeRenderer.class);
            if (sr != null) {
                sr.dispose();
            }
        }
        if (stage != null) {
            stage.dispose();
        }
        if (starfield != null) {
            starfield.dispose();
            starfield = null;
        }
        FontProvider.disposeAllIncremental();
    }
}
