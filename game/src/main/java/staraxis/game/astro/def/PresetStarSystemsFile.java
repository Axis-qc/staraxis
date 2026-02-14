package staraxis.game.astro.def;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * PresetStarSystemsFile（预设星系文件容器）喵。
 *
 * 作用：
 * - 为 assets/star_system/star-system-presets.json 提供“对象包裹格式”的顶层结构喵。
 * - 允许未来扩展 schemaVersion、分组、标签、生成规则等字段而不破坏兼容性喵。
 *
 * 文件格式示例：
 * {
 * "schemaVersion": 1,
 * "presets": [ ... ]
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PresetStarSystemsFile {

    /** 结构版本号，用于兼容性校验喵。 */
    public int schemaVersion = 1;

    /** 预设星系定义列表，确保不为 null 喵。 */
    public List<PresetStarSystemDef> presets = new ArrayList<>();

    /**
     * 获取安全的预设列表喵。
     */
    public List<PresetStarSystemDef> getPresetsSafe() {
        return presets == null ? List.of() : presets;
    }
}
