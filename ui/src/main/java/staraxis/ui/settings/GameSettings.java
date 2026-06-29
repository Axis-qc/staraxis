package staraxis.ui.settings;

/**
 * 游戏设置的数据模型 (POJO)。
 *
 * 设计要点：
 * - 包含所有可配置的游戏设置项。
 * - 提供默认值，用于在设置文件缺失或损坏时回退。
 * - 字段使用 public，便于 Jackson/Json 序列化与反序列化。
 */
public class GameSettings {

    public int schemaVersion = 1;

    // 显示设置
    public String resolution = "1280x720";
    public boolean fullscreen = false;
    public boolean vsync = true;
    public int fpsLimit = 0; // 0 for unlimited

    // 缩放设置
    public float uiScale = 1.0f;
    public float fontScale = 1.0f;

    // 画面设置
    public String graphicsQuality = "medium"; // low, medium, high
    public int gpuIndex = 0; // 显卡/显示器索引（0 = 默认/首选）

    // 音频设置
    public float masterVolume = 0.8f;
    public float musicVolume = 1.0f;
    public float sfxVolume = 1.0f;

    /**
     * 创建一份默认设置。
     */
    public static GameSettings createDefault() {
        return new GameSettings();
    }
}
