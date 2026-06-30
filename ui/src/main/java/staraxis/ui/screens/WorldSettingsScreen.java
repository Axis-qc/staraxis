package staraxis.ui.screens;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;
import staraxis.game.world.WorldGenConfig;
import staraxis.game.world.WorldType;
import staraxis.ui.Gui;
import staraxis.ui.effects.EffectRegistry;
import staraxis.ui.layout.ScreenLayout;
import staraxis.ui.theme.UiTheme;
import staraxis.ui.widgets.VectorButton;
import staraxis.ui.widgets.VectorLabel;
import staraxis.ui.widgets.VectorSelectBox;
import staraxis.ui.widgets.VectorTextField;

/**
 * 新游戏世界参数设置 Screen。
 *
 * 使用矢量控件，颜色从 UiTheme 读取，不依赖 Skin。
 */
public class WorldSettingsScreen implements Disposable {

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    private VectorTextField worldNameField;
    private VectorTextField worldSeedField;
    private VectorSelectBox systemCountBox;
    private VectorSelectBox worldTypeBox;
    private String selectedNationId;

    // 由 show() 获取
    private ShapeRenderer sr;
    private BitmapFont font;

    public WorldSettingsScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    public void show() {
        dispose();

        EffectRegistry effectRegistry = gui.tryGet(EffectRegistry.class);
        UiTheme theme = UiTheme.from(effectRegistry);

        sr = gui.tryGet(ShapeRenderer.class);
        font = getVectorFont();

        ScreenLayout L = new ScreenLayout(theme, sr != null ? sr : new ShapeRenderer(), font, effectRegistry);

        Table table = new Table();
        table.setFillParent(true);
        table.top().left();
        table.pad(ScreenLayout.PADDING_LEFT, stage.getHeight() - 140, 0, 0);

        // --- 页面标题 ---
        VectorLabel pageTitle = L.createPageTitle(gui.i18n("newGame.worldSettings.title"), 0, 0);
        table.add(pageTitle).colspan(2).left().padBottom(20).row();

        // 世界名称
        VectorLabel nameLabel = createBodyLabel(L, gui.i18n("newGame.worldName"));
        table.add(nameLabel).left().padRight(10).padBottom(8);
        worldNameField = new VectorTextField(sr, font, "");
        worldNameField.setMessageText(gui.i18n("newGame.worldNameHint"));
        worldNameField.setSize(220, 34);
        table.add(worldNameField).width(220).height(34).padBottom(8).row();

        // 恒星系数量
        VectorLabel countLabel = createBodyLabel(L, gui.i18n("newGame.systemCount"));
        table.add(countLabel).left().padRight(10).padBottom(8);
        systemCountBox = new VectorSelectBox(sr, font);
        systemCountBox.setItems("500", "1000", "2000", "4000", "8000", "10000");
        systemCountBox.setSelected("500");
        systemCountBox.setSize(220, 34);
        table.add(systemCountBox).width(220).height(34).padBottom(8).row();

        // 世界类型
        VectorLabel typeLabel = createBodyLabel(L, gui.i18n("newGame.worldType"));
        table.add(typeLabel).left().padRight(10).padBottom(8);
        worldTypeBox = new VectorSelectBox(sr, font);
        worldTypeBox.setItems("SINGLE_PLAYER", "MULTI_PLAYER");
        worldTypeBox.setSelected("SINGLE_PLAYER");
        worldTypeBox.setSize(220, 34);
        table.add(worldTypeBox).width(220).height(34).padBottom(8).row();

        // 世界种子
        VectorLabel seedLabel = createBodyLabel(L, gui.i18n("newGame.worldSeed"));
        table.add(seedLabel).left().padRight(10).padBottom(8);
        worldSeedField = new VectorTextField(sr, font, "");
        worldSeedField.setMessageText(gui.i18n("newGame.worldSeedHint"));
        worldSeedField.setSize(220, 34);
        table.add(worldSeedField).width(220).height(34).padBottom(8).row();

        // 按钮行
        Table buttonRow = new Table();
        buttonRow.defaults().padRight(10).padTop(16);

        VectorButton nationBtn = L.createPrimaryButton(gui.i18n("newGame.selectNation"),
                () -> gui.dispatchAction("NATION_SELECT"),
                0, 0, 200, 40);
        buttonRow.add(nationBtn).width(200).height(40);

        VectorButton startBtn = L.createPrimaryButton(gui.i18n("newGame.start"),
                this::startNewGame,
                0, 0, 200, 40);
        buttonRow.add(startBtn).width(200).height(40);

        VectorButton backBtn = L.createPrimaryButton(gui.i18n("common.back"),
                () -> gui.dispatchAction("BACK_TO_MAIN_MENU"),
                0, 0, 200, 40);
        buttonRow.add(backBtn).width(200).height(40);

        table.add(buttonRow).colspan(2).left().padTop(8).row();

        root = table;
        stage.addActor(root);
    }

    // ===== 辅助方法 =====

    private VectorLabel createBodyLabel(ScreenLayout L, String text) {
        return new VectorLabel(getVectorFont(), text, L.getTheme().text);
    }

    private BitmapFont getVectorFont() {
        BitmapFont f = gui.tryGet(BitmapFont.class);
        if (f == null) f = staraxis.ui.FontProvider.createVectorFont();
        return f;
    }

    public void setSelectedNation(String nationId) {
        this.selectedNationId = nationId;
    }

    private void startNewGame() {
        WorldGenConfig cfg = new WorldGenConfig();
        cfg.worldSeed = worldSeedField.getText().isBlank() ? null : worldSeedField.getText();
        cfg.systemCount = Integer.parseInt(systemCountBox.getSelected());
        cfg.worldType = WorldType.valueOf(worldTypeBox.getSelected());

        if (selectedNationId != null && !selectedNationId.isBlank()) {
            staraxis.game.nation.NationDef def = new staraxis.game.nation.NationDef();
            def.id = selectedNationId;
            def.name = selectedNationId;
            cfg.playerNationDef = def;
        }

        var callback = gui.getOnStartNewGame();
        if (callback != null) {
            callback.accept(cfg);
        } else {
            staraxis.game.StarAxisGameRuntime rt = staraxis.game.StarAxisGameRuntime.newGame(cfg);
            gui.registerRuntime(rt);
            gui.dispatchAction("START_GAME");
        }
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
