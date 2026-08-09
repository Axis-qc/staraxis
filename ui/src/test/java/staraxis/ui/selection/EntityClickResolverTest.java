package staraxis.ui.selection;

import org.junit.jupiter.api.Test;
import staraxis.game.entity.EntityType;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * EntityClickResolverTest（实体点击意图解析单元测试）。
 *
 * 覆盖 System View 左键点击意图的全部决策分支：
 * - 行星单击/双击 → SELECT_AND_OPEN_DETAIL（选中并打开详情窗口）
 * - 其他实体（舰船/恒星/卫星/小行星）→ SELECT（仅选中，不误开窗口）
 * - 空白/未命中/类型未知 → NONE（不选中、不误开窗口）
 * - 移动模式左键确认 → NONE（确认移动不弹窗、不改选中）
 */
class EntityClickResolverTest {

    // ===== 空白 / 未命中 / 类型未知 =====

    @Test
    void noHitReturnsNone() {
        assertEquals(EntityClickResolver.ClickIntent.NONE,
                EntityClickResolver.resolve(-1, EntityType.PLANET));
    }

    @Test
    void unknownTypeReturnsNone() {
        assertEquals(EntityClickResolver.ClickIntent.NONE,
                EntityClickResolver.resolve(42, null));
    }

    @Test
    void noHitWithUnknownTypeReturnsNone() {
        assertEquals(EntityClickResolver.ClickIntent.NONE,
                EntityClickResolver.resolveLeftClick(-1, null, false));
    }

    // ===== 行星单击 / 双击（双击复用同一点击意图） =====

    @Test
    void planetSingleClickOpensDetailWindow() {
        assertEquals(EntityClickResolver.ClickIntent.SELECT_AND_OPEN_DETAIL,
                EntityClickResolver.resolveLeftClick(1001, EntityType.PLANET, false));
    }

    @Test
    void planetDoubleClickOpensDetailWindow() {
        // 双击与单击复用同一点击意图：行星双击同样打开详情窗口喵
        assertEquals(EntityClickResolver.ClickIntent.SELECT_AND_OPEN_DETAIL,
                EntityClickResolver.resolveLeftClick(2002, EntityType.PLANET, false));
    }

    // ===== 其他实体：仅选中，不误开行星窗口 =====

    @Test
    void shipSelectsOnly() {
        assertEquals(EntityClickResolver.ClickIntent.SELECT,
                EntityClickResolver.resolveLeftClick(7, EntityType.SHIP, false));
    }

    @Test
    void starSelectsOnly() {
        assertEquals(EntityClickResolver.ClickIntent.SELECT,
                EntityClickResolver.resolveLeftClick(3, EntityType.STAR, false));
    }

    @Test
    void moonSelectsOnly() {
        assertEquals(EntityClickResolver.ClickIntent.SELECT,
                EntityClickResolver.resolveLeftClick(5, EntityType.MOON, false));
    }

    @Test
    void asteroidSelectsOnly() {
        assertEquals(EntityClickResolver.ClickIntent.SELECT,
                EntityClickResolver.resolveLeftClick(9, EntityType.ASTEROID, false));
    }

    // ===== 移动模式：确认移动，不弹窗、不改选中 =====

    @Test
    void moveModePlanetClickNeverOpensWindow() {
        // 移动模式左键为确认移动：即使命中行星也禁止弹出行星窗口喵
        assertEquals(EntityClickResolver.ClickIntent.NONE,
                EntityClickResolver.resolveLeftClick(1001, EntityType.PLANET, true));
    }

    @Test
    void moveModeShipClickNeverSelects() {
        // 移动模式确认移动不改动选中：命中舰船同样返回 NONE 喵
        assertEquals(EntityClickResolver.ClickIntent.NONE,
                EntityClickResolver.resolveLeftClick(7, EntityType.SHIP, true));
    }

    @Test
    void moveModeEmptyClickNeverOpens() {
        assertEquals(EntityClickResolver.ClickIntent.NONE,
                EntityClickResolver.resolveLeftClick(-1, null, true));
    }
}
