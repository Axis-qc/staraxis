package staraxis.game.space.system;

import staraxis.game.space.OrbitalElements;

/**
 * PlanetData（行星数据）。
 *
 * 记录行星的轨道参数、类型和视觉属性。
 * 用于 System View 渲染。
 */
public record PlanetData(
    /** 行星唯一ID。 */
    long planetId,

    /** 行星类型（决定颜色和大小范围）。 */
    PlanetType planetType,

    /** 轨道根数（描述绕主恒星的运动）。 */
    OrbitalElements orbit,

    /** 行星半径（GU），由类型范围内随机生成。 */
    double radiusGU,

    /** 行星颜色 RGB（0-1），由类型基础色 + 随机偏移生成。 */
    float colorR,
    float colorG,
    float colorB
) {
}
