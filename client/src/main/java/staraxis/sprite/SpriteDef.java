package staraxis.sprite;

import java.util.List;

/**
 * SpriteDef（精灵定义 POJO）。
 *
 * 描述一条精灵的纹理来源，支持两种模式：
 * 1. tilesheet 切片：指定 tilesheet（引用 TilesheetDef 的 key）+ tileRow/tileCol
 * 2. 单纹理：指定 texturePath 直接加载单张图片
 *
 * 职责：纯数据模型，Jackson 反序列化 sprite_registry.json 的 sprites 节用。
 */
public class SpriteDef {

    /** 精灵唯一标识（主键），供外部通过 key 查询纹理。 */
    public String spriteKey;

    /** 单纹理文件路径（模式1：与 tilesheet 二选一）。 */
    public String texturePath;

    /** 引用的 tilesheet key（模式2：与 texturePath 二选一）。 */
    public String tilesheet;

    /** tilesheet 中的行号（0-indexed）。 */
    public Integer tileRow;

    /** tilesheet 中的列号（0-indexed）。 */
    public Integer tileCol;

    /** 覆盖 tilesheet 全局 tileSize（可选，用于异形切片）。 */
    public Integer tileSize;

    /** 动画帧序列（可选，P0 预留不实现）。 */
    public List<AnimatedSpriteDef> animation;

    public SpriteDef() {
    }
}
