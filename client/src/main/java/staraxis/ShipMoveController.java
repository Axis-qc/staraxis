package staraxis;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.command.MoveShipCommand;
import staraxis.game.entity.EntityType;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.state.snapshot.EntitySnapshot.ShipDetails;
import staraxis.render.WorldCamera;
import staraxis.render.system.SystemViewRenderer;
import staraxis.ui.SelectionService;
import staraxis.ui.selection.EntityClickResolver;
import staraxis.ui.selection.EntityClickResolver.ClickIntent;

import java.util.function.Consumer;

/**
 * ShipMoveController（舰船移动交互控制器）。
 *
 * 负责：
 * - 舰船选中/取消（单击）
 * - 双击聚焦舰船并跟踪镜头
 * - 右键 Homeworld 式 3D 移动（拖拽设目标 + 调整 Y 轴）
 * - 移动目标可视化（大圆环 + 路径线 + 垂直指示线 + 目标点小圆环）
 * - 已发出的移动路径可视化
 * - 镜头跟踪实体
 *
 * 边界约束：本控制器只读访问快照数据，所有状态修改通过 runtime.submitCommand() 发送命令喵。
 */
public class ShipMoveController {

    /** 全局选中服务（选中态唯一来源），外部注入喵 */
    private SelectionService selectionService;
    private long cameraFollowTargetId = -1;

    /** 行星左键点击回调：外部（InGameHudScreen）据此打开/聚焦行星详情窗口喵 */
    private Consumer<Long> onPlanetClick;

    /**
     * 注册行星左键点击回调。命中行星时（单击/双击）以行星 entityId 调用喵。
     * 仅通知意图，不替外部做窗口管理。
     */
    public void setOnPlanetClick(Consumer<Long> onPlanetClick) {
        this.onPlanetClick = onPlanetClick;
    }

    private boolean moveModeActive;
    private boolean yAdjustMode;
    private boolean waitingRightRelease;
    private double moveTargetX, moveTargetY, moveTargetZ;
    private double moveBaseY;
    private double yAdjustBaseY;
    private int moveDragStartScreenY;
    private int lastMouseScreenX = -1, lastMouseScreenY = -1;
    private static final double Y_SENSITIVITY = 50.0;

    private long lastClickTimeNs;
    private int lastClickX = -1, lastClickY = -1;
    private static final long DOUBLE_CLICK_INTERVAL_NS = 400_000_000L;
    private static final int DOUBLE_CLICK_PX_THRESHOLD = 15;
    private final Vector3 focusTmp = new Vector3();
    private GameSnapshotProvider snapshotProvider;

    public void setSnapshotProvider(GameSnapshotProvider provider) {
        this.snapshotProvider = provider;
    }

    public void setSelectionService(SelectionService selectionService) {
        this.selectionService = selectionService;
    }

    public long getSelectedShipId() {
        return selectionService != null ? selectionService.getSelectedEntityId() : -1;
    }

    public long getCameraFollowTargetId() {
        return cameraFollowTargetId;
    }

    public boolean isMoveModeActive() {
        return moveModeActive;
    }

    /**
     * 通过命令按钮（摘要面板「移动」指令）进入移动模式。
     *
     * 与右键进入移动模式等价但不依赖右键：直接置 moveModeActive，
     * 目标点初始化为舰船当前位置（随后 XZ 跟随鼠标更新），避免立即确认时误发送向原点的移动命令。
     * 未选中舰船、快照缺失或舰船无位置时不做任何事。
     */
    public void enterMoveMode() {
        long selId = selectionService != null ? selectionService.getSelectedEntityId() : -1;
        if (selId < 0 || snapshotProvider == null) return;
        EntitySnapshot shipSnap = readShipSnapshotOrNull(snapshotProvider.getRealtimeState(), selId);
        if (shipSnap == null || shipSnap.posWorldGU == null) return;

        moveModeActive = true;
        yAdjustMode = false;
        waitingRightRelease = false;
        moveBaseY = shipSnap.posWorldGU.y();
        moveTargetX = shipSnap.posWorldGU.x();
        moveTargetY = moveBaseY;
        moveTargetZ = shipSnap.posWorldGU.z();
        lastMouseScreenX = Gdx.input.getX();
        lastMouseScreenY = Gdx.input.getY();
    }

    public double getMoveTargetY() {
        return moveTargetY;
    }

    /**
     * 取消镜头跟踪（WASDQE 手动控制镜头时调用）。
     */
    public void cancelCameraFollow() {
        cameraFollowTargetId = -1;
    }

    /**
     * 从实时快照中读取舰船快照。
     * 仅用于只读操作（位置查询、类型判断），不修改任何状态喵。
     */
    private EntitySnapshot readShipSnapshotOrNull(RealTimeWorldState state, long shipId) {
        if (state == null) return null;
        for (var snapList : state.getEntitySnapshotsBySystemView().values()) {
            for (EntitySnapshot snap : snapList) {
                if (snap.entityId == shipId && snap.details instanceof ShipDetails) {
                    return snap;
                }
            }
        }
        return null;
    }

    /**
     * 每帧更新移动模式（右键拖动目标点）。
     *
     * 状态机：
     * - 右键按下 -> 进入移动模式（等待右键松开后才开始 XZ 跟随，避免进入即锁定）
     * - 右键松开后 -> XZ 跟随鼠标在舰船平面移动
     * - 再次按住右键 -> 锁定当前 XZ，进入 Y 高度调整
     * - 松开右键 -> 退出 Y 高度调整，XZ 恢复跟随
     * - 左键确认（在 handleLeftClick 中处理）-> 发送命令退出移动模式
     */
    public void updateMoveMode(StarAxisGameRuntime runtime, WorldCamera systemCamera) {
        long selectedId = selectionService != null ? selectionService.getSelectedEntityId() : -1;
        if (selectedId < 0 || runtime == null)
            return;

        EntitySnapshot shipSnap = readShipSnapshotOrNull(snapshotProvider.getRealtimeState(), selectedId);
        if (shipSnap == null || shipSnap.posWorldGU == null)
            return;

        boolean rightJustPressed = Gdx.input.isButtonJustPressed(com.badlogic.gdx.Input.Buttons.RIGHT);
        boolean rightPressed = Gdx.input.isButtonPressed(com.badlogic.gdx.Input.Buttons.RIGHT);

        // 进入移动模式：右键首次按下，等待松开后才开始 XZ 跟随，避免进入即锁定喵
        if (rightJustPressed && !moveModeActive) {
            moveModeActive = true;
            yAdjustMode = false;
            waitingRightRelease = true;
            moveBaseY = shipSnap.posWorldGU.y();
            moveTargetY = moveBaseY;
            moveDragStartScreenY = Gdx.input.getY();
            // 立即 ray cast 一次确定初始 XZ（舰船平面）
            updateMoveTargetXZ(systemCamera);
            lastMouseScreenX = Gdx.input.getX();
            lastMouseScreenY = Gdx.input.getY();
            return;
        }

        if (!moveModeActive)
            return;

        // 等待进入移动模式后的右键松开：松开后 XZ 才开始跟随鼠标喵
        if (waitingRightRelease) {
            if (!rightPressed) {
                waitingRightRelease = false;
                updateMoveTargetXZ(systemCamera);
                lastMouseScreenX = Gdx.input.getX();
                lastMouseScreenY = Gdx.input.getY();
            }
            return;
        }

        // Y 高度调整模式切换
        if (rightJustPressed && !yAdjustMode) {
            // 按住右键：锁定当前 XZ，进入 Y 高度调整，以当前 Y 为调整基准
            yAdjustMode = true;
            yAdjustBaseY = moveTargetY;
            moveDragStartScreenY = Gdx.input.getY();
        } else if (!rightPressed && yAdjustMode) {
            // 松开右键：退出 Y 高度调整，XZ 保持锁定值不立即 ray cast（避免 Y 变化后同屏幕位置在新平面交点跳变）
            // 仅同步 lastMouse 为当前鼠标位置，鼠标真正移动后才重新 ray cast 更新 XZ
            yAdjustMode = false;
            lastMouseScreenX = Gdx.input.getX();
            lastMouseScreenY = Gdx.input.getY();
        }

        if (yAdjustMode) {
            // Y 高度调整：XZ 锁定，只更新 Y
            int dy = Gdx.input.getY() - moveDragStartScreenY;
            moveTargetY = yAdjustBaseY - (double) dy * Y_SENSITIVITY;
        } else {
            // XZ 跟随鼠标：仅在鼠标屏幕位置真正变化时才 ray cast，静止时保持目标点不动喵
            int curMouseX = Gdx.input.getX();
            int curMouseY = Gdx.input.getY();
            if (curMouseX != lastMouseScreenX || curMouseY != lastMouseScreenY) {
                updateMoveTargetXZ(systemCamera);
                lastMouseScreenX = curMouseX;
                lastMouseScreenY = curMouseY;
            }
        }
    }

    /**
     * 通过鼠标射线在当前 moveTargetY 平面上计算移动目标 XZ 坐标。
     */
    private void updateMoveTargetXZ(WorldCamera systemCamera) {
        var ray = systemCamera.camera.getPickRay(Gdx.input.getX(), Gdx.input.getY());
        double planeY = moveTargetY;
        double t = (planeY - ray.origin.y) / ray.direction.y;
        moveTargetX = ray.origin.x + ray.direction.x * t;
        moveTargetZ = ray.origin.z + ray.direction.z * t;
    }

    /**
     * 左键按下处理：双击聚焦 / 移动模式确认（发送命令）/ 单击选中。
     *
     * 单击与双击统一复用 {@link EntityClickResolver#resolveLeftClick} 解析点击意图，
     * 行星是否打开详情只在解析器集中判定，不在本控制器双击/单击各自重复判断喵。
     */
    public void handleLeftClick(long hoveredId,
                                 StarAxisGameRuntime runtime, SystemViewRenderer systemViewRenderer,
                                 WorldCamera systemCamera) {
        long now = System.nanoTime();
        int screenX = Gdx.input.getX();
        int screenY = Gdx.input.getY();

        boolean isDoubleClick = (now - lastClickTimeNs) < DOUBLE_CLICK_INTERVAL_NS
                && Math.abs(screenX - lastClickX) < DOUBLE_CLICK_PX_THRESHOLD
                && Math.abs(screenY - lastClickY) < DOUBLE_CLICK_PX_THRESHOLD;

        lastClickTimeNs = now;
        lastClickX = screenX;
        lastClickY = screenY;

        if (isDoubleClick) {
            handleDoubleClick(hoveredId, systemViewRenderer, systemCamera);
        } else if (moveModeActive) {
            handleMoveModeConfirm(hoveredId, runtime);
        } else {
            handleSingleClick(hoveredId);
        }
    }

    /**
     * 双击处理：聚焦镜头并复用点击意图选中实体/打开行星详情窗口喵。
     * 与单击共用 {@link EntityClickResolver#resolveLeftClick}，不单独判断行星；
     * 快照查不到类型时意图为 {@link ClickIntent#NONE}，仅聚焦镜头不选中喵。
     */
    private void handleDoubleClick(long hoveredId, SystemViewRenderer systemViewRenderer,
                                   WorldCamera systemCamera) {
        if (hoveredId < 0) {
            return;
        }
        cameraFollowTargetId = hoveredId;
        // 双击与单击解析同一意图：行星 → 选中并打开详情，普通实体 → 仅选中喵
        EntityType type = resolveEntityType(hoveredId);
        ClickIntent intent = EntityClickResolver.resolveLeftClick(hoveredId, type, moveModeActive);
        if (intent != ClickIntent.NONE && selectionService != null) {
            selectionService.select(hoveredId, type);
        }
        if (intent == ClickIntent.SELECT_AND_OPEN_DETAIL && onPlanetClick != null) {
            onPlanetClick.accept(hoveredId);
        }
        systemViewRenderer.getBodyPosition(hoveredId, focusTmp);
        systemCamera.target.set(focusTmp);
    }

    /**
     * 移动模式左键确认：通过命令总线发送移动命令并退出移动模式喵。
     * 移动模式点击意图由 {@link EntityClickResolver#resolveLeftClick} 集中判定，
     * 恒为 {@link ClickIntent#NONE}（确认移动不弹行星窗口、不改选中）。
     */
    private void handleMoveModeConfirm(long hoveredId, StarAxisGameRuntime runtime) {
        // 移动模式恒 NONE：防御性守卫，确保确认移动不打开行星窗口、不改动选中喵
        ClickIntent intent = EntityClickResolver.resolveLeftClick(hoveredId, resolveEntityType(hoveredId), true);
        if (intent != ClickIntent.NONE) {
            return;
        }
        // 右键移动模式确认 -> 通过命令总线发送移动命令喵
        // ownerNationId 允许为 null 传入:无主舰船由 game 端 MoveShipHandler 直接执行
        long selId = selectionService != null ? selectionService.getSelectedEntityId() : -1;
        EntitySnapshot shipSnap = readShipSnapshotOrNull(snapshotProvider.getRealtimeState(), selId);
        if (shipSnap != null) {
            String clientCmdId = "client_" + System.currentTimeMillis() + "_" + selId;
            runtime.submitCommand(new MoveShipCommand(
                    shipSnap.ownerNationId,
                    clientCmdId,
                    selId,
                    moveTargetX, moveTargetY, moveTargetZ));
        }
        moveModeActive = false;
        yAdjustMode = false;
        waitingRightRelease = false;
    }

    /**
     * 普通单击：按点击意图统一处理（行星 → 选中并打开详情，其余 → 仅选中，未命中 → 取消选中）喵。
     */
    private void handleSingleClick(long hoveredId) {
        if (selectionService == null) {
            return;
        }
        EntityType type = resolveEntityType(hoveredId);
        ClickIntent intent = EntityClickResolver.resolveLeftClick(hoveredId, type, moveModeActive);
        if (intent != ClickIntent.NONE) {
            selectionService.select(hoveredId, type);
            if (intent == ClickIntent.SELECT_AND_OPEN_DETAIL && onPlanetClick != null) {
                onPlanetClick.accept(hoveredId);
            }
        } else {
            selectionService.deselect();
        }
    }

    /**
     * 在双快照（实时 + 每日基线）中查找实体的实际类型喵。
     * 实时快照含动态实体（舰船），每日基线含天体（恒星/行星/卫星/小行星）。
     *
     * @return 实体类型；双快照均未找到时返回 null
     */
    private EntityType resolveEntityType(long entityId) {
        RealTimeWorldState state = snapshotProvider != null ? snapshotProvider.getRealtimeState() : null;
        if (state != null) {
            for (var snapList : state.getEntitySnapshotsBySystemView().values()) {
                for (EntitySnapshot snap : snapList) {
                    if (snap != null && snap.entityId == entityId) {
                        return snap.entityType;
                    }
                }
            }
        }
        var ds = snapshotProvider != null ? snapshotProvider.getDailyState() : null;
        if (ds != null && ds.publicEntityBaselinesBySectorKey != null) {
            for (var list : ds.publicEntityBaselinesBySectorKey.values()) {
                for (EntitySnapshot snap : list) {
                    if (snap != null && snap.entityId == entityId) {
                        return snap.entityType;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 渲染移动模式下的目标点标记（大圆环 + 路径线 + 垂直指示线 + 目标点小圆环）。
     */
public void renderMovePreview(ShapeRenderer shapeRenderer, StarAxisGameRuntime runtime,
                                    WorldCamera systemCamera) {
        if (!moveModeActive || shapeRenderer == null)
            return;

        long selId = selectionService != null ? selectionService.getSelectedEntityId() : -1;
        double shipX = 0, shipY = 0, shipZ = 0;
        EntitySnapshot shipSnap = readShipSnapshotOrNull(snapshotProvider.getRealtimeState(), selId);
        if (shipSnap != null && shipSnap.posWorldGU != null) {
            shipX = shipSnap.posWorldGU.x();
            shipY = shipSnap.posWorldGU.y();
            shipZ = shipSnap.posWorldGU.z();
        }

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(systemCamera.camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        float sx = (float) shipX, sy = (float) shipY, sz = (float) shipZ;
        float mx = (float) moveTargetX, my = (float) moveTargetY, mz = (float) moveTargetZ;
        float baseY = (float) moveBaseY;

        float dx = mx - sx, dz = mz - sz;
        float circleRadius = (float) Math.sqrt(dx * dx + dz * dz);
        if (circleRadius > 10) {
            shapeRenderer.setColor(0.4f, 0.8f, 1.0f, 0.25f);
            drawCircle(shapeRenderer, sx, baseY, sz, circleRadius, 48);
        }

        shapeRenderer.setColor(0.4f, 0.8f, 1.0f, 0.4f);
        shapeRenderer.line(sx, sy, sz, mx, my, mz);

        shapeRenderer.setColor(0.4f, 0.8f, 1.0f, 0.5f);
        shapeRenderer.line(mx, baseY, mz, mx, my, mz);

        shapeRenderer.setColor(0.4f, 0.8f, 1.0f, 0.7f);
        drawCircle(shapeRenderer, mx, my, mz, 30f, 24);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * 渲染已发出的移动路径（路径线 + 目标点小圈）。
     */
    public void renderMovePath(ShapeRenderer shapeRenderer, StarAxisGameRuntime runtime,
                                WorldCamera systemCamera) {
        long selId = selectionService != null ? selectionService.getSelectedEntityId() : -1;
        if (moveModeActive || selId < 0 || runtime == null || shapeRenderer == null)
            return;

        EntitySnapshot shipSnap = readShipSnapshotOrNull(snapshotProvider.getRealtimeState(), selId);
        if (shipSnap == null || !(shipSnap.details instanceof ShipDetails sd) || !sd.isMoving || sd.movementTarget == null)
            return;

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(systemCamera.camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        float sx = (float) shipSnap.posWorldGU.x(), sy = (float) shipSnap.posWorldGU.y(), sz = (float) shipSnap.posWorldGU.z();
        float tx = (float) sd.movementTarget.x(), ty = (float) sd.movementTarget.y(), tz = (float) sd.movementTarget.z();

        shapeRenderer.setColor(0.4f, 0.8f, 1.0f, 0.3f);
        shapeRenderer.line(sx, sy, sz, tx, ty, tz);

        shapeRenderer.setColor(0.4f, 0.8f, 1.0f, 0.5f);
        drawCircle(shapeRenderer, tx, ty, tz, 20f, 20);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * 镜头跟踪实体（所有输入处理完后调用）。
     */
    public void updateCameraFollow(SystemViewRenderer systemViewRenderer,
                                    StarAxisGameRuntime runtime, WorldCamera systemCamera) {
        if (cameraFollowTargetId < 0)
            return;

        if (systemViewRenderer.getBodyPosition(cameraFollowTargetId, focusTmp)) {
            systemCamera.target.set(focusTmp);
        }
    }

    private static void drawCircle(ShapeRenderer sr, float cx, float cy, float cz, float radius, int segments) {
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (i * 2.0 * Math.PI / segments);
            float a2 = (float) ((i + 1) * 2.0 * Math.PI / segments);
            sr.line(
                cx + radius * (float) Math.cos(a1), cy, cz + radius * (float) Math.sin(a1),
                cx + radius * (float) Math.cos(a2), cy, cz + radius * (float) Math.sin(a2));
        }
    }
}