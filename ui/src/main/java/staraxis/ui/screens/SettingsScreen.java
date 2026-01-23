package staraxis.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import staraxis.ui.Gui;
import staraxis.ui.json.ComponentNode;
import staraxis.ui.json.UiFactory;
import staraxis.ui.json.UiParser;
import staraxis.ui.settings.GameSettings;
import staraxis.ui.settings.ModManager;
import staraxis.ui.settings.ModMetadata;
import staraxis.ui.settings.ModMetadataRepository;
import staraxis.ui.settings.ModOrderRepository;
import staraxis.ui.settings.SettingsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 设置界面 Screen。
 */
public class SettingsScreen implements Disposable {

    private static final String UI_PATH = "ui/gameui/settings/settings.json";

    private static final String MOD_SETTINGS_UI_DIR = "../gamedata/mods";

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    private final ModManager modManager;

    private static class TabBinding {
        final String tabId;
        final String buttonActorName;
        final String pageActorName;

        TabBinding(String tabId, String buttonActorName, String pageActorName) {
            this.tabId = tabId;
            this.buttonActorName = buttonActorName;
            this.pageActorName = pageActorName;
        }
    }

    private final Map<String, TabBinding> tabBindings = new HashMap<>();

    private String activeTab = "general";

    private GameSettings currentSettings;

    private static final Integer[] FPS_PRESETS = { 0, 30, 60, 120, 144, 240 };
    private List<String> resolutionOptions = new ArrayList<>();
    private String selectedFpsText;

    private static final float SCALE_STEP = 0.1f;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 2.0f;

    public SettingsScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
        this.modManager = new ModManager(new ModOrderRepository(), new ModMetadataRepository());
    }

    public void show() {
        dispose();

        SettingsRepository repository = gui.get(SettingsRepository.class);
        if (repository == null) {
            Gdx.app.error("SettingsScreen", "SettingsRepository not found.");
            return;
        }
        this.currentSettings = repository.load();
        this.selectedFpsText = String.valueOf(currentSettings.fpsLimit);

        buildResolutionOptions();

        UiParser parser = gui.get(UiParser.class);
        UiFactory factory = gui.get(UiFactory.class);
        ComponentNode node = parser.parseInternal(UI_PATH);
        if (node == null) {
            Gdx.app.error("SettingsScreen", "Failed to parse " + UI_PATH);
            return;
        }

        root = factory.create(node);
        stage.addActor(root);

        registerBuiltInTabs();
        loadAndRegisterModTabs();

        bindDataToView();
        renderResolutionRepeat();
        renderFpsRepeat();
        openTab(activeTab);
        stage.act(0f);
    }

    private void buildResolutionOptions() {
        Set<String> unique = new TreeSet<>((a, b) -> {
            String[] pa = a.split("x");
            String[] pb = b.split("x");
            int ha = Integer.parseInt(pa[1]);
            int hb = Integer.parseInt(pb[1]);
            if (ha != hb)
                return Integer.compare(ha, hb);
            int wa = Integer.parseInt(pa[0]);
            int wb = Integer.parseInt(pb[0]);
            return Integer.compare(wa, wb);
        });

        try {
            for (DisplayMode m : Gdx.graphics.getDisplayModes()) {
                if (m.width >= 800 && m.height >= 600) {
                    unique.add(m.width + "x" + m.height);
                }
            }
        } catch (Exception ignored) {
        }

        if (unique.isEmpty()) {
            Collections.addAll(unique, "1280x720", "1600x900", "1920x1080");
        }

        resolutionOptions = new ArrayList<>(unique);
    }

    private void bindDataToView() {
        if (!(root instanceof Group))
            return;
        Group g = (Group) root;

        TextButton resBtn = g.findActor("resolution_button");
        if (resBtn != null)
            resBtn.setText(currentSettings.resolution);

        TextButton fpsBtn = g.findActor("fps_limit_button");
        if (fpsBtn != null)
            updateFpsLimitButtonText(fpsBtn);

        TextButton vsyncBtn = g.findActor("vsync_button");
        if (vsyncBtn != null)
            updateVsyncButtonText(vsyncBtn);

        Slider vol = g.findActor("master_volume_slider");
        if (vol != null)
            vol.setValue(currentSettings.masterVolume);

        refreshUiScaleLabel();
        refreshFontScaleLabel();

        Actor resPopup = g.findActor("resolution_popup");
        if (resPopup != null)
            resPopup.setVisible(false);
        Actor fpsPopup = g.findActor("fps_popup");
        if (fpsPopup != null)
            fpsPopup.setVisible(false);
    }

    private void alignPopupToButton(Actor popup, Actor button) {
        if (popup == null || button == null || popup.getParent() == null)
            return;

        stage.act(0f);

        float popupW = popup.getWidth();
        float popupH = popup.getHeight();

        if (popupW <= 0)
            popupW = 240f;
        if (popupH <= 0)
            popupH = 240f;

        Vector2 buttonStage = new Vector2(0, 0);
        button.localToStageCoordinates(buttonStage);

        Vector2 parentStage = new Vector2(0, 0);
        popup.getParent().localToStageCoordinates(parentStage);

        float targetXStage = buttonStage.x + button.getWidth();
        float targetYStage = buttonStage.y + button.getHeight() - popupH;

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();

        if (targetXStage + popupW > screenW)
            targetXStage = screenW - popupW;
        if (targetXStage < 0)
            targetXStage = 0;

        if (targetYStage < 0)
            targetYStage = 0;
        if (targetYStage + popupH > screenH)
            targetYStage = screenH - popupH;

        float x = targetXStage - parentStage.x;
        float y = targetYStage - parentStage.y;

        popup.setPosition(x, y);
        popup.toFront();
        stage.act(0f);
    }

    public void openTab(String tabId) {
        if (tabId == null || tabId.isBlank()) {
            tabId = "general";
        }

        if (tabBindings.containsKey(tabId)) {
            activeTab = tabId;
        } else {
            activeTab = "general";
        }

        if (!(root instanceof Group)) {
            return;
        }
        Group g = (Group) root;

        for (TabBinding binding : tabBindings.values()) {
            Actor page = g.findActor(binding.pageActorName);
            if (page != null) {
                page.setVisible(binding.tabId.equals(activeTab));
            }
        }

        Actor resPopup = g.findActor("resolution_popup");
        if (resPopup != null) {
            resPopup.setVisible(false);
        }
        Actor fpsPopup = g.findActor("fps_popup");
        if (fpsPopup != null) {
            fpsPopup.setVisible(false);
        }

        if ("mods".equals(activeTab)) {
            renderModsList();
        }

        applyTabHighlight(g);
        stage.act(0f);
    }

    private void applyTabHighlight(Group rootGroup) {
        if (rootGroup == null) {
            return;
        }

        // NOTE: 文字要求统一为白色：tab 高亮只通过 alpha 表现（更亮/更暗）。
        com.badlogic.gdx.graphics.Color white = com.badlogic.gdx.graphics.Color.valueOf("FFFFFFFF");

        for (TabBinding binding : tabBindings.values()) {
            Actor a = rootGroup.findActor(binding.buttonActorName);
            if (!(a instanceof TextButton)) {
                continue;
            }

            TextButton b = (TextButton) a;

            b.setColor(white);
            if (binding.tabId.equals(activeTab)) {
                b.getColor().a = 1.0f;
            } else {
                b.getColor().a = 0.72f;
            }
        }
    }

    private void registerBuiltInTabs() {
        tabBindings.clear();
        registerTabBinding("general", "tab_general", "settings_scroll");
        registerTabBinding("graphics", "tab_graphics", "graphics_scroll");
        registerTabBinding("input", "tab_input", "input_scroll");
        registerTabBinding("other", "tab_other", "other_scroll");
        registerTabBinding("mods", "open_mod_list_button", "mods_scroll");

        // NOTE: ExampleMod 示例 Tab 不在本体内提供；这里仅注册本体内建的 Tab。
    }

    private void registerTabBinding(String tabId, String buttonActorName, String pageActorName) {
        if (tabId == null || tabId.isBlank()) {
            return;
        }
        if (buttonActorName == null || buttonActorName.isBlank()) {
            return;
        }
        if (pageActorName == null || pageActorName.isBlank()) {
            return;
        }
        tabBindings.put(tabId, new TabBinding(tabId, buttonActorName, pageActorName));
    }

    private void loadAndRegisterModTabs() {
        Gdx.app.log("SettingsScreen", "Scanning mod settings tabs under: " + MOD_SETTINGS_UI_DIR);
        if (!(root instanceof Group)) {
            return;
        }

        Group g = (Group) root;
        Actor tabBarActor = g.findActor("tab_bar");
        Actor stackActor = g.findActor("settings_stack");

        if (!(tabBarActor instanceof Table)) {
            return;
        }
        if (!(stackActor instanceof Group)) {
            return;
        }

        Table tabBar = (Table) tabBarActor;
        Group settingsStack = (Group) stackActor;

        com.badlogic.gdx.files.FileHandle modsDir = Gdx.files.local(MOD_SETTINGS_UI_DIR);
        if (!modsDir.exists() || !modsDir.isDirectory()) {
            return;
        }

        com.badlogic.gdx.files.FileHandle[] modDirs = modsDir.list();
        java.util.Arrays.sort(modDirs, java.util.Comparator.comparing(com.badlogic.gdx.files.FileHandle::name));

        UiParser parser = gui.get(UiParser.class);
        UiFactory factory = gui.get(UiFactory.class);
        if (parser == null || factory == null) {
            return;
        }

        for (com.badlogic.gdx.files.FileHandle modDir : modDirs) {
            if (!modDir.isDirectory()) {
                continue;
            }

            com.badlogic.gdx.files.FileHandle settingsDir = modDir.child("ui/settings");
            if (!settingsDir.exists() || !settingsDir.isDirectory()) {
                continue;
            }

            com.badlogic.gdx.files.FileHandle tabsDir = settingsDir.child("tabs");
            if (!tabsDir.exists() || !tabsDir.isDirectory()) {
                continue;
            }

            com.badlogic.gdx.files.FileHandle[] tabFiles = tabsDir.list("json");
            java.util.Arrays.sort(tabFiles, java.util.Comparator.comparing(com.badlogic.gdx.files.FileHandle::name));

            for (com.badlogic.gdx.files.FileHandle tabFile : tabFiles) {
                try {
                    String tabId = tabFile.nameWithoutExtension();
                    if (tabId == null || tabId.isBlank()) {
                        continue;
                    }

                    String json = tabFile.readString("UTF-8");
                    ComponentNode tabNode = parser.parseString(tabFile.path(), json);
                    if (tabNode == null) {
                        continue;
                    }

                    String buttonActorName = "tab_mod_" + modDir.name() + "_" + tabId;
                    tabNode.name = buttonActorName;
                    tabNode.properties.put("onClick", "OPEN_SETTINGS_TAB:" + buttonActorName);

                    Actor tabButton = factory.create(tabNode);

                    String pageActorName = "page_mod_" + modDir.name() + "_" + tabId;
                    com.badlogic.gdx.files.FileHandle pageFile = settingsDir.child("pages/" + tabId + ".json");
                    if (!pageFile.exists()) {
                        continue;
                    }

                    ComponentNode pageRoot = parser.parseString(pageFile.path(), pageFile.readString("UTF-8"));
                    if (pageRoot == null) {
                        continue;
                    }

                    if (pageRoot.name == null || pageRoot.name.isBlank()) {
                        pageRoot.name = pageActorName;
                    } else {
                        pageActorName = pageRoot.name;
                    }

                    Actor pageActor = factory.create(pageRoot);
                    pageActor.setVisible(false);

                    tabBar.add(tabButton).height(42).growX().fillX().padBottom(8).row();
                    settingsStack.addActor(pageActor);

                    registerTabBinding(buttonActorName, buttonActorName, pageActorName);
                } catch (Exception e) {
                    Gdx.app.error("SettingsScreen", "Failed to load mod settings tab from " + tabFile.path(), e);
                }
            }
        }
    }

    public void toggleResolutionList() {
        if (!(root instanceof Group))
            return;
        Group g = (Group) root;
        Actor resPopup = g.findActor("resolution_popup");
        Actor fpsPopup = g.findActor("fps_popup");
        Actor resBtn = g.findActor("resolution_button");

        if (fpsPopup != null)
            fpsPopup.setVisible(false);
        if (resPopup != null) {
            boolean newVisible = !resPopup.isVisible();
            resPopup.setVisible(newVisible);
            if (newVisible)
                alignPopupToButton(resPopup, resBtn);
        }
    }

    public void toggleFpsList() {
        if (!(root instanceof Group))
            return;
        Group g = (Group) root;
        Actor resPopup = g.findActor("resolution_popup");
        Actor fpsPopup = g.findActor("fps_popup");
        Actor fpsBtn = g.findActor("fps_limit_button");

        if (resPopup != null)
            resPopup.setVisible(false);
        if (fpsPopup != null) {
            boolean newVisible = !fpsPopup.isVisible();
            fpsPopup.setVisible(newVisible);
            if (newVisible)
                alignPopupToButton(fpsPopup, fpsBtn);
        }
    }

    private void renderResolutionRepeat() {
        if (!(root instanceof Group))
            return;
        Group g = (Group) root;
        Actor repeatActor = g.findActor("resolution_repeat");
        if (!(repeatActor instanceof Group))
            return;
        UiFactory factory = gui.get(UiFactory.class);
        if (factory == null)
            return;
        factory.renderRepeatItems((Group) repeatActor, toRepeatItemsData(resolutionOptions, currentSettings.resolution,
                "SELECT_RESOLUTION"));
    }

    private void renderFpsRepeat() {
        if (!(root instanceof Group))
            return;
        Group g = (Group) root;
        Actor repeatActor = g.findActor("fps_repeat");
        if (!(repeatActor instanceof Group))
            return;
        UiFactory factory = gui.get(UiFactory.class);
        if (factory == null)
            return;
        List<String> fpsItems = new ArrayList<>();
        for (Integer v : FPS_PRESETS)
            fpsItems.add(String.valueOf(v));
        factory.renderRepeatItems((Group) repeatActor,
                toRepeatItemsData(fpsItems, selectedFpsText, "SELECT_FPS_LIMIT"));
    }

    public void selectResolution(String resolution) {
        if (resolution == null || resolution.isBlank())
            return;
        currentSettings.resolution = resolution;
        try {
            String[] p = resolution.split("x");
            int w = Integer.parseInt(p[0]);
            int h = Integer.parseInt(p[1]);
            Gdx.graphics.setWindowedMode(w, h);
        } catch (Exception ignored) {
        }
        refreshResolutionButton();
        renderResolutionRepeat();
        hidePopups();
    }

    public void selectFpsLimit(String fpsText) {
        if (fpsText == null || fpsText.isBlank())
            return;
        try {
            int v = Integer.parseInt(fpsText);
            currentSettings.fpsLimit = v;
            selectedFpsText = fpsText;
            try {
                Gdx.graphics.setForegroundFPS(v);
            } catch (Exception ignored) {
            }
            refreshFpsLimitButton();
            renderFpsRepeat();
            hidePopups();
        } catch (Exception ignored) {
        }
    }

    private List<Map<String, Object>> toRepeatItemsData(List<String> items, String selected, String actionPrefix) {
        List<Map<String, Object>> data = new ArrayList<>();
        if (items == null) {
            return data;
        }

        for (String item : items) {
            Map<String, Object> row = new HashMap<>();
            row.put("item", item != null ? item : "");
            row.put("selected", item != null && item.equals(selected));
            row.put("action", actionPrefix + ":" + (item != null ? item : ""));
            data.add(row);
        }
        return data;
    }

    private void hidePopups() {
        if (!(root instanceof Group))
            return;
        Group g = (Group) root;
        Actor resPopup = g.findActor("resolution_popup");
        if (resPopup != null)
            resPopup.setVisible(false);
        Actor fpsPopup = g.findActor("fps_popup");
        if (fpsPopup != null)
            fpsPopup.setVisible(false);
    }

    public void toggleVsync() {
        currentSettings.vsync = !currentSettings.vsync;
        Gdx.graphics.setVSync(currentSettings.vsync);
        refreshVsyncButton();
    }

    public void setMasterVolume(float v) {
        currentSettings.masterVolume = v;
        refreshMasterVolumeSlider();
    }

    public void increaseUiScale() {
        changeUiScale(SCALE_STEP);
    }

    public void decreaseUiScale() {
        changeUiScale(-SCALE_STEP);
    }

    public void increaseFontScale() {
        changeFontScale(SCALE_STEP);
    }

    public void decreaseFontScale() {
        changeFontScale(-SCALE_STEP);
    }

    private void changeUiScale(float delta) {
        float newScale = currentSettings.uiScale + delta;
        newScale = new BigDecimal(newScale).setScale(1, RoundingMode.HALF_UP).floatValue();
        if (newScale >= MIN_SCALE && newScale <= MAX_SCALE) {
            currentSettings.uiScale = newScale;
            gui.applyUiScale(newScale);
            refreshUiScaleLabel();
        }
    }

    private void changeFontScale(float delta) {
        float newScale = currentSettings.fontScale + delta;
        newScale = new BigDecimal(newScale).setScale(1, RoundingMode.HALF_UP).floatValue();
        if (newScale >= MIN_SCALE && newScale <= MAX_SCALE) {
            currentSettings.fontScale = newScale;
            gui.applyFontScale(newScale);
            refreshFontScaleLabel();
        }
    }

    public void saveSettings() {
        SettingsRepository r = gui.get(SettingsRepository.class);
        if (r != null)
            r.save(currentSettings);
    }

    private void refreshUiScaleLabel() {
        if (root instanceof Group) {
            Label label = ((Group) root).findActor("ui_scale_label");
            if (label != null)
                label.setText(String.format("%.1fx", currentSettings.uiScale));
        }
    }

    private void refreshFontScaleLabel() {
        if (root instanceof Group) {
            Label label = ((Group) root).findActor("font_scale_label");
            if (label != null)
                label.setText(String.format("%.1fx", currentSettings.fontScale));
        }
    }

    private void refreshResolutionButton() {
        if (root instanceof Group) {
            TextButton b = ((Group) root).findActor("resolution_button");
            if (b != null)
                b.setText(currentSettings.resolution);
        }
    }

    private void refreshMasterVolumeSlider() {
        if (root instanceof Group) {
            Slider s = ((Group) root).findActor("master_volume_slider");
            if (s != null)
                s.setValue(currentSettings.masterVolume);
        }
    }

    private void refreshVsyncButton() {
        if (root instanceof Group) {
            TextButton b = ((Group) root).findActor("vsync_button");
            if (b != null)
                updateVsyncButtonText(b);
        }
    }

    private void refreshFpsLimitButton() {
        if (root instanceof Group) {
            TextButton b = ((Group) root).findActor("fps_limit_button");
            if (b != null)
                updateFpsLimitButtonText(b);
        }
    }

    private void updateVsyncButtonText(TextButton button) {
        button.setText(gui.i18n(currentSettings.vsync ? "common.on" : "common.off"));
    }

    private void updateFpsLimitButtonText(TextButton button) {
        button.setText(currentSettings.fpsLimit == 0 ? gui.i18n("settings.unlimited")
                : String.valueOf(currentSettings.fpsLimit));
    }

    private void renderModsList() {
        if (!(root instanceof Group)) {
            return;
        }

        Group g = (Group) root;
        Actor repeatActor = g.findActor("mods_repeat");
        if (!(repeatActor instanceof Group)) {
            return;
        }

        UiFactory factory = gui.get(UiFactory.class);
        if (factory == null) {
            return;
        }

        List<ModMetadata> mods = modManager.loadModsOrdered();
        List<Map<String, Object>> itemsData = new ArrayList<>();
        for (ModMetadata meta : mods) {
            Map<String, Object> data = new HashMap<>();
            data.put("modId", meta.modId);
            data.put("modName", meta.name != null ? meta.name : meta.modId);
            data.put("compatibleGameVersion", meta.compatibleGameVersion != null ? meta.compatibleGameVersion : "");
            data.put("description", meta.description != null ? meta.description : "");
            data.put("enabled", true);
            itemsData.add(data);
        }

        factory.renderRepeatItemsWithActors((Group) repeatActor, itemsData, (rowActor, data) -> {
            if (!(rowActor instanceof Group)) {
                return;
            }

            String modId = data.get("modId") != null ? data.get("modId").toString() : null;
            if (modId != null && !modId.isBlank()) {
                rowActor.setUserObject(modId);
            }

            Group rowGroup = (Group) rowActor;

            Actor detailActor = rowGroup.findActor("mod_detail_template");
            if (detailActor != null) {
                detailActor.setVisible(false);
            }

            Label expandLabel = rowGroup.findActor("expand");
            if (expandLabel != null) {
                expandLabel.setText(">");
            }

            Runnable toggleDetail = () -> {
                if (detailActor == null) {
                    return;
                }
                boolean newVisible = !detailActor.isVisible();
                detailActor.setVisible(newVisible);
                if (expandLabel != null) {
                    expandLabel.setText(newVisible ? "v" : ">");
                }
                if (repeatActor instanceof com.badlogic.gdx.scenes.scene2d.ui.Table) {
                    ((com.badlogic.gdx.scenes.scene2d.ui.Table) repeatActor).invalidateHierarchy();
                }
            };

            if (expandLabel != null) {
                expandLabel.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        toggleDetail.run();
                    }
                });
            }

            Label nameLabel = rowGroup.findActor("mod_name");
            if (nameLabel != null) {
                nameLabel.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        toggleDetail.run();
                    }
                });
            }

            // 拖拽交互由 DragAndDrop 统一注册（见 renderModsList 末尾 setupModListDragAndDrop）

            CheckBox enabledCheckBox = rowGroup.findActor("mod_enabled");
            if (enabledCheckBox != null) {
                Object enabledValue = data.get("enabled");
                enabledCheckBox.setChecked(enabledValue instanceof Boolean && (Boolean) enabledValue);
                enabledCheckBox.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        // NOTE: 仅 UI 暂存；启用/禁用的持久化与加载链路后续再接。
                    }
                });
            }
        });

        if (repeatActor instanceof com.badlogic.gdx.scenes.scene2d.ui.Table) {
            Table t = (Table) repeatActor;
            t.invalidateHierarchy();
            setupModListDragAndDrop(t);
        }
    }

    private final DragAndDrop modListDragAndDrop = new DragAndDrop();

    private void setupModListDragAndDrop(Table repeatTable) {
        if (repeatTable == null) {
            return;
        }

        // 先清理旧的 sources/targets（DragAndDrop 无 clear API，只能新建实例；这里保持实例常驻，重复 add 也没问题，因为
        // Actor 会被重建）
        // 由于 renderModsList 每次都会重新 create row actors，这里每次调用都需要重新注册。
        for (Actor row : repeatTable.getChildren()) {
            if (!(row instanceof Group)) {
                continue;
            }

            Actor handle = ((Group) row).findActor("handle");
            if (handle == null) {
                continue;
            }

            modListDragAndDrop.addSource(new ModRowDragSource(handle, row, repeatTable));
            modListDragAndDrop.addTarget(new ModRowDragTarget(row, repeatTable));
        }
    }

    private Actor buildDragPreview(Actor rowActor) {
        Table preview = new Table();
        preview.setSize(rowActor.getWidth(), rowActor.getHeight());
        preview.getColor().a = 0.75f;

        if (rowActor instanceof Group) {
            Actor modName = ((Group) rowActor).findActor("mod_name");
            Actor compat = ((Group) rowActor).findActor("mod_compat");

            String nameText = modName instanceof Label ? ((Label) modName).getText().toString() : "";
            String compatText = compat instanceof Label ? ((Label) compat).getText().toString() : "";

            Label nameLabel = new Label(nameText, gui.get(com.badlogic.gdx.scenes.scene2d.ui.Skin.class));
            Label compatLabel = new Label(compatText, gui.get(com.badlogic.gdx.scenes.scene2d.ui.Skin.class));
            nameLabel.getColor().a = 0.95f;
            compatLabel.getColor().a = 0.85f;

            preview.add(nameLabel).left().growX().fillX().padLeft(12);
            preview.add(compatLabel).right().padRight(12).width(120);
        }

        return preview;
    }

    private class ModRowDragSource extends DragAndDrop.Source {

        private final Actor rowActor;
        private final Table repeatTable;

        ModRowDragSource(Actor handleActor, Actor rowActor, Table repeatTable) {
            super(handleActor);
            this.rowActor = rowActor;
            this.repeatTable = repeatTable;
        }

        @Override
        public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
            if (rowActor == null || repeatTable == null) {
                return null;
            }

            DragAndDrop.Payload payload = new DragAndDrop.Payload();
            payload.setObject(rowActor);

            // 不能直接用原行作为 dragActor：DragAndDrop 会临时把它挂到 stage，导致列表布局错乱。
            // 这里用一个半透明的快照（只复制文本），实现“跟随鼠标”的视觉反馈。
            payload.setDragActor(buildDragPreview(rowActor));
            payload.setValidDragActor(payload.getDragActor());
            payload.setInvalidDragActor(payload.getDragActor());

            // 轻微透明作为拖拽反馈
            rowActor.getColor().a = 0.82f;
            return payload;
        }

        @Override
        public void dragStop(InputEvent event, float x, float y, int pointer, DragAndDrop.Payload payload,
                DragAndDrop.Target target) {
            if (rowActor != null) {
                rowActor.getColor().a = 1.0f;
            }
            super.dragStop(event, x, y, pointer, payload, target);
        }
    }

    private class ModRowDragTarget extends DragAndDrop.Target {

        private final Actor targetRow;
        private final Table repeatTable;

        ModRowDragTarget(Actor targetRow, Table repeatTable) {
            super(targetRow);
            this.targetRow = targetRow;
            this.repeatTable = repeatTable;
        }

        @Override
        public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
            // 只允许在同一个列表内拖拽
            if (!(payload.getObject() instanceof Actor)) {
                return false;
            }
            return true;
        }

        @Override
        public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
            if (!(payload.getObject() instanceof Actor)) {
                return;
            }
            Actor draggedRow = (Actor) payload.getObject();

            int from = indexOf(repeatTable, draggedRow);
            int to = indexOf(repeatTable, targetRow);
            if (from < 0 || to < 0 || from == to) {
                return;
            }

            // 仅支持列表内垂直排序：按照目标行索引插入
            repeatTable.getChildren().removeValue(draggedRow, true);
            repeatTable.getChildren().insert(to, draggedRow);
            repeatTable.invalidateHierarchy();

            List<String> newOrder = new ArrayList<>();
            for (Actor a : repeatTable.getChildren()) {
                Object uo = a.getUserObject();
                if (uo instanceof String) {
                    newOrder.add((String) uo);
                }
            }
            modManager.saveOrder(newOrder);
        }

        private int indexOf(Table table, Actor actor) {
            int idx = 0;
            for (Actor a : table.getChildren()) {
                if (a == actor) {
                    return idx;
                }
                idx++;
            }
            return -1;
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
