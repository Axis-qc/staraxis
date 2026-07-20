package staraxis.sprite;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SpriteRegistry（客户端纹理注册器）。
 *
 * 启动时将 assets/sprites/sprite_registry.json 中声明的所有纹理
 * 加载到 GPU 内存，构建 spriteKey → TextureRegion 映射。
 * 渲染时按 key 直接取用，零文件路径感知。
 *
 * 使用方式：
 * - 启动时调用 loadAll() 一次性加载所有纹理
 * - 渲染时调用 get("ship_frigate") 取 TextureRegion
 * - 关闭时调用 dispose() 释放所有纹理
 *
 * Tileheet 复用：同一 tilesheet 的所有精灵共享一个 Texture 实例，
 * 通过 TextureRegion 裁剪不同的切片区域。
 *
 * 后续扩展：getAnimation(spriteKey) 可返回 Animation<TextureRegion>。
 */
public class SpriteRegistry {

    private static final String REGISTRY_PATH = "assets/sprites/sprite_registry.json";

    private static final Logger LOG = new Logger("SpriteRegistry", Logger.INFO);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** tilesheet key → 已加载的 GPU Texture（多精灵共享）。 */
    private final Map<String, Texture> tilesheetTextures = new HashMap<>();

    /** tilesheet key → TilesheetDef（用于查询 tileSize 等元数据）。 */
    private final Map<String, TilesheetDef> tilesheetDefs = new HashMap<>();

    /** spriteKey → TextureRegion（最终查询目标）。 */
    private final Map<String, TextureRegion> textures = new HashMap<>();

    /** 已加载的所有 Texture 引用（含 tilesheet 和单纹理），dispose 时统一释放。 */
    private final Set<Texture> allTextures = new HashSet<>();

    /** JSON 中的 defaultSprite 字段值。 */
    private String defaultSpriteKey;

    /** 是否已完成加载。 */
    private boolean loaded;

    /**
     * 一次性加载 sprite_registry.json 并构建所有 GPU 纹理。
     * 阻塞 IO + GPU 上传，仅允许在启动线程调用。
     */
    public void loadAll() {
        if (loaded) {
            LOG.info("SpriteRegistry 已加载，跳过重复调用");
            return;
        }

        // 1. 反序列化 JSON → POJO
        SpriteRegistryFile file;
        try {
            file = objectMapper.readValue(
                    Gdx.files.internal(REGISTRY_PATH).file(),
                    SpriteRegistryFile.class);
        } catch (Exception e) {
            LOG.error("无法加载 sprite_registry.json: " + e.getMessage());
            loaded = true;
            return;
        }

        if (file == null) {
            LOG.error("sprite_registry.json 解析为空");
            loaded = true;
            return;
        }

        this.defaultSpriteKey = file.defaultSprite;

        // 2. 加载 tilesheet Texture（先于 sprites 遍历）
        if (file.tilesheets != null) {
            for (Map.Entry<String, TilesheetDef> entry : file.tilesheets.entrySet()) {
                String key = entry.getKey();
                TilesheetDef def = entry.getValue();
                if (def == null || def.path == null || def.path.isBlank()) {
                    LOG.error("tilesheet " + key + " 缺少 path，跳过");
                    continue;
                }
                try {
                    Texture tex = new Texture(Gdx.files.internal(def.path));
                    tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                    tilesheetTextures.put(key, tex);
                    tilesheetDefs.put(key, def);
                    allTextures.add(tex);
                } catch (Exception e) {
                    LOG.error("无法加载 tilesheet '" + key + "': " + def.path + " — " + e.getMessage());
                }
            }
        }

        // 3. 遍历 sprites，构建 TextureRegion
        if (file.sprites != null) {
            for (SpriteDef def : file.sprites) {
                if (def == null || def.spriteKey == null || def.spriteKey.isBlank()) {
                    LOG.error("sprite 缺少 spriteKey，跳过");
                    continue;
                }

                TextureRegion region = buildRegion(def);
                if (region != null) {
                    textures.put(def.spriteKey, region);
                }
            }
        }

        LOG.info("SpriteRegistry 加载完成: " + textures.size() + " 精灵, "
                + tilesheetTextures.size() + " tilesheet(s)");

        loaded = true;
    }

    /**
     * 按 spriteKey 获取纹理，纯内存 Map 查找（O(1)）。
     *
     * @param spriteKey 精灵标识 key
     * @return TextureRegion，未找到返回 null
     */
    public TextureRegion get(String spriteKey) {
        return textures.get(spriteKey);
    }

    /**
     * 获取默认纹理。
     * 对应 sprite_registry.json 中的 defaultSprite 字段。
     *
     * @return 默认 TextureRegion，未配置则返回第一个精灵
     */
    public TextureRegion getDefault() {
        TextureRegion region = null;
        if (defaultSpriteKey != null) {
            region = textures.get(defaultSpriteKey);
        }
        if (region == null && !textures.isEmpty()) {
            region = textures.values().iterator().next();
        }
        return region;
    }

    /** 释放所有已加载的纹理资源。 */
    public void dispose() {
        for (Texture tex : allTextures) {
            tex.dispose();
        }
        tilesheetTextures.clear();
        tilesheetDefs.clear();
        textures.clear();
        allTextures.clear();
        loaded = false;
    }

    // ---- 内部方法 ----

    /**
     * 根据 SpriteDef 构建 TextureRegion。
     * 支持 tilesheet 切片模式和单纹理模式。
     */
    private TextureRegion buildRegion(SpriteDef def) {
        // 模式1：tilesheet 切片
        if (def.tilesheet != null && !def.tilesheet.isBlank()) {
            Texture tex = tilesheetTextures.get(def.tilesheet);
            if (tex == null) {
                LOG.error("sprite '" + def.spriteKey + "' 引用了未加载的 tilesheet: " + def.tilesheet);
                return null;
            }

            TilesheetDef sheetDef = tilesheetDefs.get(def.tilesheet);
            int tSize = def.tileSize != null
                    ? def.tileSize
                    : (sheetDef != null ? sheetDef.tileSize : tex.getWidth());
            int tRow = def.tileRow != null ? def.tileRow : 0;
            int tCol = def.tileCol != null ? def.tileCol : 0;

            return new TextureRegion(tex,
                    tCol * tSize,
                    tRow * tSize,
                    tSize, tSize);
        }

        // 模式2：单纹理
        if (def.texturePath != null && !def.texturePath.isBlank()) {
            try {
                Texture tex = new Texture(Gdx.files.internal(def.texturePath));
                tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                allTextures.add(tex);
                return new TextureRegion(tex);
            } catch (Exception e) {
                LOG.error("无法加载纹理 '" + def.spriteKey + "': " + def.texturePath + " — " + e.getMessage());
                return null;
            }
        }

        LOG.error("sprite '" + def.spriteKey + "' 既无 tilesheet 也无 texturePath，跳过");
        return null;
    }
}
