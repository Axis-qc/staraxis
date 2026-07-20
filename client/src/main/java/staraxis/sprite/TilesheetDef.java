package staraxis.sprite;

/**
 * TilesheetDef（tilesheet 声明 POJO）。
 *
 * 声明一张 tilesheet 的路径、单格尺寸和列数，
 * 供 SpriteDef 引用并按 tileRow/tileCol 截取切片。
 *
 * 职责：纯数据模型，Jackson 反序列化 sprite_registry.json 的 tilesheets 节用。
 */
public class TilesheetDef {

    /** tilesheet 文件路径（相对于 assets 根）。 */
    public String path;

    /** 每格像素尺寸（默认值，可被 SpriteDef.tileSize 覆盖）。 */
    public int tileSize;

    /** tilesheet 列数。 */
    public int columns;

    public TilesheetDef() {
    }
}
