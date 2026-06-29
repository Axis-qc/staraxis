package staraxis.render.lod;

/**
 * LodLevel（细节层级）。
 *
 * 根据相机距离决定渲染细节程度。
 */
public enum LodLevel {

    /** 全精度：球体 + 辉光 + 轨道环。 */
    FULL,

    /** 低精度：低面数球体，无辉光，无轨道细节。 */
    LOW,

    /** 光点：仅渲染为像素点。 */
    POINT,

    /** 隐藏：不渲染。 */
    HIDDEN
}
