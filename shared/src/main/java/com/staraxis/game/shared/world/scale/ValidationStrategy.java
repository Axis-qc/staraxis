package com.staraxis.game.shared.world.scale;

/**
 * 验证策略枚举（Validation strategy enum）。
 * 
 * 作用（Purpose）：定义配置验证的策略（警告或拒绝）。
 * 依赖（Dependencies）：无。
 * 对外接口（Public API）：枚举值 WARN, REJECT。
 */
public enum ValidationStrategy {
    /**
     * 警告：超出阈值时记录警告，但允许继续生成。
     */
    WARN,

    /**
     * 拒绝：超出阈值时拒绝生成，返回错误。
     */
    REJECT
}
