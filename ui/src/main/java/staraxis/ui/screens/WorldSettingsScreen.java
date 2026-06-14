package staraxis.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import staraxis.game.world.WorldGenConfig;
import staraxis.game.world.WorldType;
import staraxis.ui.Gui;

/**
 * 新游戏世界参数设置 Screen。
 */
public class WorldSettingsScreen implements Disposable {

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    private TextField worldNameField;
    private TextField worldSeedField;
    private SelectBox<String> worldRadiusBox;
    private SelectBox<String> worldTypeBox;
    private String selectedNationId;

    public WorldSettingsScreen(Gui gui) {
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

        Label title = new Label(gui.i18n("newGame.worldSettings.title"), skin);
        table.add(title).colspan(2).padBottom(20).row();

        table.add(new Label(gui.i18n("newGame.worldName"), skin)).padRight(10).padBottom(8);
        worldNameField = new TextField("", skin);
        worldNameField.setMessageText(gui.i18n("newGame.worldNameHint"));
        table.add(worldNameField).width(200).padBottom(8).row();

        table.add(new Label(gui.i18n("newGame.worldSettings.worldRadius"), skin)).padRight(10).padBottom(8);
        worldRadiusBox = new SelectBox<>(skin);
        worldRadiusBox.setItems("2", "3", "4", "5", "6");
        worldRadiusBox.setSelected("3");
        table.add(worldRadiusBox).width(200).padBottom(8).row();

        table.add(new Label(gui.i18n("newGame.worldType"), skin)).padRight(10).padBottom(8);
        worldTypeBox = new SelectBox<>(skin);
        worldTypeBox.setItems("SINGLE_PLAYER", "MULTI_PLAYER");
        worldTypeBox.setSelected("SINGLE_PLAYER");
        table.add(worldTypeBox).width(200).padBottom(8).row();

        table.add(new Label(gui.i18n("newGame.worldSeed"), skin)).padRight(10).padBottom(8);
        worldSeedField = new TextField("", skin);
        worldSeedField.setMessageText(gui.i18n("newGame.worldSeedHint"));
        table.add(worldSeedField).width(200).padBottom(8).row();

        TextButton nationBtn = new TextButton(gui.i18n("newGame.selectNation"), skin);
        nationBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                gui.dispatchAction("NATION_SELECT");
            }
        });
        table.add(nationBtn).colspan(2).width(200).padBottom(16).row();

        TextButton startBtn = new TextButton(gui.i18n("newGame.start"), skin);
        startBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                startNewGame();
            }
        });
        table.add(startBtn).colspan(2).width(200).padBottom(8).row();

        TextButton backBtn = new TextButton(gui.i18n("common.back"), skin);
        backBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                gui.dispatchAction("BACK_TO_MAIN_MENU");
            }
        });
        table.add(backBtn).colspan(2).width(200).row();

        root = table;
        stage.addActor(root);
    }

    public void setSelectedNation(String nationId) {
        this.selectedNationId = nationId;
    }

    private void startNewGame() {
        WorldGenConfig cfg = new WorldGenConfig();
        cfg.worldSeed = worldSeedField.getText().isBlank() ? null : worldSeedField.getText();
        cfg.worldRadius = Integer.parseInt(worldRadiusBox.getSelected());
        cfg.worldType = WorldType.valueOf(worldTypeBox.getSelected());
        cfg.galaxyShape = "hex";

        if (selectedNationId != null && !selectedNationId.isBlank()) {
            staraxis.game.nation.NationDef def = new staraxis.game.nation.NationDef();
            def.id = selectedNationId;
            def.name = selectedNationId;
            cfg.playerNationDef = def;
        }

        staraxis.game.StarAxisGameRuntime rt = staraxis.game.StarAxisGameRuntime.newGame(cfg);
        gui.registerRuntime(rt);
        gui.dispatchAction("START_GAME");
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
