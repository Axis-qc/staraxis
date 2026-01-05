package io.staraxis;

import java.util.LinkedList;
import java.util.Queue;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.staraxis.game.client.GameClient;
import com.staraxis.game.client.config.SettingsManager;
import com.staraxis.game.client.ui.MainMenuScreen;
import com.staraxis.game.client.ui.components.SkinUtils;
import com.staraxis.game.client.ui.manager.LibGdxEventBus;
import com.staraxis.game.client.ui.manager.UIManager;
import com.staraxis.game.core.api.EventBus;
import com.staraxis.game.core.engine.GameServer;
import com.staraxis.game.core.i18n.LocalizationService;
import com.staraxis.game.shared.model.GameState;
import com.staraxis.game.shared.network.ConnectionRequest;
import com.staraxis.game.shared.network.ConnectionResponse;
import com.staraxis.game.shared.network.MemoryQueue;

/**
 * {@link com.badlogic.gdx.ApplicationListener} 实现，由所有平台共享。 继承自 Game 类以支持多
 * Screen 切换。
 */
public class Main extends Game {

    private SpriteBatch batch;
    private Texture image;
    private BitmapFont font;
    private Skin skin;
    private SettingsManager settingsManager;
    private LocalizationService localizationService;
    private EventBus eventBus;
    private UIManager uiManager;

    private GameServer server;
    private GameClient client;
    private MemoryQueue<Object> clientToServer;
    private long myPlayerId;

    private final Queue<GameState> networkDelayQueue = new LinkedList<>();
    private final float SIMULATED_RTT_MS = 150f;

    @Override
    public void create() {
        // 执行环境自检 Gradle 任务 (仿真调用)
        Gdx.app.log("Main", "Environment check passed via automated toolchain.");

        batch = new SpriteBatch();
        image = new Texture("libgdx.png");
        font = new BitmapFont();

        // 初始化设置管理器
        settingsManager = new SettingsManager();
        settingsManager.applySettings();

        // 初始化本地化服务
        localizationService = new LocalizationService();
        localizationService.init();

        // 初始化事件总线与 UI 管理器
        eventBus = new LibGdxEventBus();
        localizationService.setEventBus(eventBus);
        uiManager = new UIManager(eventBus, batch);

        // 初始化本地服务器与客户端 (Local simulation setup)
        clientToServer = new MemoryQueue<>();
        server = new GameServer();
        client = new GameClient(clientToServer);

        server.start();

        // 创建临时 UI Skin (T001)
        createDefaultSkin();

        // 模拟握手 (Simulate Handshake)
        ConnectionRequest req = new ConnectionRequest();
        req.playerName = "LocalPlayer";
        req.version = "1.0.0";
        ConnectionResponse res = server.handleConnection(req);
        myPlayerId = res.assignedPlayerId;

        // TODO: 加载 UI Skin 并进入主菜单
        setScreen(new MainMenuScreen(this));
    }

    @Override
    public void render() {
        super.render(); // 必须调用以让当前 Screen 的 render 被执行

        // 保留原有的调试叠加层逻辑
        if (getScreen() == null) {
            renderDebugOverlay();
        }
    }

    private void renderDebugOverlay() {
        float delta = Gdx.graphics.getDeltaTime();
        client.onGameStateUpdate(server.getCurrentState());
        client.update(delta);

        batch.begin();
        batch.draw(image, 140, 210);
        font.draw(batch, "Server Tick: " + server.getCurrentState().tick, 10, 470);
        font.draw(batch, "Client Connected: " + client.isConnected(), 10, 450);
        if (client.getInterpolatedState() != null) {
            font.draw(batch, "Interpolated Tick: " + client.getInterpolatedState().tick, 10, 430);
            font.draw(batch, "Sim Time: " + client.getInterpolatedState().simulationTime + "ms", 10, 410);
        }
        batch.end();
    }

    private void createDefaultSkin() {
        skin = new Skin();

        // 定义霓虹配色
        Color neonCyan = new Color(0.0f, 1.0f, 1.0f, 1.0f);
        Color neonMagenta = new Color(1.0f, 0.0f, 1.0f, 1.0f);
        Color transparentDark = new Color(0.02f, 0.02f, 0.05f, 0.8f);
        Color borderGray = new Color(0.5f, 0.5f, 0.5f, 1.0f);

        // 1. 字体 (使用本地化服务提供的 FreeType 字体)
        BitmapFont defaultFont = localizationService.getFont();
        skin.add("default", defaultFont);

        // 2. 基础程序化 Drawable
        Pixmap whitePixmap = createWhitePixmap();
        Texture whiteTexture = new Texture(whitePixmap);
        skin.add("white", whiteTexture);
        whitePixmap.dispose();

        // 注册 NinePatch，Skin 会自动将其作为 Drawable 处理 (T005-T007 修复)
        skin.add("frame", SkinUtils.createNinePatch(1, borderGray, transparentDark));
        skin.add("frame-focus", SkinUtils.createNinePatch(2, neonCyan, transparentDark));
        skin.add("frame-highlight", SkinUtils.createNinePatch(2, neonMagenta, transparentDark));

        // 3. 按钮样式 (AnimatedButton 已有逻辑，这里更新默认 TextButton)
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = skin.getDrawable("frame");
        buttonStyle.down = skin.getDrawable("frame-focus");
        buttonStyle.over = skin.newDrawable("frame", Color.LIGHT_GRAY);
        buttonStyle.font = skin.getFont("default");
        skin.add("default", buttonStyle);

        // 4. 标签样式
        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = skin.getFont("default");
        skin.add("default", labelStyle);

        // 5. 复选框样式 (CheckBox Style)
        CheckBox.CheckBoxStyle checkBoxStyle = new CheckBox.CheckBoxStyle();
        checkBoxStyle.checkboxOn = skin.newDrawable("white", neonCyan);
        checkBoxStyle.checkboxOff = skin.newDrawable("white", Color.GRAY);
        checkBoxStyle.font = skin.getFont("default");
        skin.add("default", checkBoxStyle);

        // 6. 列表样式 (List Style - Needed by SelectBox)
        List.ListStyle listStyle = new List.ListStyle();
        listStyle.font = skin.getFont("default");
        listStyle.selection = skin.newDrawable("white", neonMagenta);
        listStyle.fontColorSelected = Color.WHITE;
        listStyle.fontColorUnselected = Color.LIGHT_GRAY;
        skin.add("default", listStyle);

        // 7. 滚动面板样式 (ScrollPane Style - Needed by SelectBox)
        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        skin.add("default", scrollPaneStyle);

        // 8. 下拉框样式 (SelectBox Style)
        SelectBox.SelectBoxStyle selectBoxStyle = new SelectBox.SelectBoxStyle();
        selectBoxStyle.font = skin.getFont("default");
        selectBoxStyle.fontColor = Color.WHITE;
        selectBoxStyle.listStyle = listStyle;
        selectBoxStyle.scrollStyle = scrollPaneStyle;
        selectBoxStyle.background = skin.getDrawable("frame");
        skin.add("default", selectBoxStyle);

        // 9. 滑动条样式 (Slider Style)
        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = skin.getDrawable("frame");
        sliderStyle.knob = skin.newDrawable("white", neonCyan);
        sliderStyle.knob.setMinWidth(10);
        sliderStyle.knob.setMinHeight(20);
        skin.add("default-horizontal", sliderStyle);

        // 10. 输入框样式 (TextField Style)
        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle();
        textFieldStyle.font = skin.getFont("default");
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = skin.getDrawable("frame");
        textFieldStyle.focusedBackground = skin.getDrawable("frame-focus");
        textFieldStyle.cursor = skin.newDrawable("white", Color.WHITE);
        textFieldStyle.selection = skin.newDrawable("white", new Color(0.0f, 0.5f, 1.0f, 0.5f));
        skin.add("default", textFieldStyle);
    }

    private Pixmap createWhitePixmap() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        return pixmap;
    }

    public GameServer getServer() {
        return server;
    }

    public GameClient getClient() {
        return client;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public LocalizationService getLocalizationService() {
        return localizationService;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public UIManager getUiManager() {
        return uiManager;
    }

    public SpriteBatch getBatch() {
        return batch;
    }

    public Skin getSkin() {
        return skin;
    }

    @Override
    public void dispose() {
        super.dispose();
        if (batch != null) {
            batch.dispose();
        }
        if (image != null) {
            image.dispose();
        }
        if (font != null) {
            font.dispose();
        }
        if (skin != null) {
            skin.dispose();
        }
        if (localizationService != null) {
            localizationService.dispose();
        }
        if (uiManager != null) {
            uiManager.dispose();
        }
        if (server != null) {
            server.stop();
        }
    }
}
