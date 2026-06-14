package staraxis.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;
import staraxis.ui.Gui;
import staraxis.ui.i18n.I18nService;
import staraxis.ui.widgets.MenuEntry;
import staraxis.ui.widgets.VectorButton;
import staraxis.ui.widgets.VectorLabel;

import java.util.List;

public class MainMenuScreen implements Disposable {

    private static final Color GLOW_COLOR = new Color(0.3f, 0.65f, 0.95f, 1f);
    private static final Color TITLE_COLOR = new Color(1f, 1f, 1f, 1f);
    private static final Color SUBTITLE_COLOR = new Color(0.3f, 0.65f, 0.95f, 1f);
    private static final Color VERSION_COLOR = new Color(0.53f, 0.53f, 0.53f, 0.42f);

    private final Gui gui;
    private final Stage stage;
    private final ShapeRenderer sr;
    private final BitmapFont titleFont;
    private final BitmapFont subtitleFont;
    private final BitmapFont menuFont;
    private final BitmapFont versionFont;
    private final GlyphLayout layout;

    private Actor root;

    public MainMenuScreen(Gui gui, ShapeRenderer sr, BitmapFont defaultFont) {
        this.gui = gui;
        this.stage = gui.getStage();
        this.sr = sr;
        this.layout = new GlyphLayout();

        this.titleFont = defaultFont;
        this.subtitleFont = defaultFont;
        this.menuFont = defaultFont;
        this.versionFont = defaultFont;
    }

    public void show() {
        dispose();

        Table stack = new Table();
        stack.setFillParent(true);

        // --- Title ---
        VectorLabel title = new VectorLabel(titleFont, gui.i18n("app.title"), TITLE_COLOR);
        title.setSize(400, 50);
        title.setPosition(64, stage.getHeight() - 140);

        // --- Subtitle ---
        VectorLabel subtitle = new VectorLabel(subtitleFont, gui.i18n("mainMenu.web.subtitle"), SUBTITLE_COLOR);
        subtitle.setSize(400, 30);
        subtitle.setPosition(64, stage.getHeight() - 175);

        stack.addActor(title);
        stack.addActor(subtitle);

        // --- Menu entries ---
        float menuY = stage.getHeight() - 240;
        float entryHeight = 44f;
        float entryWidth = 400f;
        float menuX = 64;

        addMenuEntry(stack, gui.i18n("mainMenu.newGame"), null,
                () -> gui.dispatchAction("NEW_GAME"),
                menuX, menuY - 0 * (entryHeight + 8f), entryWidth, entryHeight);

        addMenuEntry(stack, gui.i18n("mainMenu.loadGame"), null,
                () -> gui.dispatchAction("LOAD_GAME"),
                menuX, menuY - 1 * (entryHeight + 8f), entryWidth, entryHeight);

        addMenuEntry(stack, gui.i18n("mainMenu.multiplayer"), gui.i18n("mainMenu.tag.developing"),
                () -> gui.dispatchAction("SHOW_DEVELOPING_DIALOG"),
                menuX, menuY - 2 * (entryHeight + 8f), entryWidth, entryHeight);

        addMenuEntry(stack, gui.i18n("mainMenu.shipDesigner"), gui.i18n("mainMenu.tag.developing"),
                () -> gui.dispatchAction("SHOW_DEVELOPING_DIALOG"),
                menuX, menuY - 3 * (entryHeight + 8f), entryWidth, entryHeight);

        addMenuEntry(stack, gui.i18n("mainMenu.settings"), null,
                () -> gui.dispatchAction("OPEN_SETTINGS"),
                menuX, menuY - 4 * (entryHeight + 8f), entryWidth, entryHeight);

        addMenuEntry(stack, gui.i18n("mainMenu.exit"), null,
                () -> gui.dispatchAction("EXIT_CLICK"),
                menuX, menuY - 5 * (entryHeight + 8f), entryWidth, entryHeight);

        // --- Language button (top-right) ---
        String langLabel = "> " + gui.i18n("lang.current");
        VectorButton langBtn = new VectorButton(sr, menuFont, langLabel, () -> {
            I18nService i18n = gui.get(I18nService.class);
            String current = i18n.getCurrentLanguage();
            List<String> langs = i18n.listAvailableLanguages();
            int idx = langs.indexOf(current);
            String next = langs.get((idx + 1) % langs.size());
            i18n.load(next);
            show();
        });
        langBtn.setSize(140, 36);
        langBtn.setPosition(stage.getWidth() - 155, stage.getHeight() - 48);
        stack.addActor(langBtn);

        // --- Version (bottom-right) ---
        VectorLabel version = new VectorLabel(versionFont, gui.i18n("common.version"), VERSION_COLOR);
        layout.setText(versionFont, gui.i18n("common.version"));
        version.setSize(layout.width, layout.height);
        version.setPosition(stage.getWidth() - layout.width - 32, 28);
        stack.addActor(version);

        root = stack;
        stage.addActor(root);
    }

    private void addMenuEntry(Table stack, String text, String tag, Runnable action,
                              float x, float y, float w, float h) {
        MenuEntry entry = new MenuEntry(sr, menuFont, text, tag, action);
        entry.setSize(w, h);
        entry.setPosition(x, y);
        stack.addActor(entry);
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
