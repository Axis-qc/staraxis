package staraxis.game.planet.surface;

import staraxis.game.astro.def.PlanetTypeDef;
import staraxis.game.planet.PlanetSurface;
import staraxis.game.planet.def.NamePoolDef;
import staraxis.game.planet.def.PlanetAssetRepository;
import staraxis.game.planet.def.SurfaceRegionTypeDef;
import staraxis.game.util.WeightedRandomUtil;

import java.util.*;

/**
 * SurfaceGenerator（地表生成器）
 *
 * description: 根据行星类型定义的权重，确定性地生成地表区划（SurfaceRegion）喵。
 * 遵循数据驱动与确定性模拟原则，所有随机过程必须基于传入的种子喵。
 *
 * usage: 调用 generate(surface, type, repo) 为新发现/创建的行星生成地表数据喵。
 *
 * important_notes:
 * 1. 相同 seed + PlanetTypeDef 必须产生相同结果喵。
 * 2. 区域总面积归一化为 1.0 (100%) 喵。
 * 3. 依赖 PlanetAssetRepository 提供的地表类型权重与命名池喵。
 */
public class SurfaceGenerator {

    /**
     * 生成次级地貌（SubSurfaceFeature）列表喵。
     * 根据区域定义中的次级地貌配置生成地貌，确保百分比总和为1.0喵。
     *
     * @param regionDef 地表区域类型定义喵。
     * @param rng       随机数生成器喵。
     * @return 次级地貌列表喵。
     */
    private List<SubSurfaceFeature> generateSubSurfaceFeatures(SurfaceRegionTypeDef regionDef, Random rng) {
        List<SubSurfaceFeature> features = new ArrayList<>();

        // 如果没有配置次级地貌，创建默认地貌喵
        if (regionDef.subFeatures == null || regionDef.subFeatures.length == 0) {
            SubSurfaceFeature defaultFeature = new SubSurfaceFeature();
            defaultFeature.featureType = "PLAINS"; // 默认地貌类型为平原喵
            defaultFeature.percentageOfRegion = 1.0;
            defaultFeature.resourceTendencies = new HashMap<>();
            features.add(defaultFeature);
            return features;
        }

        // 为每个地貌类型生成占比权重喵
        double totalWeight = 0.0;
        double[] weights = new double[regionDef.subFeatures.length];

        for (int i = 0; i < regionDef.subFeatures.length; i++) {
            SurfaceRegionTypeDef.SubFeatureDef subDef = regionDef.subFeatures[i];
            if (subDef.percentageRange != null && subDef.percentageRange.length >= 2) {
                double min = subDef.percentageRange[0];
                double max = subDef.percentageRange[1];
                // 确定性随机生成初始权重喵
                weights[i] = min + rng.nextDouble() * (max - min);
            } else {
                // 默认范围 0.1 - 0.3 喵
                weights[i] = 0.1 + rng.nextDouble() * 0.2;
            }
            totalWeight += weights[i];
        }

        // 归一化百分比，确保总和严格等于 1.0 喵
        if (totalWeight > 0) {
            for (int i = 0; i < weights.length; i++) {
                weights[i] /= totalWeight;
            }
        } else {
            // 防御性处理：如果总权重为0，平分比例喵
            for (int i = 0; i < weights.length; i++) {
                weights[i] = 1.0 / weights.length;
            }
        }

        // 创建次级地貌对象并赋值喵
        for (int i = 0; i < regionDef.subFeatures.length; i++) {
            SurfaceRegionTypeDef.SubFeatureDef subDef = regionDef.subFeatures[i];
            SubSurfaceFeature feature = new SubSurfaceFeature();
            feature.featureType = subDef.featureTypeId;
            feature.percentageOfRegion = weights[i];
            feature.resourceTendencies = subDef.resourceTendencies != null ? new HashMap<>(subDef.resourceTendencies)
                    : new HashMap<>();
            features.add(feature);
        }

        return features;
    }

    /**
     * 为指定行星表面生成区划喵。
     *
     * @param surface 行星表面组件喵。
     * @param type    行星类型定义喵。
     * @param repo    资产仓库喵。
     */
    public void generate(PlanetSurface surface, PlanetTypeDef type, PlanetAssetRepository repo) {
        if (type == null || type.surfaceRegionWeights == null || type.surfaceRegionWeights.isEmpty()) {
            return;
        }

        // 使用行星自带的生成种子确保确定性喵
        Random rng = new Random(surface.surfaceGenerationSeed);

        // 1. 确定区域数量（暂定 3-6 个，未来可随行星大小/质量缩放）喵
        int regionCount = 3 + rng.nextInt(4);

        // 2. 分配面积份额：先随机分配原始份额，再归一化到 1.0 喵
        double[] shares = new double[regionCount];
        double totalShare = 0;
        for (int i = 0; i < regionCount; i++) {
            // 0.5 ~ 1.5 的随机权重，保证区划面积不会差距过大喵
            shares[i] = 0.5 + rng.nextDouble();
            totalShare += shares[i];
        }

        // 3. 按权重抽取类型并生成区域喵
        for (int i = 0; i < regionCount; i++) {
            String selectedTypeId = WeightedRandomUtil.weightedKey(type.surfaceRegionWeights, rng);

            SurfaceRegionTypeDef regionDef = repo.getSurfaceRegionType(selectedTypeId);
            if (regionDef == null)
                continue;

            SurfaceRegion region = new SurfaceRegion();
            // 使用通用的确定性 ID 混合逻辑，消除区域数量上限隐患喵
            region.regionId = SurfaceNamingUtils.mixRegionId(surface.planetEntityId, i);
            region.planetEntityId = surface.planetEntityId;
            region.regionType = regionDef.typeId;
            region.surfacePercentage = shares[i] / totalShare;

            // 确定性命名：优先使用命名池，否则回退喵
            NamePoolDef namePool = repo.getNamePool(regionDef.namePoolId);
            if (namePool != null) {
                region.name = SurfaceNamingUtils.generateName(rng, namePool);
            } else {
                region.name = regionDef.getDisplayName() + " " + (i + 1);
            }

            // 初始开发空间：从配置范围中根据权重随机喵
            if (regionDef.developableSpaceRatioRange != null && regionDef.developableSpaceRatioRange.length >= 2) {
                double min = regionDef.developableSpaceRatioRange[0];
                double max = regionDef.developableSpaceRatioRange[1];
                region.developableSpaceRatio = min + rng.nextDouble() * (max - min);
            } else {
                region.developableSpaceRatio = 0.5; // 默认 50% 可开发喵
            }

            // 生成次级地貌（SubSurfaceFeature）喵
            region.subFeatures = generateSubSurfaceFeatures(regionDef, rng);

            surface.addSurfaceRegion(region);
        }
    }
}
