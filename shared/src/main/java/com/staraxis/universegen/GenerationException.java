package com.staraxis.universegen;

/**
 * 包装生成过程中的受检异常，便于调用方统一处理。
 */
public class GenerationException extends Exception {
    public GenerationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
