package staraxis.game.planet;

import staraxis.game.planet.surface.SurfaceRegion;
import staraxis.game.planet.city.City;
import staraxis.game.planet.resource.ResourceSite;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PlanetSurface（行星地表组件）
 *
 * 行星地表综合管理组件，可作为 PlanetBody 的扩展喵。
 * 管理行星的地表区域、城市和资源点喵。
 */
public class PlanetSurface {

    /** 所属行星实体ID喵。 */
    public long planetEntityId;

    /** 地表区域列表喵。 */
    public List<SurfaceRegion> surfaceRegions;

    /** 城市列表喵。 */
    public List<City> cities;

    /** 资源点列表喵。 */
    public List<ResourceSite> resourceSites;

    /** 行星大小加成，体现"同等城市规模在不同大小星球上代表的开发程度不同"喵。 */
    public double planetSizeModifier;

    /** 总开发规模，用于计算地表灯光表现喵。 */
    public double totalDevelopmentScale;

    /** 行星首都城市ID喵。 */
    public long planetaryCapitalCityId;

    /** 上次地表区域生成种子，用于确定性生成喵。 */
    public long surfaceGenerationSeed;

    /**
     * 使用生成器初始化地表区划喵。
     *
     * @param type 行星类型定义喵。
     * @param repo 资产仓库喵。
     * @param seed 生成种子喵。
     */
    public void initializeSurface(staraxis.game.astro.def.PlanetTypeDef type,
            staraxis.game.planet.def.PlanetAssetRepository repo, long seed) {
        this.surfaceGenerationSeed = seed;
        this.surfaceRegions.clear();
        new staraxis.game.planet.surface.SurfaceGenerator().generate(this, type, repo);
    }

    /**
     * 默认构造函数喵。
     */
    public PlanetSurface() {
        this.surfaceRegions = new ArrayList<>();
        this.cities = new ArrayList<>();
        this.resourceSites = new ArrayList<>();
        this.planetSizeModifier = 1.0;
        this.totalDevelopmentScale = 0.0;
        this.planetaryCapitalCityId = 0;
    }

    /**
     * 构造函数，指定行星实体ID喵。
     *
     * @param planetEntityId 行星实体ID喵。
     */
    public PlanetSurface(long planetEntityId) {
        this();
        this.planetEntityId = planetEntityId;
    }

    /**
     * 添加地表区域喵。
     *
     * @param region 地表区域喵。
     */
    public void addSurfaceRegion(SurfaceRegion region) {
        surfaceRegions.add(region);
    }

    /**
     * 添加城市喵。
     *
     * @param city 城市喵。
     */
    public void addCity(City city) {
        cities.add(city);

        // 如果是第一个城市，自动设为行星首都喵
        if (planetaryCapitalCityId == 0) {
            planetaryCapitalCityId = city.cityId;
            city.isPlanetaryCapital = true;
        }

        // 将城市ID添加到所属区域的cityIds列表中喵
        SurfaceRegion region = getSurfaceRegion(city.regionId);
        if (region != null && !region.cityIds.contains(city.cityId)) {
            region.cityIds.add(city.cityId);
        }

        // 更新总开发规模喵
        updateTotalDevelopmentScale();
    }

    /**
     * 添加资源点喵。
     *
     * @param resourceSite 资源点喵。
     */
    public void addResourceSite(ResourceSite resourceSite) {
        resourceSites.add(resourceSite);
    }

    /**
     * 根据ID获取地表区域喵。
     *
     * @param regionId 区域ID喵。
     * @return 地表区域，如果不存在返回null喵。
     */
    public SurfaceRegion getSurfaceRegion(long regionId) {
        return surfaceRegions.stream()
                .filter(r -> r.regionId == regionId)
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据ID获取城市喵。
     *
     * @param cityId 城市ID喵。
     * @return 城市，如果不存在返回null喵。
     */
    public City getCity(long cityId) {
        return cities.stream()
                .filter(c -> c.cityId == cityId)
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据ID获取资源点喵。
     *
     * @param resourceSiteId 资源点ID喵。
     * @return 资源点，如果不存在返回null喵。
     */
    public ResourceSite getResourceSite(long resourceSiteId) {
        return resourceSites.stream()
                .filter(r -> r.resourceSiteId == resourceSiteId)
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取行星首都城市喵。
     *
     * @return 首都城市，如果不存在返回null喵。
     */
    public City getPlanetaryCapitalCity() {
        if (planetaryCapitalCityId == 0) {
            return null;
        }
        return getCity(planetaryCapitalCityId);
    }

    /**
     * 获取指定区域的所有城市喵。
     *
     * @param regionId 区域ID喵。
     * @return 该区域的城市列表喵。
     */
    public List<City> getCitiesInRegion(long regionId) {
        return cities.stream()
                .filter(c -> c.regionId == regionId)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定区域的所有资源点喵。
     *
     * @param regionId 区域ID喵。
     * @return 该区域的资源点列表喵。
     */
    public List<ResourceSite> getResourceSitesInRegion(long regionId) {
        return resourceSites.stream()
                .filter(r -> r.regionId == regionId)
                .collect(Collectors.toList());
    }

    /**
     * 获取城市已开发的资源点喵。
     *
     * @param cityId 城市ID喵。
     * @return 该城市已开发的资源点列表喵。
     */
    public List<ResourceSite> getDevelopedResourceSitesByCity(long cityId) {
        City city = getCity(cityId);
        if (city == null || city.developedResourceSiteIds == null) {
            return new ArrayList<>();
        }

        return city.developedResourceSiteIds.stream()
                .map(this::getResourceSite)
                .filter(r -> r != null && r.isDeveloped())
                .collect(Collectors.toList());
    }

    /**
     * 获取指定区域未发现的资源点喵。
     *
     * @param regionId 区域ID喵。
     * @return 该区域未发现的资源点列表喵。
     */
    public List<ResourceSite> getUndiscoveredResourceSitesInRegion(long regionId) {
        return resourceSites.stream()
                .filter(r -> r.regionId == regionId && "UNDISCOVERED".equals(r.status))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定区域已发现但未开发的资源点喵。
     *
     * @param regionId 区域ID喵。
     * @return 该区域已发现但未开发的资源点列表喵。
     */
    public List<ResourceSite> getDiscoveredUndevelopedResourceSitesInRegion(long regionId) {
        return resourceSites.stream()
                .filter(r -> r.regionId == regionId && "DISCOVERED".equals(r.status) && r.developedByCityId == 0)
                .collect(Collectors.toList());
    }

    /**
     * 更新总开发规模喵。
     * 根据文档公式：totalDevelopmentScale = Σ(city_i.scale * planetSizeModifier)喵。
     */
    public void updateTotalDevelopmentScale() {
        totalDevelopmentScale = cities.stream()
                .mapToDouble(c -> c.cityScale * planetSizeModifier)
                .sum();

        // 更新各区域的开发比例喵
        updateRegionDevelopedSpaceRatios();
    }

    /**
     * 更新所有地表区域的已开发空间比例喵。
     */
    public void updateRegionDevelopedSpaceRatios() {
        for (SurfaceRegion region : surfaceRegions) {
            region.calculateDevelopedSpaceRatio(this);
        }
    }

    /**
     * 计算行星地表总面积（相对比例）喵。
     * 假设所有地表区域的surfacePercentage之和为1.0喵。
     *
     * @return 地表总面积比例（总是1.0）喵。
     */
    public double getTotalSurfaceArea() {
        return surfaceRegions.stream()
                .mapToDouble(r -> r.surfacePercentage)
                .sum();
    }

    /**
     * 计算总已开发空间比例喵。
     *
     * @return 所有区域已开发空间占总地表面积的比例喵。
     */
    public double getTotalDevelopedSpaceRatio() {
        double totalDeveloped = 0.0;
        for (SurfaceRegion region : surfaceRegions) {
            totalDeveloped += region.calculateDevelopedSpaceRatio() * region.surfacePercentage;
        }
        return totalDeveloped;
    }

    /**
     * 检查行星是否有剩余可开发空间喵。
     *
     * @return 如果有任何区域有剩余可开发空间，返回true喵。
     */
    public boolean hasRemainingDevelopableSpace() {
        return surfaceRegions.stream()
                .anyMatch(SurfaceRegion::hasRemainingDevelopableSpace);
    }

    /**
     * 获取适合建立新城市的区域列表喵。
     *
     * @return 有剩余可开发空间的区域列表喵。
     */
    public List<SurfaceRegion> getRegionsSuitableForNewCity() {
        return surfaceRegions.stream()
                .filter(SurfaceRegion::hasRemainingDevelopableSpace)
                .collect(Collectors.toList());
    }

    /**
     * 获取行星总人口喵。
     *
     * @return 所有城市人口之和喵。
     */
    public long getTotalPopulation() {
        return cities.stream()
                .mapToLong(c -> c.population)
                .sum();
    }

    /**
     * 获取行星总人口容量喵。
     *
     * @return 所有城市人口容量之和喵。
     */
    public long getTotalPopulationCap() {
        return cities.stream()
                .mapToLong(c -> c.populationCap)
                .sum();
    }

    /**
     * 获取行星总体吸引力（平均）喵。
     *
     * @return 城市吸引力的加权平均值喵。
     */
    public double getAverageAttractiveness() {
        if (cities.isEmpty()) {
            return 0.0;
        }
        double totalWeightedAttractiveness = 0.0;
        long totalPopulation = 0;

        for (City city : cities) {
            totalWeightedAttractiveness += city.attractiveness * city.population;
            totalPopulation += city.population;
        }

        return totalPopulation > 0 ? totalWeightedAttractiveness / totalPopulation : 0.0;
    }
}