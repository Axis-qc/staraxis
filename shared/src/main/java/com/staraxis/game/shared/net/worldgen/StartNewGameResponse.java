package com.staraxis.game.shared.net.worldgen;

import com.staraxis.game.shared.net.worldgen.snapshot.WorldSnapshot;

/**
 * 新游戏世界生成响应（StartNewGameResponse）。
 */
public class StartNewGameResponse {

    private String schemaVersion;
    private StartNewGameEffectiveConfig effectiveConfig;
    private WorldSnapshot world;
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

    public WorldSnapshot getWorld() {
        return world;
    }

    public void setWorld(WorldSnapshot world) {
        this.world = world;
    }

    public ErrorEnvelope getError() {
        return error;
    }

    public void setError(ErrorEnvelope error) {
        this.error = error;
    }
}
