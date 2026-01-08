package com.staraxis.universegen.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * 配置数据类，支持：
 * 1. 通过 JSON 文件加载静态字段；
 * 2. 可选 Lua 脚本对配置进行二次调整（脚本接收 config 实例，可覆写字段）。
 */
public class UniverseGenConfig {

    private long seed;
    private int sectorCount;
    private float hexRadiusLy;
    private double starToDeepSpaceRatio;

    // Jackson 需要无参构造
    public UniverseGenConfig() {
    }

    public long getSeed() { return seed; }
    public int getSectorCount() { return sectorCount; }
    public float getHexRadiusLy() { return hexRadiusLy; }
    public double getStarToDeepSpaceRatio() { return starToDeepSpaceRatio; }

    public void setSeed(long seed) { this.seed = seed; }
    public void setSectorCount(int sectorCount) { this.sectorCount = sectorCount; }
    public void setHexRadiusLy(float hexRadiusLy) { this.hexRadiusLy = hexRadiusLy; }
    public void setStarToDeepSpaceRatio(double ratio) { this.starToDeepSpaceRatio = ratio; }

    // ------------------ Loader ------------------ //

    public static UniverseGenConfig load(File jsonFile, File luaScript) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        UniverseGenConfig cfg = mapper.readValue(jsonFile, UniverseGenConfig.class);
        if (luaScript != null && luaScript.exists()) {
            applyLuaPatch(cfg, luaScript);
        }
        validate(cfg);
        return cfg;
    }

    private static void applyLuaPatch(UniverseGenConfig cfg, File lua) throws IOException {
        Globals globals = JsePlatform.standardGlobals();
        globals.set("config", CoerceJavaToLua.coerce(cfg));
        globals.loadfile(lua.getAbsolutePath()).call();
    }

    private static void validate(UniverseGenConfig cfg) {
        Objects.requireNonNull(cfg, "config null");
        if (cfg.sectorCount <= 0) {
            throw new IllegalArgumentException("sectorCount must be >0");
        }
        if (cfg.hexRadiusLy <= 0) {
            throw new IllegalArgumentException("hexRadiusLy must be >0");
        }
        if (cfg.starToDeepSpaceRatio <= 0 || cfg.starToDeepSpaceRatio > 1) {
            throw new IllegalArgumentException("starToDeepSpaceRatio must be (0,1]");
        }
    }
}
