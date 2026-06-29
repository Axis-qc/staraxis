package staraxis.game.space.galaxy;

/**
 * GalaxyType（星系类型）。
 *
 * 定义可生成的星系形状类型。
 * 每种类型有对应的 GalaxyGenerator 实现。
 */
public enum GalaxyType {

    /** 螺旋臂星系（对数螺旋公式生成）。 */
    SPIRAL,

    /** 椭圆星系（椭球分布）。 */
    ELLIPTICAL,

    /** 不规则星系（随机分布）。 */
    IRREGULAR
}
