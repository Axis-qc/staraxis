package com.staraxis.game.shared.net.worldgen.snapshot;

/**
 * 星区类型常量（SectorTypes）。
 * 
 * 术语：
 * - galaxy：星系星区（包含一个 star_system）
 * - star_system：恒星系（位于 galaxy 星区内部，不作为星区类型）
 */
public final class SectorTypes {

    public static final String GALAXY = "galaxy";
    public static final String NEBULA = "nebula";
    public static final String DEEP_SPACE = "deep_space";

    private SectorTypes() {
    }
}