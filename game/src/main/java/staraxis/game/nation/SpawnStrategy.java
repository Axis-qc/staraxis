package staraxis.game.nation;

/**
 * SpawnStrategy（出生策略）
 *
 * 描述玩家国家在开局时如何选择初始星系/星球的方式。
 * 后续可以在 PlayerNationDesign 或 NationDef 中引用本枚举，以决定生成逻辑。
 */
public enum SpawnStrategy {
    /** 随机选择一个合适的星系与行星作为起始位置。 */
    RANDOM_SYSTEM,

    /** 使用预设星系（例如根据预设ID从 AstroGenerator 的映射中查找）。 */
    PRESET_SYSTEM,

    /** 自行选择：由玩家在界面中指定目标星系/星球。 */
    MANUAL_SELECTION
}
