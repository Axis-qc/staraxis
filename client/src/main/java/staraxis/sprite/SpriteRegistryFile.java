package staraxis.sprite;

import java.util.List;
import java.util.Map;

/**
 * SpriteRegistryFile（纹理注册表 JSON 顶层容器 POJO）。
 *
 * 对应 assets/sprites/sprite_registry.json 的完整结构：
 * - tilesheets：tilesheet 声明表（key → {path, tileSize, columns}）
 * - sprites：精灵定义列表
 * - defaultSprite：默认精灵 key，getDefault() 按此查找
 *
 * 职责：纯数据模型，Jackson 反序列化用。
 */
public class SpriteRegistryFile {

    /** tilesheet 声明表，key 为 tilesheet 标识名。 */
    public Map<String, TilesheetDef> tilesheets;

    /** 精灵定义列表。 */
    public List<SpriteDef> sprites;

    /** 默认精灵 key，未匹配时使用的兜底纹理。 */
    public String defaultSprite;

    public SpriteRegistryFile() {
    }
}
