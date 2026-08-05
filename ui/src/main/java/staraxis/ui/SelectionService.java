package staraxis.ui;

import staraxis.game.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * SelectionService（全局选中服务）。
 *
 * 管理当前选中的实体 ID 与类型，供 Galaxy/System 双视图共用。
 * 取代 ShipMoveController 中的 selectedShipId 作为唯一选中态来源。
 * 选中变更时通过 {@link SelectionListener} 回调通知外部（UI 面板刷新、高亮更新等）。
 *
 * 职责边界：
 * - 只管理"选中哪个实体"，不关心如何选中（由各视图控制器处理输入）
 * - 不管理移动模式、镜头跟踪等——这些仍在 ShipMoveController 中
 */
public class SelectionService {

    public interface SelectionListener {
        /**
         * 选中变更回调。
         *
         * @param entityId  新选中的实体 ID，-1 表示取消选中
         * @param entityType 新选中的实体类型，null 表示取消选中
         */
        void onSelectionChanged(long entityId, EntityType entityType);
    }

    private long selectedEntityId = -1;
    private EntityType selectedEntityType;
    private final List<SelectionListener> listeners = new ArrayList<>();

    public SelectionService() {
    }

    /** 选中指定实体。同实体重复选中不触发回调喵。 */
    public void select(long entityId, EntityType entityType) {
        if (entityId == selectedEntityId && entityType == selectedEntityType) {
            return;
        }
        selectedEntityId = entityId;
        selectedEntityType = entityType;
        fireSelectionChanged();
    }

    /** 取消当前选中喵。 */
    public void deselect() {
        if (selectedEntityId < 0) return;
        selectedEntityId = -1;
        selectedEntityType = null;
        fireSelectionChanged();
    }

    public long getSelectedEntityId() {
        return selectedEntityId;
    }

    public EntityType getSelectedEntityType() {
        return selectedEntityType;
    }

    public boolean hasSelection() {
        return selectedEntityId >= 0;
    }

    public void addListener(SelectionListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(SelectionListener listener) {
        listeners.remove(listener);
    }

    private void fireSelectionChanged() {
        for (SelectionListener l : listeners) {
            l.onSelectionChanged(selectedEntityId, selectedEntityType);
        }
    }
}