package com.staraxis.game.core.world.astronomical;

/**
 * 配置异常（Configuration Exception）。
 * 
 * 作用（Purpose）：当配置文件格式错误或缺失时抛出。
 * 
 * 依赖（Dependencies）：仅 Java 标准库。
 */
public class ConfigurationException extends Exception {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
