package com.staraxis.universegen.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 星区内容类型注册表（数据驱动）。
 *
 * <p>说明：
 * - 本注册表用于描述“有哪些星区类型（typeId）可用，以及其显示/调试属性”。
 * - 具体分配比例不在这里定义，由 UniverseGenConfig 提供。
 */
public class SectorContentTypeRegistry {

    /** key=typeId */
    private Map<String, SectorContentTypeDefinition> definitions = new LinkedHashMap<>();

    public SectorContentTypeRegistry() {
    }

    public Map<String, SectorContentTypeDefinition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(Map<String, SectorContentTypeDefinition> definitions) {
        this.definitions = definitions;
    }

    public void put(SectorContentTypeDefinition def) {
        if (def == null || def.getTypeId() == null) {
            throw new IllegalArgumentException("def/typeId 不能为空");
        }
        definitions.put(def.getTypeId(), def);
    }
}
