package com.staraxis.universegen.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 配置数据类，支持：
 * 1. 通过 JSON 文件加载静态字段；
 * 2. 可选 Lua 脚本对配置进行二次调整（脚本接收 config 实例，可覆写字段）。
 */
public class UniverseGenConfig {

    private long seed;
    private int galaxyRadiusR;
    private float hexRadiusLy;

    /**
     * 历史字段：用于旧版 SectorGenerator 的“星系星区 vs 深空”比例。
     * 015 正式分配逻辑将使用 contentRatios。
     */
    private double starToDeepSpaceRatio;

    /**
     * 内容类型注册表（数据驱动）。key 为 typeId。
     *
     * 注意：术语对齐.md 规定本期至少包含：star-system / deep_space / nebula。
     */
    private SectorContentTypeRegistry contentTypeRegistry;

    /**
     * 内容分配比例（开局设置保证比例和为 1.0）。key 为 typeId。
     */
    private Map<String, Double> contentRatios;

    /**
     * 星系预设列表（数据驱动），用于在生成时优先占用星区。
     */
    private List<GalaxyPreset> galaxyPresets;

    // Jackson 需要无参构造
    public UniverseGenConfig() {
        // 默认注册表（满足最小可用）
        this.contentTypeRegistry = new SectorContentTypeRegistry();
        this.contentTypeRegistry.put(new SectorContentTypeDefinition("star-system", "星系星区"));
        this.contentTypeRegistry.put(new SectorContentTypeDefinition("deep_space", "深空"));
        this.contentTypeRegistry.put(new SectorContentTypeDefinition("nebula", "星云"));

        // 默认比例（仅兜底；正式比例来自开局设置/外部配置）
        this.contentRatios = new HashMap<>();
        this.contentRatios.put("star-system", 0.5);
        this.contentRatios.put("deep_space", 0.3);
        this.contentRatios.put("nebula", 0.2);

        this.galaxyPresets = new ArrayList<>();

        // 旧字段给一个合理默认值，避免 JSON 未填时 validate 失败
        this.starToDeepSpaceRatio = 0.5;
    }

    public long getSeed() {
        return seed;
    }

    public int getGalaxyRadiusR() {
        return galaxyRadiusR;
    }

    /**
     * 根据六边形半径 R 计算总星区数：N = 1 + 3R(R+1)
     */
    public int getSectorCount() {
        return 1 + 3 * galaxyRadiusR * (galaxyRadiusR + 1);
    }

    public float getHexRadiusLy() {
        return hexRadiusLy;
    }

    public double getStarToDeepSpaceRatio() {
        return starToDeepSpaceRatio;
    }

    public SectorContentTypeRegistry getContentTypeRegistry() {
        return contentTypeRegistry;
    }

    public Map<String, Double> getContentRatios() {
        return contentRatios;
    }

    public List<GalaxyPreset> getGalaxyPresets() {
        return galaxyPresets;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public void setGalaxyRadiusR(int galaxyRadiusR) {
        this.galaxyRadiusR = galaxyRadiusR;
    }

    public void setHexRadiusLy(float hexRadiusLy) {
        this.hexRadiusLy = hexRadiusLy;
    }

    public void setStarToDeepSpaceRatio(double ratio) {
        this.starToDeepSpaceRatio = ratio;
    }

    public void setContentTypeRegistry(SectorContentTypeRegistry contentTypeRegistry) {
        this.contentTypeRegistry = contentTypeRegistry;
    }

    public void setContentRatios(Map<String, Double> contentRatios) {
        this.contentRatios = contentRatios;
    }

    public void setGalaxyPresets(List<GalaxyPreset> galaxyPresets) {
        this.galaxyPresets = galaxyPresets;
    }

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
        if (cfg.galaxyRadiusR < 0) {
            throw new IllegalArgumentException("galaxyRadiusR must be >=0");
        }
        if (cfg.hexRadiusLy <= 0) {
            throw new IllegalArgumentException("hexRadiusLy must be >0");
        }
        if (cfg.starToDeepSpaceRatio <= 0 || cfg.starToDeepSpaceRatio > 1) {
            throw new IllegalArgumentException("starToDeepSpaceRatio must be (0,1]");
        }
        if (cfg.contentTypeRegistry == null || cfg.contentTypeRegistry.getDefinitions() == null || cfg.contentTypeRegistry.getDefinitions().isEmpty()) {
            throw new IllegalArgumentException("contentTypeRegistry must not be empty");
        }
        if (cfg.contentRatios == null || cfg.contentRatios.isEmpty()) {
            throw new IllegalArgumentException("contentRatios must not be empty");
        }
        if (cfg.galaxyPresets == null) {
            throw new IllegalArgumentException("galaxyPresets must not be null");
        }
        double sum = 0.0;
        for (double v : cfg.contentRatios.values()) {
            sum += v;
        }
        if (Math.abs(sum - 1.0) > 1e-6) {
            throw new IllegalArgumentException("contentRatios sum must be 1.0");
        }
    }
}
