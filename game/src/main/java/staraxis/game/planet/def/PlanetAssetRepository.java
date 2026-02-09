package staraxis.game.planet.def;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import staraxis.game.mod.ModManager;
import staraxis.game.mod.ModOrderRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * PlanetAssetRepository（行星资产仓库）
 *
 * 读取行星地表相关定义从assets/目录喵。
 * 管理地表区域类型、城市阶段、城市专精、资源类型等配置喵。
 */
public class PlanetAssetRepository {

    private final ObjectMapper objectMapper;

    private List<SurfaceRegionTypeDef> surfaceRegionTypes = List.of();
    private List<CityStageDef> cityStages = List.of();
    private List<CitySpecializationDef> citySpecializations = List.of();
    private List<ResourceTypeDef> resourceTypes = List.of();
    private List<NamePoolDef> namePools = List.of();

    // 快速查找映射
    private Map<String, SurfaceRegionTypeDef> surfaceRegionTypeMap = new HashMap<>();
    private Map<String, CityStageDef> cityStageMap = new HashMap<>();
    private Map<String, CitySpecializationDef> citySpecializationMap = new HashMap<>();
    private Map<String, ResourceTypeDef> resourceTypeMap = new HashMap<>();
    private Map<String, NamePoolDef> namePoolMap = new HashMap<>();

    public PlanetAssetRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 加载所有行星资产定义喵。
     */
    public void loadAll() {
        // base + mods（后读覆盖前读）喵
        surfaceRegionTypes = readMergedListByKey(
                "assets/planet/surface-region-types.json",
                "planet/surface-region-types.json",
                SurfaceRegionTypeDef[].class,
                def -> def == null ? null : def.typeId);

        cityStages = readMergedListByKey(
                "assets/planet/city-stages.json",
                "planet/city-stages.json",
                CityStageDef[].class,
                def -> def == null ? null : def.stageId);

        citySpecializations = readMergedListByKey(
                "assets/planet/city-specializations.json",
                "planet/city-specializations.json",
                CitySpecializationDef[].class,
                def -> def == null ? null : def.specializationId);

        resourceTypes = readMergedListByKey(
                "assets/planet/resource-types.json",
                "planet/resource-types.json",
                ResourceTypeDef[].class,
                def -> def == null ? null : def.resourceId);

        namePools = readMergedListByKey(
                "assets/planet/name-pools.json",
                "planet/name-pools.json",
                NamePoolDef[].class,
                def -> def == null ? null : def.poolId);

        // 构建快速查找映射
        buildLookupMaps();
    }

    /**
     * 构建快速查找映射喵。
     */
    private void buildLookupMaps() {
        surfaceRegionTypeMap.clear();
        for (SurfaceRegionTypeDef def : surfaceRegionTypes) {
            surfaceRegionTypeMap.put(def.typeId, def);
        }

        cityStageMap.clear();
        for (CityStageDef def : cityStages) {
            cityStageMap.put(def.stageId, def);
        }

        citySpecializationMap.clear();
        for (CitySpecializationDef def : citySpecializations) {
            citySpecializationMap.put(def.specializationId, def);
        }

        resourceTypeMap.clear();
        for (ResourceTypeDef def : resourceTypes) {
            resourceTypeMap.put(def.resourceId, def);
        }

        namePoolMap.clear();
        for (NamePoolDef def : namePools) {
            if (def != null && def.poolId != null && !def.poolId.isBlank()) {
                namePoolMap.put(def.poolId, def);
            }
        }
    }

    /**
     * 获取所有地表区域类型定义喵。
     *
     * @return 不可修改的地表区域类型列表喵。
     */
    public List<SurfaceRegionTypeDef> getSurfaceRegionTypes() {
        return Collections.unmodifiableList(surfaceRegionTypes);
    }

    /**
     * 获取所有城市阶段定义喵。
     *
     * @return 不可修改的城市阶段列表喵。
     */
    public List<CityStageDef> getCityStages() {
        return Collections.unmodifiableList(cityStages);
    }

    /**
     * 获取所有城市专精定义喵。
     *
     * @return 不可修改的城市专精列表喵。
     */
    public List<CitySpecializationDef> getCitySpecializations() {
        return Collections.unmodifiableList(citySpecializations);
    }

    /**
     * 获取所有资源类型定义喵。
     *
     * @return 不可修改的资源类型列表喵。
     */
    public List<ResourceTypeDef> getResourceTypes() {
        return Collections.unmodifiableList(resourceTypes);
    }

    /**
     * 根据ID获取地表区域类型定义喵。
     *
     * @param typeId 类型ID喵。
     * @return 地表区域类型定义，如果不存在返回null喵。
     */
    public SurfaceRegionTypeDef getSurfaceRegionType(String typeId) {
        return surfaceRegionTypeMap.get(typeId);
    }

    /**
     * 根据ID获取城市阶段定义喵。
     *
     * @param stageId 阶段ID喵。
     * @return 城市阶段定义，如果不存在返回null喵。
     */
    public CityStageDef getCityStage(String stageId) {
        return cityStageMap.get(stageId);
    }

    /**
     * 根据ID获取城市专精定义喵。
     *
     * @param specializationId 专精ID喵。
     * @return 城市专精定义，如果不存在返回null喵。
     */
    public CitySpecializationDef getCitySpecialization(String specializationId) {
        return citySpecializationMap.get(specializationId);
    }

    /**
     * 根据ID获取资源类型定义喵。
     *
     * @param resourceId 资源类型ID喵。
     * @return 资源类型定义，如果不存在返回null喵。
     */
    public ResourceTypeDef getResourceType(String resourceId) {
        return resourceTypeMap.get(resourceId);
    }

    /**
     * 获取所有命名池定义喵。
     *
     * @return 不可修改的命名池列表喵。
     */
    public List<NamePoolDef> getNamePools() {
        return Collections.unmodifiableList(namePools);
    }

    /**
     * 根据ID获取命名池定义喵。
     *
     * @param poolId 命名池ID喵。
     * @return 命名池定义，如果不存在返回null喵。
     */
    public NamePoolDef getNamePool(String poolId) {
        return namePoolMap.get(poolId);
    }

    /**
     * 获取默认城市阶段（通常为OUTPOST）喵。
     *
     * @return 默认城市阶段定义喵。
     */
    public CityStageDef getDefaultCityStage() {
        return getCityStage("OUTPOST");
    }

    /**
     * 获取默认城市专精喵。
     *
     * @return 默认城市专精定义，如果没有标记为默认的则返回第一个喵。
     */
    public CitySpecializationDef getDefaultCitySpecialization() {
        // 首先查找标记为默认的专精
        for (CitySpecializationDef def : citySpecializations) {
            if (def.isDefault) {
                return def;
            }
        }
        // 否则返回第一个专精
        if (!citySpecializations.isEmpty()) {
            return citySpecializations.get(0);
        }
        return null;
    }

    /**
     * 获取下一城市阶段定义喵。
     *
     * @param currentStageId 当前阶段ID喵。
     * @return 下一阶段定义，如果是最终阶段返回null喵。
     */
    public CityStageDef getNextCityStage(String currentStageId) {
        CityStageDef current = getCityStage(currentStageId);
        if (current == null) {
            return null;
        }
        String nextStageId = current.getNextStageId();
        if (nextStageId == null) {
            return null;
        }
        return getCityStage(nextStageId);
    }

    /**
     * 检查是否为有效的城市阶段ID喵。
     *
     * @param stageId 阶段ID喵。
     * @return 如果存在该阶段定义，返回true喵。
     */
    public boolean isValidCityStage(String stageId) {
        return cityStageMap.containsKey(stageId);
    }

    /**
     * 检查是否为有效的城市专精ID喵。
     *
     * @param specializationId 专精ID喵。
     * @return 如果存在该专精定义，返回true喵。
     */
    public boolean isValidCitySpecialization(String specializationId) {
        return citySpecializationMap.containsKey(specializationId);
    }

    /**
     * 检查是否为有效的资源类型ID喵。
     *
     * @param resourceId 资源类型ID喵。
     * @return 如果存在该资源类型定义，返回true喵。
     */
    public boolean isValidResourceType(String resourceId) {
        return resourceTypeMap.containsKey(resourceId);
    }

    /**
     * 读取基础配置与所有已启用 Mod 的配置并按 ID 覆盖合并喵。
     *
     * @param basePath        基础资产路径（如 "assets/planet/xxx.json"）喵。
     * @param modRelativePath Mod 内部相对路径（如 "planet/xxx.json"）喵。
     * @param arrayClazz      反序列化的数组类喵。
     * @param keyExtractor    从对象中提取唯一 ID 的函数喵。
     * @param <T>             元素类型喵。
     * @return 合并后的列表喵。
     */
    private <T> List<T> readMergedListByKey(
            String basePath,
            String modRelativePath,
            Class<?> arrayClazz,
            Function<T, String> keyExtractor) {

        Map<String, T> mergedMap = new java.util.LinkedHashMap<>();

        // 1. 加载本体配置喵
        List<T> baseList = readList(basePath, arrayClazz);
        for (T item : baseList) {
            String key = keyExtractor.apply(item);
            if (key != null) {
                mergedMap.put(key, item);
            }
        }

        // 2. 加载所有已启用 Mod 的配置并覆盖喵
        ModManager modMgr = new ModManager(new ModOrderRepository());
        List<String> modIds = modMgr.listModIdsOrderedAndEnabled();

        for (String modId : modIds) {
            String modPath = "gamedata/mods/" + modId + "/" + modRelativePath;
            List<T> modList = readList(modPath, arrayClazz);
            for (T item : modList) {
                String key = keyExtractor.apply(item);
                if (key != null) {
                    mergedMap.put(key, item);
                }
            }
        }

        return new ArrayList<>(mergedMap.values());
    }

    /**
     * 读取JSON文件为对象列表喵。
     *
     * @param path       文件路径喵。
     * @param arrayClazz 数组类喵。
     * @param <T>        元素类型喵。
     * @return 对象列表喵。
     */
    private <T> List<T> readList(String path, Class<?> arrayClazz) {
        try {
            File file = new File(path);
            if (!file.isFile()) {
                System.out.println("[WARN PlanetAssetRepository] File not found: " + path);
                return List.of();
            }
            Object arr = objectMapper.readValue(file, arrayClazz);
            if (!(arr instanceof Object[])) {
                return List.of();
            }
            Object[] a = (Object[]) arr;
            ArrayList<T> out = new ArrayList<>(a.length);
            for (Object o : a) {
                @SuppressWarnings("unchecked")
                T t = (T) o;
                out.add(t);
            }
            return out;
        } catch (Exception e) {
            System.out.println("[ERROR PlanetAssetRepository] Failed to read " + path + ": " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 验证所有定义是否有效喵。
     *
     * @return 如果所有定义都有效，返回true喵。
     */
    public boolean validateAll() {
        boolean valid = true;

        for (SurfaceRegionTypeDef def : surfaceRegionTypes) {
            if (def.typeId == null || def.typeId.isEmpty()) {
                System.out.println("[ERROR PlanetAssetRepository] Invalid SurfaceRegionTypeDef: missing typeId");
                valid = false;
            }
            if (def.namePoolId != null && !def.namePoolId.isBlank() && !namePoolMap.containsKey(def.namePoolId)) {
                System.out.println("[ERROR PlanetAssetRepository] SurfaceRegionTypeDef " + def.typeId
                        + " references non-existent namePoolId: " + def.namePoolId);
                valid = false;
            }
        }

        for (NamePoolDef def : namePools) {
            if (!def.isValid()) {
                System.out.println("[ERROR PlanetAssetRepository] Invalid NamePoolDef: " + def.poolId);
                valid = false;
            }
        }

        for (CityStageDef def : cityStages) {
            if (!def.isValid()) {
                System.out.println("[ERROR PlanetAssetRepository] Invalid CityStageDef: " + def.stageId);
                valid = false;
            }
        }

        for (CitySpecializationDef def : citySpecializations) {
            if (!def.isValid()) {
                System.out.println(
                        "[ERROR PlanetAssetRepository] Invalid CitySpecializationDef: " + def.specializationId);
                valid = false;
            }
        }

        for (ResourceTypeDef def : resourceTypes) {
            if (!def.isValid()) {
                System.out.println("[ERROR PlanetAssetRepository] Invalid ResourceTypeDef: " + def.resourceId);
                valid = false;
            }
        }

        return valid;
    }
}