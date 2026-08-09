package staraxis.ui.selection;

import staraxis.game.entity.EntityType;

/**
 * EntityClickResolver（实体点击意图解析）。
 *
 * 纯逻辑：根据命中实体、其类型与点击上下文（移动模式），决定一次左键点击的意图：
 * - 移动模式确认移动：不选中、不打开任何窗口
 * - 未命中或类型未知：不选中、不打开任何窗口
 * - 命中普通实体（舰船/恒星/卫星/小行星）：仅选中
 * - 命中行星：选中并打开/聚焦行星详情窗口
 *
 * 单击与双击共用 {@link #resolveLeftClick(long, EntityType, boolean)} 同一解析入口，
 * 双击仅在控制器侧额外叠加镜头聚焦行为；行星判断只在此处集中，
 * 避免 client 层双击/单击各自散落 if/else 重复判断。
 *
 * 本类不依赖渲染与输入，只做决策映射，可单测。
 * 实际点击处理由各视图控制器调用 {@link #resolveLeftClick(long, EntityType, boolean)} 驱动。
 */
public final class EntityClickResolver {

    /** 点击意图枚举喵 */
    public enum ClickIntent {
        /** 未命中/类型未知/移动模式确认移动：取消选中，不打开窗口喵 */
        NONE,
        /** 命中普通实体：仅选中喵 */
        SELECT,
        /** 命中行星：选中并打开/聚焦行星详情窗口喵 */
        SELECT_AND_OPEN_DETAIL
    }

    private EntityClickResolver() {
    }

    /**
     * 解析一次点击的意图喵。
     *
     * @param hoveredId   命中实体的 entityId，&lt; 0 表示未命中
     * @param hoveredType 命中实体的类型，null 表示类型未知（双快照均未找到）
     * @return 点击意图；未命中/类型未知返回 {@link ClickIntent#NONE}
     */
    public static ClickIntent resolve(long hoveredId, EntityType hoveredType) {
        if (hoveredId < 0 || hoveredType == null) {
            return ClickIntent.NONE;
        }
        if (hoveredType == EntityType.PLANET) {
            return ClickIntent.SELECT_AND_OPEN_DETAIL;
        }
        return ClickIntent.SELECT;
    }

    /**
     * 解析一次左键点击的意图（单击/双击统一入口）喵。
     *
     * 双击与单击解析同一意图：行星 → 选中并打开详情；普通实体 → 仅选中；
     * 未命中/未知 → {@link ClickIntent#NONE}。移动模式确认移动时恒为
     * {@link ClickIntent#NONE}，即确认移动不选中、不弹出行星窗口
     * （单击/双击均不例外）。
     *
     * @param hoveredId      命中实体的 entityId，&lt; 0 表示未命中
     * @param hoveredType    命中实体的类型，null 表示类型未知（双快照均未找到）
     * @param moveModeActive 是否处于移动模式（左键为确认移动）
     * @return 点击意图；移动模式恒返回 {@link ClickIntent#NONE}
     */
    public static ClickIntent resolveLeftClick(long hoveredId, EntityType hoveredType, boolean moveModeActive) {
        if (moveModeActive) {
            return ClickIntent.NONE;
        }
        return resolve(hoveredId, hoveredType);
    }
}
