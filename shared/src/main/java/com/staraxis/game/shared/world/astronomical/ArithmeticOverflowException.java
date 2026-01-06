package com.staraxis.game.shared.world.astronomical;

/**
 * 算术溢出异常（Arithmetic Overflow Exception）。
 * 
 * 作用（Purpose）：当日文单位运算结果超出 long 类型范围时抛出。
 * 
 * 依赖（Dependencies）：仅 Java 标准库。
 */
public class ArithmeticOverflowException extends ArithmeticException {

    private static final long serialVersionUID = 1L;

    public ArithmeticOverflowException(String message) {
        super(message);
    }

    public ArithmeticOverflowException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
