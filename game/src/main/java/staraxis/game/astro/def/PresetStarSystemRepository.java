package staraxis.game.astro.def;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PresetStarSystemRepository（预设星系仓库）喵。
 *
 * 作用：
 * - 负责从磁盘加载 assets/star_system/star-system-presets.json 喵。
 * - 提供预设星系定义的查询与获取接口喵。
 *
 * 使用方式：
 * - 在游戏启动或重新加载配置时调用 loadAll() 喵。
 * - AstroGenerator 通过 getPresets() 获取所有待生成的预设喵。
 *
 * 注意事项：
 * - 文件读取属于阻塞 IO，不应在模拟 tick 内调用喵。
 */
public class PresetStarSystemRepository {

    private final ObjectMapper objectMapper;
    private final List<PresetStarSystemDef> presets = new ArrayList<>();

    public PresetStarSystemRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 加载所有预设星系定义喵。
     */
    public void loadAll() {
        presets.clear();
        try {
            File file = new File("assets/star_system/star-system-presets.json");
            if (file.exists()) {
                PresetStarSystemsFile wrapper = objectMapper.readValue(file, PresetStarSystemsFile.class);
                if (wrapper != null) {
                    presets.addAll(wrapper.getPresetsSafe());
                }
                staraxis.game.log.GameLog.logThrottled("preset_load",
                        "[PresetStarSystemRepository] Loaded " + presets.size()
                                + " presets schemaVersion="
                                + (wrapper == null ? "null" : String.valueOf(wrapper.schemaVersion)));
            } else {
                System.out.println(
                        "[PresetStarSystemRepository] No preset file found at assets/star_system/star-system-presets.json 喵");
            }
        } catch (Exception e) {
            System.err.println("[PresetStarSystemRepository] Failed to load presets: " + e.getMessage() + " 喵");
        }
    }

    /**
     * 获取所有加载的预设星系定义喵。
     */
    public List<PresetStarSystemDef> getPresets() {
        return Collections.unmodifiableList(presets);
    }
}
