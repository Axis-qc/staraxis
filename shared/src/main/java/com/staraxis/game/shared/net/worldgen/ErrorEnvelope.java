package com.staraxis.game.shared.net.worldgen;

/**
 * 结构化错误对象（ErrorEnvelope）。
 */
public class ErrorEnvelope {

    private String errorCode;
    private String messageKey;
    private String details;

    public ErrorEnvelope() {
    }

    public ErrorEnvelope(String errorCode, String messageKey, String details) {
        this.errorCode = errorCode;
        this.messageKey = messageKey;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
