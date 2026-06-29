package staraxis.ui.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import staraxis.game.world.WorldGenConfig;
import staraxis.game.world.WorldType;
import staraxis.ui.Gui;
import staraxis.ui.effects.EffectRegistry;
import staraxis.ui.layout.ScreenLayout;
import staraxis.ui.theme.UiTheme;
import staraxis.ui.widgets.VectorButton;
import staraxis.ui.widgets.VectorLabel;

/**
 * 新游戏世界参数设置 Screen。
 *
 * 使用 VectorLabel / VectorButton 统一控件，颜色从 UiTheme 读取。
 * TextField / SelectBox 使用透明主题样式，与整体 HUD 风格保持一致。
 */
public class WorldSettingsScreen implements Disposable {

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    private TextField worldNameField;
    private TextField worldSeedField;
    private SelectBox<String> systemCountBox;
    private SelectBox<String> worldTypeBox;
    private String selectedNationId;

    public WorldSettingsScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    public void show() {
        dispose();

        // 获取主题与布局工具
        EffectRegistry effectRegistry = gui.tryGet(EffectRegistry.class);
        UiTheme theme = UiTheme.from(effectRegistry);

        ShapeRenderer sr = gui.tryGet(ShapeRenderer.class);
        BitmapFont font = getVectorFont();

        ScreenLayout L = new ScreenLayout(theme, sr != null ? sr : new ShapeRenderer(), font, effectRegistry);

        Table table = new Table();
        table.setFillParent(true);
        table.top().left();
        table.pad(ScreenLayout.PADDING_LEFT, stage.getHeight() - 140, 0, 0);

        // --- 页面标题 ---
        VectorLabel pageTitle = L.createPageTitle(gui.i18n("newGame.worldSettings.title"), 0, 0);
        table.add(pageTitle).colspan(2).left().padBottom(20).row();

        // --- 创建透明主题样式（供 TextField / SelectBox 使用） ---
        Skin skin = gui.get(Skin.class);

        // 世界名称
        VectorLabel nameLabel = createBodyLabel(L, gui.i18n("newGame.worldName"));
        table.add(nameLabel).left().padRight(10).padBottom(8);
        worldNameField = createThemedTextField(skin, gui.i18n("newGame.worldNameHint"), theme);
        table.add(worldNameField).width(220).padBottom(8).row();

        // 恒星系数量
        VectorLabel countLabel = createBodyLabel(L, gui.i18n("newGame.systemCount"));
        table.add(countLabel).left().padRight(10).padBottom(8);
        systemCountBox = createThemedSelectBox(skin, theme);
        systemCountBox.setItems("500", "1000", "2000", "4000", "8000", "10000");
        systemCountBox.setSelected("500");
        table.add(systemCountBox).width(220).padBottom(8).row();

        // 世界类型
        VectorLabel typeLabel = createBodyLabel(L, gui.i18n("newGame.worldType"));
        table.add(typeLabel).left().padRight(10).padBottom(8);
        worldTypeBox = createThemedSelectBox(skin, theme);
        worldTypeBox.setItems("SINGLE_PLAYER", "MULTI_PLAYER");
        worldTypeBox.setSelected("SINGLE_PLAYER");
        table.add(worldTypeBox).width(220).padBottom(8).row();

        // 世界种子
        VectorLabel seedLabel = createBodyLabel(L, gui.i18n("newGame.worldSeed"));
        table.add(seedLabel).left().padRight(10).padBottom(8);
        worldSeedField = createThemedTextField(skin, gui.i18n("newGame.worldSeedHint"), theme);
        table.add(worldSeedField).width(220).padBottom(8).row();

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

    /**
     * 创建正文标签（使用主题文本色）。
     */
    private VectorLabel createBodyLabel(ScreenLayout L, String text) {
        return new VectorLabel(getVectorFont(), text, L.getTheme().text);
    }

    /**
     * 创建透明主题化的 TextField。
     */
    private TextField createThemedTextField(Skin skin, String messageText, UiTheme theme) {
        TextField.TextFieldStyle style = buildTransparentTextFieldStyle(skin, theme);
        TextField field = new TextField("", style);
        field.setMessageText(messageText);
        return field;
    }

    /**
     * 创建透明主题化的 SelectBox。
     */
    private SelectBox<String> createThemedSelectBox(Skin skin, UiTheme theme) {
        SelectBox.SelectBoxStyle style = buildTransparentSelectBoxStyle(skin, theme);
        return new SelectBox<>(style);
    }

    /**
     * 构建透明 TextField 样式。
     * 背景使用半透明主题面板色，文本使用主题文本色。
     */
    private TextField.TextFieldStyle buildTransparentTextFieldStyle(Skin skin, UiTheme theme) {
        TextField.TextFieldStyle origStyle = skin.get(TextField.TextFieldStyle.class);
        TextField.TextFieldStyle style = new TextField.TextFieldStyle();
        style.font = origStyle.font;
        style.fontColor = theme.text;
        style.focusedFontColor = theme.textHover;
        style.messageFont = origStyle.messageFont;
        style.messageFontColor = theme.textMuted;
        style.background = createSolidDrawable(theme.panelBg);
        style.focusedBackground = createSolidDrawable(
                new Color(theme.primary.r, theme.primary.g, theme.primary.b, 0.15f));
        style.cursor = origStyle.cursor;
        style.selection = origStyle.selection;
        return style;
    }

    /**
     * 构建透明 SelectBox 样式。
     */
    private SelectBox.SelectBoxStyle buildTransparentSelectBoxStyle(Skin skin, UiTheme theme) {
        SelectBox.SelectBoxStyle origStyle = skin.get(SelectBox.SelectBoxStyle.class);
        SelectBox.SelectBoxStyle style = new SelectBox.SelectBoxStyle();
        style.font = origStyle.font;
        style.fontColor = theme.text;
        style.background = createSolidDrawable(theme.panelBg);
        style.backgroundOver = createSolidDrawable(
                new Color(theme.primary.r, theme.primary.g, theme.primary.b, 0.12f));
        style.backgroundOpen = createSolidDrawable(
                new Color(theme.primary.r, theme.primary.g, theme.primary.b, 0.10f));
        style.scrollStyle = origStyle.scrollStyle;
        // 下拉列表样式直接复用原始 Skin 的（保持与主题配色兼容即可）
        style.listStyle = origStyle.listStyle;
        return style;
    }

    /**
     * 创建纯色 Drawable（1x1 像素纹理拉伸，用于控件背景）。
     */
    private Drawable createSolidDrawable(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    /**
     * 获取矢量字体（用于 VectorLabel 渲染）。
     */
    private BitmapFont getVectorFont() {
        BitmapFont font = gui.tryGet(BitmapFont.class);
        if (font == null) {
            font = staraxis.ui.FontProvider.createVectorFont();
        }
        return font;
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
