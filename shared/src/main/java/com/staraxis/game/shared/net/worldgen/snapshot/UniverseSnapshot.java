package com.staraxis.game.shared.net.worldgen.snapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 新版世界快照协议（UniverseSnapshot）。
 * 用于替换原有的 WorldSnapshot，支持单/双/三恒星系统。
 * 
 * 注意：本协议不保证向后兼容，允许破坏性变更。
 */
public class UniverseSnapshot {
    /**
     * 协议版本，用于客户端兼容性检查
     */
    private String schemaVersion = com.staraxis.game.shared.net.worldgen.SchemaVersions.WORLDGEN_V2;
    
    /**
     * 世界种子值
     */
    private long seedValue;
    
    /**
     * 世界边界半径（六边形格数）
     */
    private int boundsRadius;
    
    /**
     * 所有星区快照列表
     */
    private List<SectorSnapshot> sectors = new ArrayList<>();
    
    /**
     * 世界生成统计信息
     */
    private WorldGenStatsSnapshot stats;

    // Getters and Setters
    
    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public long getSeedValue() {
        return seedValue;
    }

    public void setSeedValue(long seedValue) {
        this.seedValue = seedValue;
    }

    public int getBoundsRadius() {
        return boundsRadius;
    }

    public void setBoundsRadius(int boundsRadius) {
        this.boundsRadius = boundsRadius;
    }

    public List<SectorSnapshot> getSectors() {
        return sectors;
    }

    public void setSectors(List<SectorSnapshot> sectors) {
        this.sectors = sectors != null ? sectors : new ArrayList<>();
    }

    public WorldGenStatsSnapshot getStats() {
        return stats;
    }

    public void setStats(WorldGenStatsSnapshot stats) {
        this.stats = stats;
    }
}