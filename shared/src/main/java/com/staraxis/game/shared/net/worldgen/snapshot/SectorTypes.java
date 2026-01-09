package com.staraxis.game.shared.net.worldgen.snapshot;

/**
 * 星区类型常量（SectorTypes）。
 * 
 * 术语：
 * - star-system：星系星区（会生成一个恒星系 Star System）
 * - deep_space：深空
 * - nebula：星云
 */
public final class SectorTypes {

    public static final String STAR_SYSTEM = "star-system";
    public static final String NEBULA = "nebula";
    public static final String DEEP_SPACE = "deep_space";

    private SectorTypes() {
    }
}