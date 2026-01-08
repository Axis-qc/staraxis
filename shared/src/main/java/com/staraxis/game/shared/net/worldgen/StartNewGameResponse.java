package com.staraxis.game.shared.net.worldgen;

import com.staraxis.game.shared.net.worldgen.snapshot.UniverseSnapshot;

/**
 * 新游戏世界生成响应（StartNewGameResponse）。
 * 
 * 注意：013 特性起，world 字段破坏性替换为 UniverseSnapshot。
 */
public class StartNewGameResponse {

    private String schemaVersion;
    private StartNewGameEffectiveConfig effectiveConfig;
    private UniverseSnapshot world;
    private ErrorEnvelope error;

    public StartNewGameResponse() {
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public StartNewGameEffectiveConfig getEffectiveConfig() {
        return effectiveConfig;
    }

    public void setEffectiveConfig(StartNewGameEffectiveConfig effectiveConfig) {
        this.effectiveConfig = effectiveConfig;
    }

    public UniverseSnapshot getWorld() {
        return world;
    }

    public void setWorld(UniverseSnapshot world) {
        this.world = world;
    }

    public ErrorEnvelope getError() {
        return error;
    }

    public void setError(ErrorEnvelope error) {
        this.error = error;
    }
}
