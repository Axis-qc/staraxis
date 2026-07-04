package staraxis.game.space.event;

import java.util.*;

/**
 * CrossSystemEventTable（跨系统事件表）。
 *
 * 全局事件表，管理所有在途的跨系统移动事件。
 * 主线程持有，所有 worker 只读共享（阶段4合并时写）。
 *
 * 数据结构：
 * - eventsByArrivalTick：按到达 tick 索引，支持快速查询当前 tick 到期事件
 * - inTransitEntities：在途实体索引（entityId -> event），避免重复出发
 */
public class CrossSystemEventTable {

    /** 事件按到达 tick 索引（arrivalTick -> events）。 */
    private final NavigableMap<Long, List<CrossSystemEvent>> eventsByArrivalTick = new TreeMap<>();

    /** 在途实体索引（entityId -> event）。 */
    private final Map<Long, CrossSystemEvent> inTransitEntities = new HashMap<>();

    /**
     * 添加一个跨系统事件到事件表。
     * 如果是 FTL_DEPARTURE 类型，同时标记实体为在途。
     */
    public void addEvent(CrossSystemEvent event) {
        eventsByArrivalTick.computeIfAbsent(event.arrivalTick, k -> new ArrayList<>()).add(event);
        if (event.type == CrossSystemEvent.EventType.FTL_DEPARTURE) {
            inTransitEntities.put(event.entityId, event);
        }
    }

    /**
     * 获取指定 tick 到期的所有事件，并从表中移除。
     * 由 TickDispatcher 阶段1在每 tick 开始时调用。
     *
     * @param tick 当前游戏 tick
     * @return 该 tick 到达的事件列表，无事件返回空列表
     */
    public List<CrossSystemEvent> getEventsDueAt(long tick) {
        List<CrossSystemEvent> due = eventsByArrivalTick.remove(tick);
        if (due == null) return List.of();
        // 从在途索引中移除已到达实体
        for (CrossSystemEvent event : due) {
            inTransitEntities.remove(event.entityId);
        }
        return due;
    }

    /**
     * 检查指定实体是否在途。
     */
    public boolean isInTransit(long entityId) {
        return inTransitEntities.containsKey(entityId);
    }

    /**
     * 获取在途实体的当前事件。
     */
    public CrossSystemEvent getInTransitEvent(long entityId) {
        return inTransitEntities.get(entityId);
    }

    /**
     * 获取在途实体索引的只读视图。
     */
    public Map<Long, CrossSystemEvent> getInTransitEntitiesView() {
        return Collections.unmodifiableMap(inTransitEntities);
    }

    /**
     * 获取当前在途实体数量。
     */
    public int getInTransitCount() {
        return inTransitEntities.size();
    }

    /**
     * 清空所有事件（仅用于测试或世界重置）。
     */
    public void clear() {
        eventsByArrivalTick.clear();
        inTransitEntities.clear();
    }
}
