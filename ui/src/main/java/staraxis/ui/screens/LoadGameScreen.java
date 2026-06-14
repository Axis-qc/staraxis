package staraxis.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import staraxis.ui.Gui;

import java.util.ArrayList;
import java.util.List;

/**
 * 加载存档 Screen。
 */
public class LoadGameScreen implements Disposable {

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    public LoadGameScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    public void show() {
        dispose();

        Skin skin = gui.get(Skin.class);
        if (skin == null) return;

        Table table = new Table();
        table.setFillParent(true);
        table.center();

        Label title = new Label(gui.i18n("mainMenu.loadGame"), skin);
        table.add(title).padBottom(16).row();

        List<String> saves = scanSaves();
        if (saves.isEmpty()) {
            Label empty = new Label(gui.i18n("loadGame.noSaves"), skin);
            table.add(empty).padBottom(16).row();
        } else {
            Table saveList = new Table();
            ScrollPane scroll = new ScrollPane(saveList, skin);
            scroll.setFadeScrollBars(false);

            for (String worldId : saves) {
                TextButton btn = new TextButton(worldId, skin);
                btn.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        Gdx.app.log("LoadGameScreen", "Load save: " + worldId);
                        gui.dispatchAction("SHOW_DEVELOPING_DIALOG");
                    }
                });
                saveList.add(btn).width(280).height(40).pad(4).row();
            }

            table.add(scroll).width(320).height(300).padBottom(16).row();
        }

        TextButton backBtn = new TextButton(gui.i18n("common.back"), skin);
        backBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                gui.dispatchAction("BACK_TO_MAIN_MENU");
            }
        });
        table.add(backBtn).width(200).row();

        root = table;
        stage.addActor(root);
    }

    private List<String> scanSaves() {
        List<String> saves = new ArrayList<>();
        try {
            FileHandle dir = Gdx.files.local("gamedata/saves");
            if (dir.exists() && dir.isDirectory()) {
                for (FileHandle fh : dir.list()) {
                    if (fh.isDirectory()) {
                        FileHandle state = fh.child("state.json");
                        if (state.exists()) {
                            saves.add(fh.name());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Gdx.app.error("LoadGameScreen", "Failed to scan saves", e);
        }
        return saves;
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
