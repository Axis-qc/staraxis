package staraxis.game.astro;

/**
 * Habitability（行星可殖民性等级）。
 *
 * 表示一颗行星对玩家来说的可殖民性/宜居程度，用于 UI 标识与推荐逻辑。
 * 从不可殖民到天堂般的宜居依次递进：
 *   INHOSPITABLE（不宜居）→ HOSTILE（恶劣）→ TOUGH（艰难）
 *   → HABITABLE（宜居）→ PARADISE（天堂）
 *
 * TODO 细化判定：当前仅按 planetTypeId 粗分（见 PlanetBody.computeHabitability），
 * 后续应引入温度带、大气成分、引力等多因素计算。
 */
public enum Habitability {

    /** 不宜居：完全无法殖民（气态巨行星、荒凉岩质等）喵 */
    INHOSPITABLE,

    /** 恶劣：环境严酷，殖民成本高喵 */
    HOSTILE,

    /** 艰难：条件勉强可殖民，需要较高科技/资源投入喵 */
    TOUGH,

    /** 宜居：适合殖民喵 */
    HABITABLE,

    /** 天堂：理想家园级宜居喵 */
    PARADISE
}