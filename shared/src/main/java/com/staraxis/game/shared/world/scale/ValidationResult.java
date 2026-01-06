package com.staraxis.game.shared.world.scale;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 验证结果（Validation result）。
 * 
 * 作用（Purpose）：存储配置验证的结果（是否有效、警告信息、错误信息）。
 * 依赖（Dependencies）：无。
 * 对外接口（Public API）：isValid/setValid/getWarnings/addWarning/getErrors/addError。
 */
public class ValidationResult implements Serializable {

    private boolean isValid;
    private List<String> warnings;
    private List<String> errors;

    public ValidationResult() {
        this.isValid = true;
        this.warnings = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public ValidationResult(boolean isValid) {
        this.isValid = isValid;
        this.warnings = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public void addWarning(String warning) {
        if (warning != null && !warning.trim().isEmpty()) {
            warnings.add(warning.trim());
        }
    }

    public void addWarnings(List<String> warnings) {
        if (warnings != null) {
            for (String warning : warnings) {
                addWarning(warning);
            }
        }
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public void addError(String error) {
        if (error != null && !error.trim().isEmpty()) {
            errors.add(error.trim());
            isValid = false; // 添加错误时自动标记为无效
        }
    }

    public void addErrors(List<String> errors) {
        if (errors != null) {
            for (String error : errors) {
                addError(error);
            }
        }
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public String toString() {
        return "ValidationResult{"
                + "isValid=" + isValid
                + ", warnings=" + warnings.size()
                + ", errors=" + errors.size()
                + '}';
    }
}
