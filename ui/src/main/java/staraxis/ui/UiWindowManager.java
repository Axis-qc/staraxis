package staraxis.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.utils.IdentityMap;
import staraxis.ui.widgets.VectorWindow;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * UiWindowManager（全局窗口管理器）喵。
 *
 * 统一管理基于 {@link VectorWindow} 的非模态信息窗口（实体详情、舰队列表、通知中心等）：
 * - 单例窗口：按 id 注册，重复打开只置前不新建（舰队列表、通知中心等）
 * - 多例钉住窗口：按 groupId 分组，同组上限 {@link #MAX_PINNED_PER_GROUP} 个，
 *   超出时关闭最旧窗口（FIFO），用于实体详情对比
 * - 新窗口统一在屏幕中央出现，多开时按 {@link #CASCADE_STEP} 级联偏移
 * - Z-order：窗口被点击（任意位置）自动置前
 * - ESC 栈：{@link #hasOpenWindow()} / {@link #closeTopMost()}，
 *   由 ClientGame 的 ESC 处理调用，有窗口先关窗口，无窗口才弹暂停菜单
 *
 * 管理边界（2026-07-23 决策）：DevConsole、PauseMenu、模态弹窗（如
 * SelectHomeConfirmDialog）不纳入管理，维持各自独立逻辑。
 */
public class UiWindowManager {

    /** 同组多例窗口上限，超出时关闭最旧窗口喵 */
    private static final int MAX_PINNED_PER_GROUP = 3;
    /** 级联偏移步长（px），每新开一个窗口在中央基础上偏移一档喵 */
    private static final float CASCADE_STEP = 30f;
    /** 级联档位数，超过后回到中央重新开始，避免无限偏移出屏喵 */
    private static final int CASCADE_STEPS = 6;

    /** 模态窗口淡入时长（秒）喵 */
    private static final float MODAL_FADE_IN_SECONDS = 0.15f;

    private final Stage stage;

    /** 统一 UI 命中守卫服务：本管理器注册窗口 bounds 判定，3D 层零 UI 知识喵 */
    private final UiPointerService pointerService;

    /** 打开中的单例窗口：id → 窗口喵 */
    private final Map<String, VectorWindow> singletons = new LinkedHashMap<>();
    /** 打开中的多例窗口：groupId → FIFO 队列（队首最旧）喵 */
    private final Map<String, Deque<VectorWindow>> pinnedByGroup = new HashMap<>();
    /** 反查表：窗口 → 单例 id（多例窗口无此条目），关闭时清理注册表用喵 */
    private final IdentityMap<VectorWindow, String> singletonIdByWindow = new IdentityMap<>();
    /** 反查表：窗口 → 多例 groupId（单例窗口无此条目）喵 */
    private final IdentityMap<VectorWindow, String> groupIdByWindow = new IdentityMap<>();
    /** 模态窗口标记（无遮罩视觉，仅用于窗口外点击关闭与 3D 拦截判定）喵 */
    private final IdentityMap<VectorWindow, Boolean> modalWindows = new IdentityMap<>();
    /** 级联计数器喵 */
    private int cascadeCounter = 0;

    public UiWindowManager(Stage stage, UiPointerService pointerService) {
        this.stage = stage;
        this.pointerService = pointerService;
        // 窗口区域自注册到统一守卫：模态全屏拦截 + 打开中的窗口 bounds 命中判定喵
        if (pointerService != null) {
            pointerService.register(this::isPointBlocking3D);
        }
    }

    /**
     * 打开单例窗口：不存在则用工厂创建并加入舞台；已存在则置前返回原窗口喵。
     *
     * @param id      单例窗口 id（调用方负责命名空间，避免与多例 groupId 撞名）
     * @param factory 窗口工厂，仅在窗口未打开时调用
     * @return 打开中的窗口
     */
    public VectorWindow openSingleton(String id, Supplier<VectorWindow> factory) {
        VectorWindow win = singletons.get(id);
        if (win != null) {
            if (win.getStage() != null) {
                win.toFront();
                return win;
            }
            // 窗口已被外部移除（如 stage.clear() 切屏），清理注册表后重建喵
            dismiss(win);
        }
        win = factory.get();
        if (win == null) return null;
        adopt(win);
        singletons.put(id, win);
        singletonIdByWindow.put(win, id);
        return win;
    }

    /**
     * 打开模态单例窗口：从锚点位置弹出 + 点击窗口外关闭（无遮罩视觉）喵。
     *
     * 与 {@link #openSingleton} 的区别：
     * - 首次打开时窗口以锚点（屏幕坐标，左下原点）为中心定位，替代中央级联定位
     * - 模态语义：点击窗口外区域关闭窗口（由 ClientGame 左键轮询判定），
     *   模态期间 3D 视图交互全屏拦截（{@link #isPointBlocking3D}）
     * - 窗口带淡入动画
     *
     * @param id      单例窗口 id
     * @param anchorX 锚点屏幕 X（左下原点）
     * @param anchorY 锚点屏幕 Y（左下原点）
     * @param factory 窗口工厂，仅在窗口未打开时调用
     * @return 打开中的窗口
     */
    public VectorWindow openSingletonModal(String id, float anchorX, float anchorY,
                                            Supplier<VectorWindow> factory) {
        VectorWindow win = singletons.get(id);
        if (win != null) {
            if (win.getStage() != null) {
                win.toFront();
                return win;
            }
            dismiss(win);
        }
        win = factory.get();
        if (win == null) return null;
        adoptAt(win, anchorX, anchorY);
        singletons.put(id, win);
        singletonIdByWindow.put(win, id);
        markModal(win);
        return win;
    }

    /**
     * 打开多例钉住窗口：同组上限 {@link #MAX_PINNED_PER_GROUP} 个，超出关闭最旧喵。
     *
     * @param groupId 多例窗口分组 id（如 "star-system"、"planet"）
     * @param factory 窗口工厂，每次调用都会执行
     * @return 新打开的窗口
     */
    public VectorWindow openPinned(String groupId, Supplier<VectorWindow> factory) {
        Deque<VectorWindow> queue = pinnedByGroup.computeIfAbsent(groupId, k -> new ArrayDeque<>());
        // 先清理队列中已被外部移除的死引用，再执行 FIFO 上限检查喵
        queue.removeIf(win -> win.getStage() == null);
        while (queue.size() >= MAX_PINNED_PER_GROUP) {
            VectorWindow oldest = queue.pollFirst();
            if (oldest != null) {
                dismiss(oldest);
            }
        }
        VectorWindow win = factory.get();
        if (win == null) return null;
        adopt(win);
        queue.addLast(win);
        groupIdByWindow.put(win, groupId);
        return win;
    }

    /** 判断单例窗口是否打开中喵 */
    public boolean isOpen(String id) {
        VectorWindow win = singletons.get(id);
        return win != null && win.getStage() != null;
    }

    /**
     * 获取当前打开的单例窗口（未打开或已钉住转为多例时返回 null）喵。
     * 用于单例模式下选中变更时的内容刷新。
     */
    public VectorWindow getSingleton(String id) {
        VectorWindow win = singletons.get(id);
        return (win != null && win.getStage() != null) ? win : null;
    }

    /**
     * 将单例窗口钉住转为多例窗口：脱离单例注册表，转入多例分组管理喵。
     * 窗口实例保留在舞台上不重建，位置不重置，仅切换管理模式。
     * 转组后不再随选中刷新（由调用方按 groupId 语义决定）。
     *
     * @param id      原单例窗口 id
     * @param groupId 目标多例分组 id
     * @return 转组后的窗口；单例未打开时返回 null
     */
    public VectorWindow pinSingleton(String id, String groupId) {
        VectorWindow win = singletons.get(id);
        if (win == null || win.getStage() == null) return null;

        // 1. 脱离单例注册表喵
        singletons.remove(id);
        singletonIdByWindow.remove(win);
        // 模态标记一并移除（钉住后不再拦截 3D）喵
        unmarkModal(win);

        // 2. 执行多例分组 FIFO 上限检查（与 openPinned 一致）喵
        Deque<VectorWindow> queue = pinnedByGroup.computeIfAbsent(groupId, k -> new ArrayDeque<>());
        queue.removeIf(w -> w.getStage() == null);
        while (queue.size() >= MAX_PINNED_PER_GROUP) {
            VectorWindow oldest = queue.pollFirst();
            if (oldest != null) {
                dismiss(oldest);
            }
        }

        // 3. 转入多例分组喵
        queue.addLast(win);
        groupIdByWindow.put(win, groupId);
        win.toFront();
        return win;
    }

    /** 关闭指定 id 的单例窗口（未打开时无操作）喵 */
    public void close(String id) {
        VectorWindow win = singletons.get(id);
        if (win != null) {
            dismiss(win);
        }
    }

    /** 切换单例窗口：开着则关闭，关着则打开喵 */
    public void toggleSingleton(String id, Supplier<VectorWindow> factory) {
        if (isOpen(id)) {
            close(id);
        } else {
            openSingleton(id, factory);
        }
    }

    /** 是否有任何被管理的窗口打开中（ESC 栈判断用）喵 */
    public boolean hasOpenWindow() {
        return topMostManagedWindow() != null;
    }

    /**
     * 关闭最上层的被管理窗口（ESC 栈）喵。
     *
     * @return true 表示确实关闭了一个窗口（调用方应消费本次 ESC），false 表示无窗口可关
     */
    public boolean closeTopMost() {
        VectorWindow win = topMostManagedWindow();
        if (win == null) return false;
        dismiss(win);
        return true;
    }

    /** 关闭所有被管理的窗口喵 */
    public void closeAll() {
        for (VectorWindow win : singletons.values().toArray(new VectorWindow[0])) {
            dismiss(win);
        }
        for (Deque<VectorWindow> queue : pinnedByGroup.values()) {
            for (VectorWindow win : queue.toArray(new VectorWindow[0])) {
                dismiss(win);
            }
        }
    }

    // ===== private helpers =====

    /**
     * 接管窗口：挂接关闭按钮、点击置前监听，以锚点为中心定位（边界钳制）并加入舞台喵。
     * 与 {@link #adopt} 的区别：位置优先取锚点，不参与级联计数。
     */
    private void adoptAt(VectorWindow win, float anchorX, float anchorY) {
        win.setCloseButtonVisible(true);
        win.setOnClose(() -> dismiss(win));
        win.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                win.toFront();
                return false;
            }
        });
        float x = anchorX - win.getWidth() / 2f;
        float y = anchorY - win.getHeight() / 2f;
        x = Math.max(0f, Math.min(x, stage.getWidth() - win.getWidth()));
        y = Math.max(0f, Math.min(y, stage.getHeight() - win.getHeight()));
        win.setPosition(x, y);
        stage.addActor(win);
        win.toFront();
    }

    /**
     * 标记模态窗口（无遮罩视觉，仅登记模态状态）喵。
     *
     * 模态期间的语义：
     * - 点击窗口外区域关闭窗口（由 ClientGame 左键轮询调 {@link #closeModalIfClickedOutside} 判定）
     * - 3D 视图交互被拦截（{@link #isPointBlocking3D}）
     * 窗口带淡入动画。
     */
    private void markModal(VectorWindow win) {
        modalWindows.put(win, Boolean.TRUE);
        win.toFront();
        // 窗口淡入：初始透明渐显喵
        win.getColor().a = 0f;
        win.addAction(Actions.fadeIn(MODAL_FADE_IN_SECONDS));
    }

    /**
     * 模态窗口打开时，点击窗口外区域关闭模态窗口喵。
     *
     * 由 ClientGame 的左键轮询调用（3D 逻辑执行前）：
     * - 无模态窗口：返回 false，调用方正常处理 3D 逻辑
     * - 点击在模态窗口内：返回 true，调用方跳过 3D 逻辑（窗口消费该点击）
     * - 点击在模态窗口外：关闭模态窗口并返回 true，调用方跳过 3D 逻辑
     *
     * @param screenX 屏幕 X（左下原点）
     * @param screenY 屏幕 Y（左下原点）
     * @return true 表示本次点击已由模态窗口消费（窗口内点击或已关闭窗口），调用方不应继续处理 3D 逻辑
     */
    public boolean closeModalIfClickedOutside(float screenX, float screenY) {
        if (modalWindows.isEmpty()) return false;
        VectorWindow modal = modalWindows.keys().next();
        if (isPointInBounds(modal, screenX, screenY)) {
            return true;
        }
        dismiss(modal);
        return true;
    }

    /** 移除窗口的模态标记（若存在）喵 */
    private void unmarkModal(VectorWindow win) {
        modalWindows.remove(win);
    }

    /**
     * 接管窗口：挂接关闭按钮、点击置前监听，定位到屏幕中央（带级联偏移）并加入舞台喵。
     */
    private void adopt(VectorWindow win) {
        win.setCloseButtonVisible(true);
        win.setOnClose(() -> dismiss(win));
        // 点击窗口任意位置置前；return false 不消费事件，保证子组件正常接收交互喵
        win.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                win.toFront();
                return false;
            }
        });
        placeAtCenter(win);
        stage.addActor(win);
        win.toFront();
    }

    /**
     * 判断屏幕坐标是否需要拦截 3D 视图交互喵。
     *
     * 判定规则：
     * - 有任意模态窗口打开时全屏拦截（无遮罩视觉）——模态期间禁止操作 3D 视图
     * - 非模态状态：命中任一打开中窗口的 bounds 时拦截
     *
     * 注意：不使用 stage.hit（不检查 visible，全屏容器会误判），
     * 由窗口注册表精确判定。经 UiPointerService 注册为统一守卫的判定入口喵。
     *
     * @param screenX 屏幕 X（左下原点，与 stage 坐标一致）
     * @param screenY 屏幕 Y（左下原点，与 stage 坐标一致）
     * @return true 表示该坐标应拦截 3D 交互
     */
    public boolean isPointBlocking3D(float screenX, float screenY) {
        // 模态窗口打开时全屏拦截喵
        if (!modalWindows.isEmpty()) {
            return true;
        }
        // 非模态：遍历所有注册窗口检查 bounds 命中喵
        for (VectorWindow win : singletonIdByWindow.keys()) {
            if (win.getStage() != null && isPointInBounds(win, screenX, screenY)) {
                return true;
            }
        }
        for (VectorWindow win : groupIdByWindow.keys()) {
            if (win.getStage() != null && isPointInBounds(win, screenX, screenY)) {
                return true;
            }
        }
        return false;
    }

    /** 判断屏幕坐标是否落在窗口 bounds 内（stage 坐标，左下原点）喵 */
    private static boolean isPointInBounds(VectorWindow win, float x, float y) {
        float wx = win.getX();
        float wy = win.getY();
        return x >= wx && x <= wx + win.getWidth()
                && y >= wy && y <= wy + win.getHeight();
    }

    /** 关闭并从注册表移除窗口喵 */
    private void dismiss(VectorWindow win) {
        unmarkModal(win);
        String singletonId = singletonIdByWindow.remove(win);
        if (singletonId != null) {
            singletons.remove(singletonId);
        }
        String groupId = groupIdByWindow.remove(win);
        if (groupId != null) {
            Deque<VectorWindow> queue = pinnedByGroup.get(groupId);
            if (queue != null) {
                queue.remove(win);
            }
        }
        win.remove();
    }

    /**
     * 从舞台顶层向下查找最上层的被管理窗口。
     * 基于舞台实际 Actor 顺序反查，天然免疫 stage.clear() 留下的死引用喵。
     */
    private VectorWindow topMostManagedWindow() {
        var children = stage.getRoot().getChildren();
        for (int i = children.size - 1; i >= 0; i--) {
            Actor a = children.get(i);
            if (a instanceof VectorWindow win
                    && (singletonIdByWindow.containsKey(win) || groupIdByWindow.containsKey(win))) {
                return win;
            }
        }
        return null;
    }

    /** 将窗口定位到屏幕中央，并按级联计数器偏移（边界钳制防止出屏）喵 */
    private void placeAtCenter(VectorWindow win) {
        float offset = (cascadeCounter % CASCADE_STEPS) * CASCADE_STEP;
        cascadeCounter++;
        float x = (stage.getWidth() - win.getWidth()) / 2f + offset;
        float y = (stage.getHeight() - win.getHeight()) / 2f + offset;
        x = Math.max(0f, Math.min(x, stage.getWidth() - win.getWidth()));
        y = Math.max(0f, Math.min(y, stage.getHeight() - win.getHeight()));
        win.setPosition(x, y);
    }
}
