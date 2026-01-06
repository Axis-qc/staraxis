package com.staraxis.game.core.world.astronomical;

/**
 * 迁移异常（Migration Exception）。
 * 
 * 作用（Purpose）：当迁移过程中发生错误时抛出（包含详细错误信息）。
 * 
 * 依赖（Dependencies）：仅 Java 标准库。
 */
public class MigrationException extends Exception {

    private static final long serialVersionUID = 1L;

    public MigrationException(String message) {
        super(message);
    }

    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
