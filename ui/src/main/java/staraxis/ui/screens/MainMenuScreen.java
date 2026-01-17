package staraxis.ui.screens;

import com.badlogic.gdx.Gdx;
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

public class MainMenuScreen implements Disposable {

    private final Stage stage;
    private final Gui gui;
    private final Skin skin;

    private Table root;

    public MainMenuScreen(Stage stage, Gui gui, Skin skin) {
        this.stage = stage;
        this.gui = gui;
        this.skin = skin;
    }

    public void show() {
        if (root != null) {
            root.remove();
            root = null;
        }

        root = new Table();
        root.setFillParent(true);
        root.pad(24f);

        Label title = new Label("StarAxis", skin);
        title.setAlignment(Align.center);
        title.setColor(Color.WHITE);

        TextButton newGame = new TextButton("新游戏", skin);
        TextButton loadGame = new TextButton("加载游戏", skin);
        TextButton multiplayer = new TextButton("多人游戏（未来规划）", skin);
        TextButton shipDesigner = new TextButton("舰船设计器", skin);
        TextButton settings = new TextButton("设置", skin);
        TextButton exit = new TextButton("退出游戏", skin);

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
