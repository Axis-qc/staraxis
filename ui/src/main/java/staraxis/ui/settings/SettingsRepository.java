package staraxis.ui.settings;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 负责游戏设置的持久化（读/写 gamedata/settings.json）。
 *
 * 设计要点：
 * - 封装文件 IO 与序列化细节。
 * - 提供容错机制：文件不存在或解析失败时，自动创建并使用默认设置。
 * - 使用 Jackson (ObjectMapper) 进行 JSON 序列化，因为它已是项目依赖。
 */
public class SettingsRepository {

    private static final String SETTINGS_PATH = "gamedata/settings.json";
    private final ObjectMapper mapper;

    public SettingsRepository() {
        this.mapper = new ObjectMapper();
    }

    /**
     * 加载设置；若文件不存在或损坏，则创建并返回默认设置。
     */
    public GameSettings load() {
        FileHandle file = Gdx.files.local(SETTINGS_PATH);
        if (!file.exists()) {
            Gdx.app.log("SettingsRepository", "settings.json not found, creating default.");
            GameSettings defaults = GameSettings.createDefault();
            save(defaults);
            return defaults;
        }

        try {
            return mapper.readValue(file.read(), GameSettings.class);
        } catch (Exception e) {
            Gdx.app.error("SettingsRepository", "Failed to parse settings.json, falling back to default.", e);
            GameSettings defaults = GameSettings.createDefault();
            save(defaults); // Overwrite corrupted file
            return defaults;
        }
    }

    /**
     * 将设置保存到 gamedata/settings.json。
     */
    public void save(GameSettings settings) {
        FileHandle file = Gdx.files.local(SETTINGS_PATH);
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.writer(false), settings);
        } catch (Exception e) {
            Gdx.app.error("SettingsRepository", "Failed to save settings.json", e);
        }
    }
}
