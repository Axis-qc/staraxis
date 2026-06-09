/**
 * @file lodSystem.ts
 *
 * @description
 * LOD（层级细节）系统 - 统一管理渲染细节的层级切换与远景简化喵。
 *
 * 作用：
 * - 根据相机缩放级别（zoom）和屏幕像素大小动态决定各类型实体的渲染方式喵。
 * - 恒星：中近景使用程序化表面，远景回退为圆形指示纹理，但不会在超大缩放时隐藏喵。
 * - 行星：屏幕像素 < 10px 时使用圆形指示纹理，zoom > 1,000 开始淡出，> 100,000 完全隐藏喵。
 * - 轨道：zoom > 1,000 时完全隐藏，不再计算。
 * - 网格：不参与LOD隐藏，始终显示喵。
 * - 六边形轮廓：始终显示，不隐藏喵。
 *
 * 核心设计：
 * - 动态简化：基于屏幕像素大小而非固定 zoom 阈值，小天体更早切换为简化渲染喵。
 * - 预计算模式：在 buildFrameState 阶段一次性计算所有 LOD 决策喵。
 * - 快速剔除：当 LOD 判定完全隐藏时，直接跳过实体遍历喵。
 * - 渐隐效果：行星在 1,000-100,000 zoom 范围内线性淡出喵。
 *
 * @usage
 * - 在 worldRenderManager 中每帧调用 `computeLodState(zoom, options)` 喵。
 * - 将结果存入 WorldFrameState.lod 喵。
 * - 各渲染器从 frame.lod 查询状态，并结合屏幕像素决定是否进一步简化喵。
 *
 * @provides
 * - **LOD状态对象**: LodState - 包含所有预计算的LOD标志和参数。
 * - **LOD计算函数**: computeLodState(zoom, options) => LodState。
 * - **LOD配置类型**: LodOptions, EntityLodConfig。
 * - **辅助函数**: shouldRender(), getLodSize(), shouldShowEffects()。
 *
 * @important_notes
 * - LOD层级从0(Full)到5(Hidden)，数值越大细节越少。
 * - 各渲染器应只读取 LOD 状态，不自行重复定义 zoom 分档喵。
 * - 具体的程序化细节强弱与远景回退由渲染器结合屏幕像素大小动态计算喵。
 * - 选中实体不受 LOD 隐藏影响，始终渲染喵。
 */

/**
 * LOD层级常量
 * 定义了标准的LOD级别，从最高细节到最低细节
 */
export const LodLevel = {
    Full: 0,       // 最高细节：完整渲染
    High: 1,       // 高细节：轻微简化
    Medium: 2,     // 中等细节：明显简化
    Low: 3,        // 低细节：大幅简化
    Minimal: 4,    // 最低细节：仅保留基本表示
    Hidden: 5,     // 隐藏：完全不渲染
} as const

export type LodLevel = typeof LodLevel[keyof typeof LodLevel]

/**
 * 实体类型的LOD配置
 * 为每种实体类型定义其LOD阈值数组
 * 阈值数组的索引对应LOD层级，值为该层级的zoom上限
 * 
 * 例如：star: [100000, 500000, 1000000, 2000000, Infinity]
 * 表示：
 *   - zoom <= 100000: LodLevel.Full
 *   - zoom <= 500000: LodLevel.High
 *   - zoom <= 1000000: LodLevel.Medium
 *   - zoom <= 2000000: LodLevel.Low
 *   - zoom > 2000000: LodLevel.Minimal
 */
export type EntityLodConfig = {
    /** 该实体类型的LOD阈值，数组长度决定LOD层级数量 */
    thresholds: number[]
    /** 是否允许完全隐藏（默认为true） */
    allowHidden?: boolean
    /** 隐藏的zoom阈值（超过此值且未被选中时隐藏） */
    hiddenThreshold?: number
}

/**
 * LOD配置选项
 */
export type LodOptions = {
    /** 恒星LOD配置 */
    star?: Partial<EntityLodConfig>
    /** 行星LOD配置 */
    planet?: Partial<EntityLodConfig>
    /** 轨道LOD配置 */
    orbit?: Partial<EntityLodConfig>
    /** 网格LOD配置 */
    grid?: Partial<EntityLodConfig>
    /** 选区LOD配置 */
    selection?: Partial<EntityLodConfig>
    /** 六边形轮廓LOD配置 */
    hexOutline?: Partial<EntityLodConfig>
    /** 是否启用LOD系统（默认true） */
    enabled?: boolean
}

/**
 * 默认LOD配置
 */
export const DEFAULT_LOD_CONFIG: Required<LodOptions> = {
    enabled: true,
    star: {
        // 恒星在 Minimal（最低细节）阶段回退为圆形指示纹理喵。
        // 地图导航依赖远景恒星指示，因此恒星不进入隐藏阶段喵。
        thresholds: [2_000, 10_000, 50_000, 100_000],
        allowHidden: false,
        hiddenThreshold: Infinity,
    },
    planet: {
        // zoom > 30 时使用圆形指示纹理（不再加载真实纹理）
        // zoom > 1_000 时开始淡出
        // zoom >= 100_000 时完全隐藏
        thresholds: [30, 1_000, 10_000, 50_000],
        allowHidden: true,
        hiddenThreshold: 100_000,
    },
    orbit: {
        // 与行星圆形图标消失条件一致
        // zoom > 1_000 时开始淡出
        // zoom >= 100_000 时完全隐藏
        thresholds: [200, 1_000, 10_000, 50_000],
        allowHidden: true,
        hiddenThreshold: 100_000,
    },
    grid: {
        // 网格不参与LOD隐藏，但参与LOD参数调整
        thresholds: [50_000, 100_000, 500_000],
        allowHidden: false,
        hiddenThreshold: Infinity,
    },
    selection: {
        thresholds: [500_000, 1_000_000, 2_000_000, 5_000_000],
        allowHidden: false,
        hiddenThreshold: Infinity,
    },
    hexOutline: {
        thresholds: [100_000, 200_000, 500_000, 1_000_000],
        allowHidden: false,  // 六边形轮廓始终显示，不隐藏
        hiddenThreshold: Infinity,
    },
}

/**
 * 实体类型的LOD状态
 */
export type EntityLodState = {
    /** 当前LOD层级 */
    level: LodLevel
    /** 是否可见（未被选中时的基础可见性） */
    visible: boolean
    /** 当前LOD的详细参数（如精灵大小缩放、纹理质量等） */
    params: LodParams
}

/**
 * LOD详细参数
 * 定义了不同LOD层级下的渲染参数
 */
export type LodParams = {
    /** 精灵/网格大小缩放系数（相对于原始大小） */
    sizeScale: number
    /** 纹理质量等级（0-1，1为最高质量） */
    textureQuality: number
    /** 是否显示标签 */
    showLabel: boolean
    /** 是否显示特效（光晕、阴影等） */
    showEffects: boolean
    /** 是否显示细节（小卫星、环等） */
    showDetails: boolean
}

/**
 * 默认LOD参数表
 * 定义了每个LOD层级的标准渲染参数
 */
export const DEFAULT_LOD_PARAMS: Record<LodLevel, LodParams> = {
    [LodLevel.Full]: {
        sizeScale: 1.0,
        textureQuality: 1.0,
        showLabel: true,
        showEffects: true,
        showDetails: true,
    },
    [LodLevel.High]: {
        sizeScale: 0.9,
        textureQuality: 0.9,
        showLabel: true,
        showEffects: true,
        showDetails: true,
    },
    [LodLevel.Medium]: {
        sizeScale: 0.75,
        textureQuality: 0.75,
        showLabel: false,
        showEffects: true,
        showDetails: false,
    },
    [LodLevel.Low]: {
        sizeScale: 0.5,
        textureQuality: 0.5,
        showLabel: false,
        showEffects: false,
        showDetails: false,
    },
    [LodLevel.Minimal]: {
        sizeScale: 0.25,
        textureQuality: 0.25,
        showLabel: false,
        showEffects: false,
        showDetails: false,
    },
    [LodLevel.Hidden]: {
        sizeScale: 0,
        textureQuality: 0,
        showLabel: false,
        showEffects: false,
        showDetails: false,
    },
}

/**
 * 完整的LOD状态
 * 包含所有实体类型的LOD决策结果
 */
export type LodState = {
    /** 当前zoom值 */
    zoom: number
    /** 是否启用LOD */
    enabled: boolean
    /** 恒星LOD状态 */
    star: EntityLodState
    /** 行星LOD状态 */
    planet: EntityLodState
    /** 轨道LOD状态 */
    orbit: EntityLodState
    /** 网格LOD状态 */
    grid: EntityLodState
    /** 选区LOD状态 */
    selection: EntityLodState
    /** 六边形轮廓LOD状态 */
    hexOutline: EntityLodState
}

/**
 * 计算单个实体类型的LOD状态
 */
function computeEntityLodState(
    zoom: number,
    config: Partial<EntityLodConfig>,
): EntityLodState {
    const thresholds = config.thresholds ?? []
    const allowHidden = config.allowHidden ?? true
    const hiddenThreshold = config.hiddenThreshold ?? Infinity

    // 检查是否应该隐藏
    if (allowHidden && zoom > hiddenThreshold) {
        return {
            level: LodLevel.Hidden,
            visible: false,
            params: DEFAULT_LOD_PARAMS[LodLevel.Hidden],
        }
    }

    // 确定LOD层级
    let level: LodLevel = LodLevel.Full
    for (let i = 0; i < thresholds.length; i++) {
        const threshold = thresholds[i]!
        if (zoom > threshold) {
            level = (i + 1) as LodLevel
        } else {
            break
        }
    }

    // 确保不超过最大值
    if (level > LodLevel.Minimal) {
        level = LodLevel.Minimal
    }

    return {
        level,
        visible: true,
        params: DEFAULT_LOD_PARAMS[level],
    }
}

/**
 * 合并LOD配置（用户配置覆盖默认配置）
 */
function mergeLodConfig(options?: LodOptions): Required<LodOptions> {
    if (!options) return DEFAULT_LOD_CONFIG

    return {
        enabled: options.enabled ?? DEFAULT_LOD_CONFIG.enabled,
        star: { ...DEFAULT_LOD_CONFIG.star, ...options.star },
        planet: { ...DEFAULT_LOD_CONFIG.planet, ...options.planet },
        orbit: { ...DEFAULT_LOD_CONFIG.orbit, ...options.orbit },
        grid: { ...DEFAULT_LOD_CONFIG.grid, ...options.grid },
        selection: { ...DEFAULT_LOD_CONFIG.selection, ...options.selection },
        hexOutline: { ...DEFAULT_LOD_CONFIG.hexOutline, ...options.hexOutline },
    }
}

/**
 * 计算完整的LOD状态
 * 
 * @param zoom 当前相机缩放值
 * @param options 可选的LOD配置
 * @returns 完整的LOD状态对象
 */
export function computeLodState(zoom: number, options?: LodOptions): LodState {
    const config = mergeLodConfig(options)

    if (!config.enabled) {
        // LOD禁用，全部使用最高细节
        const fullState: EntityLodState = {
            level: LodLevel.Full,
            visible: true,
            params: DEFAULT_LOD_PARAMS[LodLevel.Full],
        }
        return {
            zoom,
            enabled: false,
            star: fullState,
            planet: fullState,
            orbit: fullState,
            grid: fullState,
            selection: fullState,
            hexOutline: fullState,
        }
    }

    return {
        zoom,
        enabled: true,
        star: computeEntityLodState(zoom, config.star ?? {}),
        planet: computeEntityLodState(zoom, config.planet ?? {}),
        orbit: computeEntityLodState(zoom, config.orbit ?? {}),
        grid: computeEntityLodState(zoom, config.grid ?? {}),
        selection: computeEntityLodState(zoom, config.selection ?? {}),
        hexOutline: computeEntityLodState(zoom, config.hexOutline ?? {}),
    }
}

/**
 * 检查实体是否应该渲染
 * 
 * @param lodState 该实体类型的LOD状态
 * @param isSelected 实体是否被选中
 * @returns 是否应该渲染
 */
export function shouldRender(lodState: EntityLodState, isSelected: boolean): boolean {
    // 如果被选中，总是渲染
    if (isSelected) return true
    // 否则遵循LOD可见性
    return lodState.visible
}

/**
 * 获取实体的实际大小缩放
 * 
 * @param lodState 该实体类型的LOD状态
 * @param isSelected 实体是否被选中
 * @param baseSize 基础大小
 * @returns 实际应该使用的大小
 */
export function getLodSize(
    lodState: EntityLodState,
    isSelected: boolean,
    baseSize: number,
): number {
    void isSelected  // 选中状态不影响实体本身大小
    return baseSize * lodState.params.sizeScale
}

/**
 * 判断是否显示特效
 * 
 * @param lodState 该实体类型的LOD状态
 * @param isSelected 实体是否被选中
 * @returns 是否显示特效
 */
export function shouldShowEffects(lodState: EntityLodState, isSelected: boolean): boolean {
    // 被选中时总是显示特效
    if (isSelected) return true
    return lodState.params.showEffects
}
