package staraxis.ui.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/**
 * CommandErrorMessagesTest（命令错误码中文映射测试，G1.2）喵。
 *
 * 覆盖：
 * - 殖民命令失败错误码 → 中文提示喵
 * - 时间/倍率、基础参数错误码 → 中文提示喵
 * - 未知错误码回退返回原始码（信息不丢失）喵
 * - null / 空白错误码返回「未知错误」喵
 * - 映射值均为可读中文（不含错误码下划线原文）喵
 */
class CommandErrorMessagesTest {

    @Test
    void colonizeErrorCodesMapToChinese() {
        assertEquals("殖民舰距行星过远", CommandErrorMessages.zh("ship_too_far_from_planet"));
        assertEquals("该行星已被殖民", CommandErrorMessages.zh("planet_already_owned"));
        assertEquals("该行星已被殖民", CommandErrorMessages.zh("planet_already_colonized"));
        assertEquals("选中舰船不是殖民舰", CommandErrorMessages.zh("ship_is_not_colony_ship"));
        assertEquals("目标不是行星", CommandErrorMessages.zh("target_is_not_planet"));
        assertEquals("殖民参数无效", CommandErrorMessages.zh("invalid_colonization_parameters"));
        assertEquals("未找到殖民舰实体", CommandErrorMessages.zh("ship_entity_not_found"));
        assertEquals("未找到目标行星", CommandErrorMessages.zh("planet_entity_not_found"));
        assertEquals("该行星不宜居，无法殖民", CommandErrorMessages.zh("planet_not_colonizable"));
        assertEquals("实体位置数据缺失", CommandErrorMessages.zh("entity_position_missing"));
        assertEquals("国家不存在", CommandErrorMessages.zh("nation_not_found"));
        assertEquals("国家当前不活跃", CommandErrorMessages.zh("nation_inactive"));
        assertEquals("殖民舰不属于当前国家", CommandErrorMessages.zh("ship_owner_mismatch"));
    }

    @Test
    void timeAndParameterErrorCodesMapToChinese() {
        assertEquals("服务器世界禁止修改时间流速",
                CommandErrorMessages.zh("forbidden_time_step_in_server_world"));
        assertEquals("时间流速设置无效", CommandErrorMessages.zh("invalid_player_time_step"));
        assertEquals("系统时间倍率设置无效", CommandErrorMessages.zh("invalid_system_scale"));
        assertEquals("缺少玩家 ID", CommandErrorMessages.zh("playerId_required"));
        assertEquals("缺少国家 ID", CommandErrorMessages.zh("nationId_required"));
        assertEquals("缺少星系 ID", CommandErrorMessages.zh("systemId_required"));
    }

    @Test
    void unknownCodeFallsBackToRawCode() {
        assertEquals("some_unknown_code", CommandErrorMessages.zh("some_unknown_code"));
    }

    @Test
    void nullAndBlankMapToUnknownText() {
        assertEquals(CommandErrorMessages.UNKNOWN_ERROR_TEXT, CommandErrorMessages.zh(null));
        assertEquals(CommandErrorMessages.UNKNOWN_ERROR_TEXT, CommandErrorMessages.zh(""));
        assertEquals(CommandErrorMessages.UNKNOWN_ERROR_TEXT, CommandErrorMessages.zh("   "));
    }

    @Test
    void mappedTextsAreReadableChinese() {
        // 映射值必须是可读中文，不应残留错误码下划线原文喵
        assertFalse(CommandErrorMessages.zh("planet_not_colonizable").contains("_"));
        assertFalse(CommandErrorMessages.zh("ship_owner_mismatch").contains("_"));
        assertFalse(CommandErrorMessages.zh("command_failed").contains("_"));
    }
}
