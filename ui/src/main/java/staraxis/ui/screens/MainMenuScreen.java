package staraxis.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import staraxis.ui.Gui;
import staraxis.ui.i18n.I18nService;
import staraxis.ui.json.ComponentNode;
import staraxis.ui.json.UiFactory;
import staraxis.ui.json.UiParser;

import java.io.Reader;
import java.util.List;
import java.util.Properties;

/**
 * 主菜单 Screen（当前以 JSON 声明式 UI 驱动）。
 *
 * 设计要点：
 * - Screen 只负责“装配与显示”，不在这里硬编码布局细节；布局交给 `assets/ui/gameui/main-menu/main_menu.json`。
 * - JSON 解析失败时不应直接崩溃，避免阻断整体启动；失败原因会通过日志暴露，便于迭代期快速修复。
 * - 语言下拉属于纯 UI 状态（是否展开），在 Screen 内通过 Actor 可见性维护，不进入核心模拟层。
 * - 语言列表来自 i18n 文件扫描（本体 + mod），新增语言文件即可自动出现在下拉菜单里。
 */
public class MainMenuScreen implements Disposable {

    private static final String UI_PATH = "ui/gameui/main-menu/main_menu.json";

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    public MainMenuScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    public void show() {
        dispose();

        UiParser parser = gui.get(UiParser.class);
        UiFactory factory = gui.get(UiFactory.class);

        ComponentNode node = parser.parseInternal(UI_PATH);
        if (node == null) {
            Gdx.app.error("MainMenuScreen", "Failed to parse " + UI_PATH);
            return;
        }

        root = factory.create(node);
        stage.addActor(root);

        wireLanguageMenu();
    }

    private void wireLanguageMenu() {
        if (!(root instanceof Group)) {
            return;
        }

        Group group = (Group) root;
        Actor menuActor = group.findActor("lang_menu");
        Actor buttonActor = group.findActor("lang_button");

        if (!(buttonActor instanceof TextButton)) {
            return;
        }
        if (!(menuActor instanceof Table)) {
            return;
        }

        TextButton langButton = (TextButton) buttonActor;
        Table langMenu = (Table) menuActor;

        populateLanguageMenu(langMenu);

        langMenu.setVisible(false);
        updateLanguageButtonText(langButton, false);

        langButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean expanded = !langMenu.isVisible();
                langMenu.setVisible(expanded);
                updateLanguageButtonText(langButton, expanded);
            }
        });
    }

    private void populateLanguageMenu(Table langMenu) {
        langMenu.clearChildren();

        I18nService i18n = gui.get(I18nService.class);
        Skin skin = gui.get(Skin.class);
        if (i18n == null || skin == null) {
            return;
        }

        String current = i18n.getCurrentLanguage();
        List<String> languages = i18n.listAvailableLanguages();

        for (String code : languages) {
            String label = loadSelfNameOrFallback(code);
            TextButton btn = new TextButton(label, skin);
            btn.setName("lang_" + code);

            boolean isCurrent = code.equals(current);
            if (isCurrent) {
                // NOTE: 禁用当前语言项，避免重复刷新；同时用颜色提示“当前已选中”。
                btn.setDisabled(true);
                btn.getLabel().setColor(Color.LIGHT_GRAY);
            } else {
                btn.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        i18n.load(code);
                        // NOTE: 点击切换后自动收起：重建主菜单会重新 wireLanguageMenu() 并默认隐藏菜单。
                        gui.showMainMenu();
                    }
                });
            }

            langMenu.add(btn).width(140).height(40).pad(4).row();
        }
    }

    private String loadSelfNameOrFallback(String languageCode) {
        try {
            FileHandle fh = Gdx.files.internal("i18n/strings_" + languageCode + ".properties");
            if (!fh.exists()) {
                FileHandle modsDir = Gdx.files.local("gamedata/mods/");
                if (modsDir.exists() && modsDir.isDirectory()) {
                    for (FileHandle modDir : modsDir.list()) {
                        if (!modDir.isDirectory()) continue;
                        FileHandle modFile = modDir.child("i18n/strings_" + languageCode + ".properties");
                        if (modFile.exists()) {
                            fh = modFile;
                            break;
                        }
                    }
                }
            }

            if (fh.exists()) {
                Properties p = new Properties();
                try (Reader r = fh.reader("UTF-8")) {
                    p.load(r);
                }
                Object v = p.get("lang.selfName");
                if (v != null) {
                    String s = v.toString().trim();
                    if (!s.isEmpty()) return s;
                }
            }
        } catch (Exception ignored) {
        }
        return languageCode;
    }

    private void updateLanguageButtonText(TextButton button, boolean expanded) {
        String prefix = expanded ? "v " : "> ";
        button.setText(prefix + gui.i18n("lang.current"));
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
