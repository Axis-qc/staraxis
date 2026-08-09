package staraxis.ui.screens;

import java.util.ArrayDeque;
import java.util.Deque;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Disposable;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.command.ColonizePlanetCommand;
import staraxis.game.command.CommandResult;
import staraxis.game.entity.EntityType;
import staraxis.game.ship.ShipDesign;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.ui.FontProvider;
import staraxis.ui.Gui;
import staraxis.ui.SelectionService;
import staraxis.ui.UiPointerService;
import staraxis.ui.UiWindowManager;
import staraxis.ui.effects.EffectRegistry;
import staraxis.ui.json.ComponentNode;
import staraxis.ui.json.UiFactory;
import staraxis.ui.json.UiParser;
import staraxis.ui.notifications.CommandErrorMessages;
import staraxis.ui.panels.EntityInfoAssembler;
import staraxis.ui.panels.EntityInfoPanel;
import staraxis.ui.panels.EntityInfoViewModel;
import staraxis.ui.panels.EntitySummaryPanel;
import staraxis.ui.panels.PlanetInfoAssembler;
import staraxis.ui.panels.PlanetInfoPanel;
import staraxis.ui.panels.PlanetInfoViewModel;
import staraxis.ui.theme.UiTheme;
import staraxis.ui.widgets.HoverTooltipBinder;
import staraxis.ui.widgets.ToastWidget;
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

    /** 全局选中服务（选中变更时刷新摘要面板和详情窗口）喵 */
    private SelectionService selectionService;

    /** 统一 UI 命中守卫服务：摘要面板自注册为交互区域喵 */
    private UiPointerService pointerService;

    /** 摘要面板当前展示的实体 ID（「详细」按钮的数据源，不依赖实时选中值）喵 */
    private long summaryEntityId = -1;

    /** 最近选中的舰船实体 ID（选中行星时不清除，殖民按钮依赖此值判断可用性）喵 */
    private long lastSelectedShipId = -1;

    /** 移动模式请求回调：由 ClientGame 接到 ShipMoveController，ui 不依赖 client 模块喵 */
    private Runnable moveModeRequester;

    /** 命令结果轮询 actor（随 stage.act 每帧驱动，轮询 CommandBus 结果队列并展示通知）喵 */
    private CommandFeedbackPoller feedbackPoller;

    /** 当前正在显示的通知（null 表示空槽，可展示下一条）喵 */
    private ToastWidget activeToast;

    /** 详情窗口单例 id（选中跟随模式），钉住后变为多例窗口喵 */
    private static final String DETAILS_SINGLETON_ID = "entity-details";

    /** 行星信息窗口单例 id（左键点击行星打开的非模态独立窗口）喵 */
    private static final String PLANET_INFO_WINDOW_ID = "planet-info";

    /** 详情窗口多例分组 id（钉住后归入此组，上限由 UiWindowManager 控制）喵 */
    private static final String PIN_GROUP_ENTITY_DETAILS = "entity-details-pinned";

    /** 命令结果通知显示时长（秒），3 秒后自动消失喵 */
    private static final float NOTIFICATION_DURATION_SECONDS = 3f;

    /** 通知距屏幕边缘的边距（px）喵 */
    private static final float TOAST_MARGIN = 12f;

    /** 命令成功通知文案喵 */
    private static final String NOTIFICATION_SUCCESS_TEXT = "命令已执行";

    /** 命令失败通知前缀喵 */
    private static final String NOTIFICATION_FAILURE_PREFIX = "命令失败：";

    public InGameHudScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    /**
     * 注册移动模式请求回调（摘要面板「移动」指令点击时调用）。
     * 由 ClientGame 接线到 ShipMoveController，ui 模块不依赖 client 模块喵。
     *
     * @param requester 进入移动模式的回调，可为 null（移动按钮点击仅记日志）
     */
    public void setMoveModeRequester(Runnable requester) {
        this.moveModeRequester = requester;
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

        // 摘要面板自注册到统一守卫：点击面板区域只触发 UI，不触发 3D 选中逻辑喵
        if (pointerService == null) {
            pointerService = gui.tryGet(UiPointerService.class);
        }
        if (pointerService != null) {
            pointerService.register(this::isPointerOverSummary);
        }

        // 懒创建命令结果轮询器：随 stage.act 每帧轮询 CommandBus 结果队列，
        // 有新结果时展示通知（成功/失败），成功后刷新选中实体详情喵
        if (feedbackPoller == null && sr != null && theme != null) {
            feedbackPoller = new CommandFeedbackPoller();
        }
        if (feedbackPoller != null && feedbackPoller.getStage() == null) {
            stage.addActor(feedbackPoller);
        }

        // 连接全局选中服务：选中变更时刷新摘要面板和详情窗口喵
        if (selectionService == null) {
            selectionService = gui.tryGet(SelectionService.class);
        }
        if (selectionService != null) {
            selectionService.addListener(this::onSelectionChanged);
            // 初次显示时，若已有选中实体则立即刷新喵
            if (selectionService.hasSelection()) {
                onSelectionChanged(selectionService.getSelectedEntityId(),
                        selectionService.getSelectedEntityType());
            }
        }

        UiParser parser = gui.get(UiParser.class);
        UiFactory factory = gui.get(UiFactory.class);
        if (parser == null || factory == null) return;

        ComponentNode node = parser.parseInternal(UI_PATH);
        if (node == null) return;

        root = factory.create(node);
        if (root != null) {
            // HUD 根容器只做布局，禁用触摸：全屏容器若 touchable enabled 会拦截
            // 下层按钮点击（stage.hit 只取最上层命中 actor）喵
            root.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
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
        // 轮询 actor 由 stage.clear() 切屏清理；清空引用避免跨屏残留旧状态喵
        if (feedbackPoller != null) {
            feedbackPoller.remove();
            feedbackPoller = null;
        }
        activeToast = null;
        // tooltipBinder 保留实例（本 Screen 为注册单例），其 tooltip 由 stage.clear() 清理，
        // 下次 show() 时 forceHide() + 懒附加自动恢复喵
    }

    // ===== 选中变更处理 =====

    /** 摘要区指令回调：「详细」开中央窗口，「移动/殖民」提交命令，其余指令仅打日志喵 */
    private void onSummaryAction(String actionId) {
        if (EntitySummaryPanel.ACTION_OPEN_DETAILS.equals(actionId)) {
            UiWindowManager wm = gui.tryGet(UiWindowManager.class);
            if (wm == null) return;
            // 以摘要面板当前展示的实体为准（点击按钮帧 3D 轮询可能已改选中态，不能用实时选中值）喵
            long entityId = summaryEntityId;
            if (entityId < 0) return;

            StarAxisGameRuntime rt = gui.getRuntime();
            EntityInfoViewModel vm = buildViewModel(entityId, rt);
            if (vm == null) return;

            // 模态单例窗口：从点击位置弹出 + 点击面板外关闭，重复打开只置前喵
            // 锚点转 stage 左下原点（Gdx.input 为左上原点，Y 需翻转）喵
            wm.openSingletonModal(DETAILS_SINGLETON_ID,
                    com.badlogic.gdx.Gdx.input.getX(),
                    com.badlogic.gdx.Gdx.graphics.getHeight() - com.badlogic.gdx.Gdx.input.getY(),
                    () -> {
                        EntityInfoPanel panel = new EntityInfoPanel(
                                sr, FontProvider.createVectorFont(), vm, theme);
                        // 钉住按钮：单例转多例，脱离选中跟随喵
                        panel.setPinButtonVisible(true);
                        panel.setOnPin(() -> wm.pinSingleton(DETAILS_SINGLETON_ID, PIN_GROUP_ENTITY_DETAILS));
                        return panel;
                    });
            return;
        }
        if (EntityInfoAssembler.ACTION_MOVE.equals(actionId)) {
            // 移动指令：经回调进入移动模式（ShipMoveController 在 client 模块，通过回调解耦）喵
            if (moveModeRequester != null) {
                moveModeRequester.run();
            } else {
                com.badlogic.gdx.Gdx.app.log("InGameHudScreen", "移动指令: 未接线 moveModeRequester");
            }
            return;
        }
        if (EntityInfoAssembler.ACTION_COLONIZE.equals(actionId)) {
            submitColonizeCommand();
            return;
        }
        com.badlogic.gdx.Gdx.app.log("InGameHudScreen", "指令点击: " + actionId);
    }

    /**
     * 殖民指令提交：使用最近选中的舰船（选中行星时不清除舰船记忆），
     * 从摘要面板取行星，校验殖民舰后经命令总线提交 {@link ColonizePlanetCommand} 喵。
     * 未选中舰船、选中非殖民舰或舰船无归属时不提交。
     */
    private void submitColonizeCommand() {
        long shipId = lastSelectedShipId;
        if (shipId < 0) return;
        long planetId = summaryEntityId;
        if (planetId < 0) return;

        StarAxisGameRuntime rt = gui.getRuntime();
        if (rt == null) return;
        RealTimeWorldState rtState = rt.getRealTimeWorldStateReadonly();
        EntitySnapshot shipSnap = findShipSnapshot(rtState, shipId);
        if (shipSnap == null || !isColonyShip(shipSnap)) return;
        String ownerNationId = shipSnap.ownerNationId;
        if (ownerNationId == null) return;

        rt.submitCommand(new ColonizePlanetCommand(shipId, planetId, ownerNationId));
    }

    /** 在实时快照中按 ID 查找舰船快照（找不到返回 null）喵 */
    private static EntitySnapshot findShipSnapshot(RealTimeWorldState rtState, long shipId) {
        if (rtState == null) return null;
        for (var list : rtState.getEntitySnapshotsBySystemView().values()) {
            for (EntitySnapshot snap : list) {
                if (snap != null && snap.entityId == shipId
                        && snap.details instanceof EntitySnapshot.ShipDetails) {
                    return snap;
                }
            }
        }
        return null;
    }

    /** 舰船是否为殖民舰（customFlags 含 {@link ShipDesign#FLAG_COLONY} 标记）喵 */
    private static boolean isColonyShip(EntitySnapshot snap) {
        if (snap == null || !(snap.details instanceof EntitySnapshot.ShipDetails sd)) return false;
        return sd.customFlags != null && sd.customFlags.contains(ShipDesign.FLAG_COLONY);
    }

    /**
     * 打开/聚焦行星信息窗口（非模态独立窗口）喵。
     *
     * 由 System View 左键点击行星时调用：
     * - 未打开：以 {@link UiWindowManager#openSingleton} 在屏幕中央创建行星窗口
     * - 已打开：置前并切换到另一颗行星
     * - 无论首次创建还是已打开切换，都用最新 view model 重建内容（快照每 tick 变化，
     *   切换行星时完整替换标题/概览/城市/状态/列表，不残留上一颗行星数据）
     * - 快照缺失/行星不存在时 {@link PlanetInfoAssembler} 返回带 missing 标记的空态 VM，
     *   窗口稳定显示空态，不会误开空窗口
     *
     * @param planetId 行星实体 ID
     */
    public void openPlanetInfo(long planetId) {
        if (sr == null || theme == null || planetId < 0) return;
        StarAxisGameRuntime rt = gui.getRuntime();
        PlanetInfoViewModel vm = buildPlanetViewModel(planetId, rt);
        if (vm == null) return;

        UiWindowManager wm = gui.tryGet(UiWindowManager.class);
        if (wm == null) return;

        // 非模态单例：首次创建构造函数完成一次 rebuild 保证初始尺寸与内容，
        // 随后统一调用 rebuild 刷新为最新 view model（禁止只在 wasOpen 时刷新）喵
        VectorWindow win = wm.openSingleton(PLANET_INFO_WINDOW_ID, () ->
                new PlanetInfoPanel(sr, FontProvider.createVectorFont(), vm, theme));
        if (win instanceof PlanetInfoPanel panel) {
            panel.rebuild(vm);
        }
    }

    /** 选中变更回调：刷新摘要面板，若详情窗口为单例模式则同步刷新喵 */
    private void onSelectionChanged(long entityId, EntityType entityType) {
        if (summaryPanel == null) return;

        if (entityId < 0 || entityType == null) {
            summaryPanel.clearEntity();
            summaryEntityId = -1;
            // 单例详情窗口跟随取消选中：清空内容（保持窗口打开，等待下次选中）喵
            UiWindowManager wm = gui.tryGet(UiWindowManager.class);
            if (wm != null) {
                VectorWindow win = wm.getSingleton(DETAILS_SINGLETON_ID);
                if (win instanceof EntityInfoPanel panel) {
                    panel.rebuild(new EntityInfoViewModel().title("未选中实体"));
                }
            }
            return;
        }

        StarAxisGameRuntime rt = gui.getRuntime();
        EntityInfoViewModel vm = buildViewModel(entityId, rt);
        if (vm != null) {
            summaryPanel.showEntity(vm);
            summaryEntityId = entityId;
        } else {
            summaryPanel.clearEntity();
            summaryEntityId = -1;
        }

        // 选中舰船时记住舰船 ID（选中行星时不清除，殖民按钮依赖此值）喵
        if (entityType == EntityType.SHIP && entityId > 0) {
            lastSelectedShipId = entityId;
        }

        // 若详情窗口正在以单例模式显示，同步刷新内容（钉住转多例后不再跟随）喵
        if (vm != null) {
            UiWindowManager wm = gui.tryGet(UiWindowManager.class);
            if (wm != null) {
                VectorWindow win = wm.getSingleton(DETAILS_SINGLETON_ID);
                if (win instanceof EntityInfoPanel panel) {
                    panel.rebuild(vm);
                }
            }
        }
    }

    // ===== 命令结果反馈（G1.2） =====

    /**
     * 命令结果轮询 actor（内部类，随 stage.act 每帧驱动）喵。
     *
     * 每帧轮询 CommandBus 结果队列：
     * - 有新结果时逐条展示通知（成功绿色 / 失败红色，3 秒自动消失），避免批量刷屏喵
     * - 成功后触发选中实体详情刷新（复用 buildViewModel + summaryPanel.showEntity）喵
     */
    private final class CommandFeedbackPoller extends Actor {

        /** 待展示的命令结果队列（一次只展示一条，空槽后再取下一条）喵 */
        private final Deque<CommandResult> pendingResults = new ArrayDeque<>();

        {
            // 轮询 actor 无视觉、零尺寸，禁止参与触摸命中喵
            setTouchable(Touchable.disabled);
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            // 当前通知已消失（ToastWidget 倒计时结束自动移除），释放空槽喵
            if (activeToast != null && activeToast.getStage() == null) {
                activeToast = null;
            }

            StarAxisGameRuntime rt = gui.getRuntime();
            if (rt == null) {
                return;
            }

            for (CommandResult result : rt.pollCommandResults()) {
                pendingResults.addLast(result);
            }

            if (activeToast == null && !pendingResults.isEmpty()) {
                showCommandResult(pendingResults.removeFirst());
            }
        }
    }

    /**
     * 展示命令执行结果通知（成功绿色 / 失败红色，3 秒自动消失），
     * 成功后触发选中实体详情刷新喵。
     *
     * @param result 命令执行结果
     */
    private void showCommandResult(CommandResult result) {
        if (sr == null || theme == null) {
            return;
        }

        boolean success = result.success();
        String message = success
                ? NOTIFICATION_SUCCESS_TEXT
                : NOTIFICATION_FAILURE_PREFIX + CommandErrorMessages.zh(result.failureCode());
        ToastWidget.Type type = success ? ToastWidget.Type.SUCCESS : ToastWidget.Type.FAILURE;

        ToastWidget toast = new ToastWidget(sr, FontProvider.createVectorFont(), theme,
                type, message, NOTIFICATION_DURATION_SECONDS);
        // 屏幕右上角展示，距边缘留边距喵
        toast.setPosition(stage.getWidth() - toast.getWidth() - TOAST_MARGIN,
                stage.getHeight() - toast.getHeight() - TOAST_MARGIN);
        activeToast = toast;
        stage.addActor(toast);

        if (success) {
            refreshSelectedEntityDetails();
        }
    }

    /**
     * 命令成功执行后刷新选中实体详情（摘要面板 + 单例详情窗口）喵。
     * 复用 buildViewModel + summaryPanel.showEntity，与选中变更刷新口径一致喵。
     */
    private void refreshSelectedEntityDetails() {
        if (summaryPanel == null || summaryEntityId < 0) {
            return;
        }
        StarAxisGameRuntime rt = gui.getRuntime();
        EntityInfoViewModel vm = buildViewModel(summaryEntityId, rt);
        if (vm == null) {
            return;
        }
        summaryPanel.showEntity(vm);

        // 若详情窗口正在以单例模式显示，同步刷新内容喵
        UiWindowManager wm = gui.tryGet(UiWindowManager.class);
        if (wm != null) {
            VectorWindow win = wm.getSingleton(DETAILS_SINGLETON_ID);
            if (win instanceof EntityInfoPanel panel) {
                panel.rebuild(vm);
            }
        }
    }

    /** 从快照构建实体视图模型（传入当前选中舰船 ID 用于殖民按钮可用性判断）喵 */
    private EntityInfoViewModel buildViewModel(long entityId, StarAxisGameRuntime rt) {
        if (rt == null) return null;
        RealTimeWorldState rtState = rt.getRealTimeWorldStateReadonly();
        DailySettlementState ds = rt.getDailySettlementStateBufferForReadonly().getActive();
        return EntityInfoAssembler.assemble(entityId, rtState, ds, getSelectedShipId());
    }

    /** 当前选中的舰船实体 ID：优先实时选中舰船，回退到最近选中的舰船（选中行星时不清除）喵 */
    private long getSelectedShipId() {
        if (selectionService != null
                && selectionService.getSelectedEntityType() == EntityType.SHIP) {
            return selectionService.getSelectedEntityId();
        }
        return lastSelectedShipId;
    }

    /** 从快照构建行星视图模型（行星信息窗口专用，组合行星基线 + 地表区域/城市）喵 */
    private PlanetInfoViewModel buildPlanetViewModel(long planetId, StarAxisGameRuntime rt) {
        if (rt == null) return null;
        RealTimeWorldState rtState = rt.getRealTimeWorldStateReadonly();
        DailySettlementState ds = rt.getDailySettlementStateBufferForReadonly().getActive();
        return PlanetInfoAssembler.assemble(planetId, rtState, ds);
    }

    /**
     * 摘要面板的区域命中判定（注册到 UiPointerService 的守卫入口）喵。
     *
     * 面板隐藏、已从舞台移除（stage.clear() 切屏）或坐标未命中时返回 false，
     * 守卫自动失效，不会误拦截 3D 交互。
     * 不用 stage.hit（不检查 visible，全屏 HUD 容器会误判）。
     *
     * @param screenX 屏幕 X（左下原点）
     * @param screenY 屏幕 Y（左下原点）
     * @return true 表示该坐标落在摘要面板交互区
     */
    private boolean isPointerOverSummary(float screenX, float screenY) {
        if (summaryPanel == null || !summaryPanel.isVisible() || summaryPanel.getStage() == null) {
            return false;
        }
        float x = summaryPanel.getX();
        float y = summaryPanel.getY();
        return screenX >= x && screenX <= x + summaryPanel.getWidth()
                && screenY >= y && screenY <= y + summaryPanel.getHeight();
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
