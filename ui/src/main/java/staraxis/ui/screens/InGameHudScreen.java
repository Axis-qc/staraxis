package staraxis.ui.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Disposable;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.ui.FontProvider;
import staraxis.ui.Gui;
import staraxis.ui.UiWindowManager;
import staraxis.ui.effects.EffectRegistry;
import staraxis.ui.json.ComponentNode;
import staraxis.ui.json.UiFactory;
import staraxis.ui.json.UiParser;
import staraxis.ui.panels.EntityInfoPanel;
import staraxis.ui.panels.EntityInfoViewModel;
import staraxis.ui.panels.EntityPanelDemoData;
import staraxis.ui.panels.EntitySummaryPanel;
import staraxis.ui.theme.UiTheme;
import staraxis.ui.widgets.HoverTooltipBinder;
import staraxis.ui.widgets.VectorLabel;
import staraxis.ui.widgets.VectorWindow;

public class InGameHudScreen implements Disposable {

    private static final String UI_PATH = "ui/gameui/ingame-hud/ingame_hud.json";

    /** 钉住窗口的分组 id（恒星系信息窗口，多例上限由 UiWindowManager 控制）喵 */
    private static final String PIN_GROUP_STAR_SYSTEM = "star-system";

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    /** 主题引用（来自 EffectRegistry）。 */
    private UiTheme theme;

    /** 形状渲染器引用（来自 Gui）。 */
    private ShapeRenderer sr;

    /** 通用悬停提示绑定器（恒星 tooltip 的显示/钉住状态机）喵 */
    private HoverTooltipBinder tooltipBinder;

    /** 最近一次收到的日结算快照，钉住开窗时重建信息文本用喵 */
    private DailySettlementState lastDailyState;

    /** 左下实体摘要面板喵 */
    private EntitySummaryPanel summaryPanel;

    /** TODO Phase 2 Step B：以下为假数据演示字段，接真实数据（SelectionService + Assembler）后删除喵 */
    private static final java.util.function.Supplier<EntityInfoViewModel>[] DEMO_SUPPLIERS =
            new java.util.function.Supplier[] {
                    EntityPanelDemoData::star,
                    EntityPanelDemoData::planet,
                    EntityPanelDemoData::moon,
                    EntityPanelDemoData::ship
            };
    /** TODO Phase 2 Step B：演示实体索引，接真实数据后删除喵 */
    private int demoIndex = 1;
    /** TODO Phase 2 Step B：当前演示视图模型，接真实数据后删除喵 */
    private EntityInfoViewModel demoCurrentVm;

    public InGameHudScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    public void show() {
        dispose();

        // 缓存 ShapeRenderer 和 UiTheme 引用
        if (sr == null) {
            sr = gui.get(ShapeRenderer.class);
        }
        if (theme == null) {
            theme = UiTheme.from(gui.tryGet(EffectRegistry.class));
        }

        // 懒创建悬停绑定器，钉住回调接到窗口管理器喵
        if (tooltipBinder == null && sr != null && theme != null) {
            tooltipBinder = new HoverTooltipBinder(stage, sr,
                    FontProvider.createVectorFont(), theme);
            tooltipBinder.setPinListener(this::openPinnedStarWindow);
        }
        if (tooltipBinder != null) {
            tooltipBinder.forceHide();
        }

        // 懒创建左下实体摘要面板（stage.clear() 切屏后由 ensureAttached 自动恢复）喵
        if (summaryPanel == null && sr != null && theme != null) {
            summaryPanel = new EntitySummaryPanel(sr, FontProvider.createVectorFont(), theme);
            summaryPanel.setPosition(8, 8);
            summaryPanel.setActionListener(this::onSummaryAction);
        }
        if (summaryPanel != null && summaryPanel.getStage() == null) {
            stage.addActor(summaryPanel);
        }
        // TODO Phase 2 Step B：假数据演示初始展示，接真实数据后删除喵
        demoShowCurrent();

        UiParser parser = gui.get(UiParser.class);
        UiFactory factory = gui.get(UiFactory.class);
        if (parser == null || factory == null) return;

        ComponentNode node = parser.parseInternal(UI_PATH);
        if (node == null) return;

        root = factory.create(node);
        if (root != null) {
            stage.addActor(root);
        }

        refreshHud();
    }

    public void refreshHud() {
        StarAxisGameRuntime rt = gui.getRuntime();
        if (rt == null) return;

        RealTimeWorldState state = rt.getRealTimeWorldStateReadonly();
        if (state == null) return;

        if (root instanceof Group rootGroup) {
            Actor timeActor = rootGroup.findActor("time_label");
            if (timeActor instanceof VectorLabel vl) {
                vl.setText(String.format("Y%d M%d D%d %02d:%02d",
                        state.year, state.month, state.day, state.hour, state.minute));
            }
        }
    }

    /**
     * 更新视图标签（左上角），显示当前视图类型与缩放层级。
     *
     * @param text 视图标签文字，例如 "星系视图" 或 "恒星系视图 · 缩放 x4.0"
     */
    public void updateViewInfo(String text) {
        if (!(root instanceof Group)) return;
        Actor actor = ((Group) root).findActor("view_label");
        if (actor instanceof VectorLabel vl) {
            vl.setText(text != null ? text : "");
        }
    }

    /**
     * 设置悬停信息文字（左下角），Galaxy View 和 System View 共用。
     *
     * @param text 悬停信息文字，空或 null 时清空
     */
    public void setHoverInfoText(String text) {
        if (!(root instanceof Group)) return;
        Actor actor = ((Group) root).findActor("star_info_label");
        if (actor instanceof VectorLabel label) {
            label.setText(text != null ? text : "");
        }
    }

    /**
     * 每帧更新恒星悬停 tooltip（Galaxy View 选择母星系时用）。
     * 内部走 HoverTooltipBinder 状态机：悬停 300ms 显示，鼠标移入 tooltip
     * 停留 3 秒自动钉住为信息窗口喵。
     *
     * @param starEntityId 悬停的恒星实体ID，&lt; 0 表示无悬停
     * @param ds           DailySettlementState 快照（无悬停时传 null）
     * @param screenX      恒星投影的 OpenGL 屏幕 X（左下原点）
     * @param screenY      恒星投影的 OpenGL 屏幕 Y（左下原点）
     */
    public void updateStarTooltipHover(long starEntityId, DailySettlementState ds,
                                       float screenX, float screenY) {
        if (ds != null) {
            lastDailyState = ds;
        }
        if (tooltipBinder == null) return;
        if (starEntityId >= 0 && ds != null) {
            tooltipBinder.updateHover(starEntityId,
                    buildSystemTooltipText(starEntityId, ds), screenX, screenY);
        } else {
            tooltipBinder.updateHover(-1, null, 0, 0);
        }
    }

    /**
     * 钉住回调：以简单文本窗口展示被钉住的恒星系信息喵。
     * TODO Phase 2：升级为正式 EntityInfoPanel（实体详情面板）。
     */
    private void openPinnedStarWindow(long starEntityId) {
        UiWindowManager wm = gui.tryGet(UiWindowManager.class);
        if (wm == null || lastDailyState == null) return;

        String content = buildSystemTooltipText(starEntityId, lastDailyState);
        if (content.isEmpty()) return;

        long systemId = findSystemIdOfStar(starEntityId, lastDailyState);
        String title = systemId > 0 ? "恒星系 #" + systemId : "恒星信息";

        wm.openPinned(PIN_GROUP_STAR_SYSTEM, () -> {
            VectorWindow win = new VectorWindow(sr, FontProvider.createVectorFont(), title);
            VectorLabel label = new VectorLabel(FontProvider.createVectorFont(), content, theme.text);
            label.setPosition(10, 10);
            win.addContent(label);
            win.pack();
            win.updateLayout();
            return win;
        });
    }

    /**
     * Galaxy View 悬停恒星信息更新（左下角坐标文本，调试向）。
     */
    public void updateStarInfo(long hoveredStarId, RealTimeWorldState state) {
        if (hoveredStarId < 0 || state == null) {
            setHoverInfoText("");
            return;
        }

        for (EntitySnapshot snap : state.getEntitySnapshotsView()) {
            if (snap != null && snap.entityId == hoveredStarId && snap.posWorldGU != null) {
                var pos = snap.posWorldGU;
                setHoverInfoText(String.format("(%.0f, %.0f, %.0f)", pos.x(), pos.y(), pos.z()));
                return;
            }
        }
        setHoverInfoText("");
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
        // tooltipBinder 保留实例（本 Screen 为注册单例），其 tooltip 由 stage.clear() 清理，
        // 下次 show() 时 forceHide() + 懒附加自动恢复喵
    }

    // ===== TODO Phase 2 Step B：假数据演示（接真实数据后整块删除） =====

    /** 循环切换演示实体（ClientGame 的 F6 调试键调用），接真实数据后删除喵 */
    public void cycleDemoEntity() {
        demoIndex = (demoIndex + 1) % DEMO_SUPPLIERS.length;
        demoShowCurrent();
    }

    /** 用当前演示实体刷新摘要面板喵 */
    private void demoShowCurrent() {
        if (summaryPanel == null) return;
        demoCurrentVm = DEMO_SUPPLIERS[demoIndex].get();
        summaryPanel.showEntity(demoCurrentVm);
    }

    /** 摘要区指令回调：「详细」开中央窗口，其余指令仅打日志演示喵 */
    private void onSummaryAction(String actionId) {
        if (EntitySummaryPanel.ACTION_OPEN_DETAILS.equals(actionId)) {
            UiWindowManager wm = gui.tryGet(UiWindowManager.class);
            if (wm != null && demoCurrentVm != null) {
                EntityInfoViewModel vm = demoCurrentVm;
                wm.openPinned("demo-entity", () -> new EntityInfoPanel(
                        sr, FontProvider.createVectorFont(), vm, theme));
            }
        } else {
            com.badlogic.gdx.Gdx.app.log("InGameHudScreen", "演示指令点击: " + actionId);
        }
    }

    // ===== private helpers =====

    /** 在快照中查找恒星所属的 systemId（找不到返回 0）喵 */
    private static long findSystemIdOfStar(long starEntityId, DailySettlementState ds) {
        if (ds.publicEntityBaselinesBySectorKey == null) return 0;
        for (var entry : ds.publicEntityBaselinesBySectorKey.entrySet()) {
            for (var snap : entry.getValue()) {
                if (snap != null && snap.entityId == starEntityId
                        && snap.details instanceof EntitySnapshot.StarDetails) {
                    return snap.systemId;
                }
            }
        }
        return 0;
    }

    /** 构建恒星系的完整信息文本。 */
    private static String buildSystemTooltipText(long starEntityId, DailySettlementState ds) {
        if (ds.publicEntityBaselinesBySectorKey == null) return "";

        // 遍历所有 sector 找到该恒星的 StarDetails
        EntitySnapshot.StarDetails starDetail = null;
        long systemId = 0;
        for (var entry : ds.publicEntityBaselinesBySectorKey.entrySet()) {
            for (var snap : entry.getValue()) {
                if (snap != null && snap.entityId == starEntityId
                        && snap.details instanceof EntitySnapshot.StarDetails sd) {
                    starDetail = sd;
                    systemId = snap.systemId;
                    break;
                }
            }
            if (starDetail != null) break;
        }
        if (starDetail == null) return "";

        // 统计该星系的行星数量与宜居行星数量
        int planetCount = 0;
        int habitableCount = 0;
        if (systemId > 0) {
            var baselines = ds.publicEntityBaselinesBySectorKey.get(String.valueOf(systemId));
            if (baselines != null) {
                for (var snap : baselines) {
                    if (snap != null && snap.details instanceof EntitySnapshot.PlanetDetails pd) {
                        if (snap.entityType == staraxis.game.entity.EntityType.PLANET) {
                            planetCount++;
                            if (pd.planetTypeId != null
                                    && staraxis.game.astro.PlanetBody.HABITABLE_PLANET_TYPE_IDS
                                            .contains(pd.planetTypeId)) {
                                habitableCount++;
                            }
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("恒星系 #%d\n", systemId));
        sb.append(String.format("光谱 %s  %dK  半径 %.1fsol",
                starDetail.starTypeId != null ? starDetail.starTypeId : "?",
                starDetail.temperatureK,
                starDetail.radiusGU / 40.0)); // 40 GU ≈ 1 Solar radius
        if (planetCount > 0) {
            sb.append(String.format("\n行星 x%d", planetCount));
            if (habitableCount > 0) {
                sb.append(String.format("  (宜居 x%d)", habitableCount));
            }
        }
        return sb.toString();
    }
}
