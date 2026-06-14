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
 * 国家选择 Screen。
 */
public class NationSelectScreen implements Disposable {

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    public NationSelectScreen(Gui gui) {
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

        Label title = new Label(gui.i18n("newGame.selectNation"), skin);
        table.add(title).padBottom(16).row();

        Table nationList = new Table();
        ScrollPane scroll = new ScrollPane(nationList, skin);
        scroll.setFadeScrollBars(false);

        List<String> nations = scanNationFiles();
        for (String nationId : nations) {
            TextButton btn = new TextButton(nationId, skin);
            btn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    WorldSettingsScreen ws = gui.get(WorldSettingsScreen.class);
                    if (ws != null) {
                        ws.setSelectedNation(nationId);
                    }
                    gui.dispatchAction("NEW_GAME");
                }
            });
            nationList.add(btn).width(240).height(40).pad(4).row();
        }

        table.add(scroll).width(280).height(300).padBottom(16).row();

        TextButton backBtn = new TextButton(gui.i18n("common.back"), skin);
        backBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                gui.dispatchAction("NEW_GAME");
            }
        });
        table.add(backBtn).width(200).row();

        root = table;
        stage.addActor(root);
    }

    private List<String> scanNationFiles() {
        List<String> nations = new ArrayList<>();
        try {
            FileHandle dir = Gdx.files.internal("nations");
            if (dir.exists() && dir.isDirectory()) {
                for (FileHandle fh : dir.list()) {
                    if (fh.isDirectory()) {
                        nations.add(fh.name());
                    }
                }
            }
        } catch (Exception e) {
            Gdx.app.error("NationSelectScreen", "Failed to scan nations", e);
        }
        if (nations.isEmpty()) {
            nations.add("player_empire");
        }
        return nations;
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
