package staraxis.sprite;

import java.util.List;

/**
 * AnimatedSpriteDef（动画帧定义 POJO）。
 *
 * 描述动画精灵的单帧信息。
 * P0 阶段预留不实现，字段先定义好供后续扩展。
 *
 * 职责：纯数据模型，Jackson 反序列化用。
 */
public class AnimatedSpriteDef {

    /** 帧持续时间（秒）。 */
    public double duration;

    /** 帧纹理路径（可选，未指定则沿用父级 SpriteDef 的纹理来源）。 */
    public String texturePath;

    /** tilesheet 行号（0-indexed）。 */
    public Integer tileRow;

    /** tilesheet 列号（0-indexed）。 */
    public Integer tileCol;

    public AnimatedSpriteDef() {
    }
}
