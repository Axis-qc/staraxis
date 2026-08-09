package staraxis.ui.notifications;

import java.util.Map;

/**
 * CommandErrorMessages
 *
 * @description
 *              命令失败错误码的中文可读化映射（G1.2 命令结果反馈）喵。
 *
 *              错误码来源：game 侧命令处理器抛出的 IllegalArgumentException 消息
 *              （如 ColonizePlanetHandler 的 ship_too_far_from_planet、
 *              planet_already_owned、ship_is_not_colony_ship）喵。
 *              UI 层将错误码映射为中文提示展示给玩家喵。
 *
 * @api
 *      - String zh(String code): 将错误码映射为中文提示
 *
 * @important_notes
 *                  - 未知错误码回退返回原始码，保证信息不丢失，便于补充映射喵。
 *                  - null / 空白错误码返回「未知错误」喵。
 */
public final class CommandErrorMessages {

    private CommandErrorMessages() {
    }

    /** 缺失 / 空白错误码时的兜底文案喵 */
    public static final String UNKNOWN_ERROR_TEXT = "未知错误";

    /** 错误码 → 中文提示映射表喵 */
    private static final Map<String, String> ZH_BY_CODE = Map.ofEntries(
            // ── CommandBus / Command 基础错误 ──
            Map.entry("command_required", "缺少命令参数"),
            Map.entry("world_state_required", "缺少世界状态"),
            Map.entry("command_class_required", "缺少命令类型"),
            Map.entry("handler_required", "缺少命令处理器"),
            Map.entry("command_type_required", "缺少命令类型标识"),

            // ── ColonizePlanetHandler 殖民失败原因 ──
            Map.entry("invalid_colonization_parameters", "殖民参数无效"),
            Map.entry("ship_entity_not_found", "未找到殖民舰实体"),
            Map.entry("planet_entity_not_found", "未找到目标行星"),
            Map.entry("ship_is_not_colony_ship", "选中舰船不是殖民舰"),
            Map.entry("target_is_not_planet", "目标不是行星"),
            Map.entry("planet_already_owned", "该行星已被殖民"),
            Map.entry("planet_already_colonized", "该行星已被殖民"),
            Map.entry("planet_not_colonizable", "该行星不宜居，无法殖民"),
            Map.entry("entity_position_missing", "实体位置数据缺失"),
            Map.entry("ship_too_far_from_planet", "殖民舰距行星过远"),
            Map.entry("nation_not_found", "国家不存在"),
            Map.entry("nation_inactive", "国家当前不活跃"),
            Map.entry("ship_owner_mismatch", "殖民舰不属于当前国家"),

            // ── 时间 / 倍率相关 ──
            Map.entry("forbidden_time_step_in_server_world", "服务器世界禁止修改时间流速"),
            Map.entry("invalid_player_time_step", "时间流速设置无效"),
            Map.entry("invalid_system_scale", "系统时间倍率设置无效"),

            // ── 玩家 / 国家 / 星系参数 ──
            Map.entry("playerId_required", "缺少玩家 ID"),
            Map.entry("nationId_required", "缺少国家 ID"),
            Map.entry("systemId_required", "缺少星系 ID"),

            // ── 系统兜底 ──
            Map.entry("command_failed", "命令执行失败")
    );

    /**
     * 将错误码映射为中文提示喵。
     *
     * @param code game 侧错误码（可为 null / 空白）
     * @return 中文提示；未知错误码返回原始码，null / 空白返回「未知错误」喵
     */
    public static String zh(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN_ERROR_TEXT;
        }
        return ZH_BY_CODE.getOrDefault(code, code);
    }
}
