package staraxis.ui.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import staraxis.ui.Gui;
import staraxis.ui.i18n.I18nService;

public class MainMenuScreen implements Disposable {

    private final Gui gui;
    private final Stage stage;
    private final Skin skin;
    private final I18nService i18n;

    private Table root;

    public MainMenuScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
        this.skin = gui.get(Skin.class);
        this.i18n = gui.get(I18nService.class);
    }

    public void show() {
        if (root != null) {
            root.remove();
            root = null;
        }

        root = new Table();
        root.setFillParent(true);
        root.pad(24f);

        Label title = new Label(i18n.get("app.title"), skin);
        title.setAlignment(Align.center);
        title.setColor(Color.WHITE);

        TextButton newGame = new TextButton(i18n.get("mainMenu.newGame"), skin);
        TextButton loadGame = new TextButton(i18n.get("mainMenu.loadGame"), skin);
        TextButton multiplayer = new TextButton(i18n.get("mainMenu.multiplayer"), skin);
        TextButton shipDesigner = new TextButton(i18n.get("mainMenu.shipDesigner"), skin);
        TextButton settings = new TextButton(i18n.get("mainMenu.settings"), skin);
        TextButton exit = new TextButton(i18n.get("mainMenu.exit"), skin);

        ChangeListener developing = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                gui.dispatchMainMenuAction("DEVELOPING");
            }
        };

        newGame.addListener(developing);
        loadGame.addListener(developing);
        multiplayer.addListener(developing);
        shipDesigner.addListener(developing);
        settings.addListener(developing);

        exit.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                gui.dispatchMainMenuAction("EXIT_CLICK");
            }
        });

        root.defaults().width(360f).height(48f).pad(8f);
        root.add(title).padBottom(18f).row();
        root.add(newGame).row();
        root.add(loadGame).row();
        root.add(multiplayer).row();
        root.add(shipDesigner).row();
        root.add(settings).row();
        root.add(exit).row();

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
