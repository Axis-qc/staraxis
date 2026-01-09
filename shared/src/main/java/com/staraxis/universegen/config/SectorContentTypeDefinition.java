package com.staraxis.universegen.config;

/**
 * 星区内容类型定义（数据驱动）。
 *
 * <p>术语对齐（见 术语对齐.md）：
 * <ul>
 *   <li>star-system：星系星区</li>
 *   <li>deep_space：深空</li>
 *   <li>nebula：星云</li>
 * </ul>
 */
public class SectorContentTypeDefinition {

    /** 类型ID（字符串，数据驱动），例如：star-system / deep_space / nebula */
    private String typeId;

    /** 显示名（中文） */
    private String displayNameZh;

    /** 调试颜色（#RRGGBB），可选 */
    private String debugColor;

    /** 图标 key（资源名），可选 */
    private String iconKey;

    public SectorContentTypeDefinition() {
    }

    public SectorContentTypeDefinition(String typeId, String displayNameZh) {
        this.typeId = typeId;
        this.displayNameZh = displayNameZh;
    }

    public String getTypeId() {
        return typeId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public String getDisplayNameZh() {
        return displayNameZh;
    }

    public void setDisplayNameZh(String displayNameZh) {
        this.displayNameZh = displayNameZh;
    }

    public String getDebugColor() {
        return debugColor;
    }

    public void setDebugColor(String debugColor) {
        this.debugColor = debugColor;
    }

    public String getIconKey() {
        return iconKey;
    }

    public void setIconKey(String iconKey) {
        this.iconKey = iconKey;
    }
}
