package staraxis.game.space.galaxy;

/**
 * StarPosition（恒星位置数据）。
 *
 * 记录恒星在星系坐标系中的位置及其基本属性。
 * 用于 Galaxy View 渲染和延迟生成恒星系内容。
 *
 * galaxyX/Y/Z：恒星在星系视图中的绝对坐标（GU），Galaxy View 渲染直接使用。
 * systemSeed：用于延迟生成该恒星系内容的确定性种子。
 */
public record StarPosition(
    /** 恒星唯一ID（全局唯一，同时作为恒星系ID）。 */
    long starId,

    /** 星系坐标X（GU），Galaxy View 渲染用。 */
    double galaxyX,

    /** 星系坐标Y（GU），Galaxy View 渲染用。 */
    double galaxyY,

    /** 星系坐标Z（GU），Galaxy View 渲染用。 */
    double galaxyZ,

    /** 光谱类型，决定颜色和大小范围。 */
    SpectralType spectralType,

    /** 恒星半径（GU），由光谱类型范围内随机生成。 */
    double radiusGU,

    /** 用于延迟生成恒星系内容的确定性种子。 */
    long systemSeed
) {
}
