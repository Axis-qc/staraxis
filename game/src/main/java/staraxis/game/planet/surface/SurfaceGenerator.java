package staraxis.game.planet.surface;

import staraxis.game.astro.def.PlanetTypeDef;
import staraxis.game.planet.PlanetSurface;
import staraxis.game.planet.def.NamePoolDef;
import staraxis.game.planet.def.PlanetAssetRepository;
import staraxis.game.planet.def.SurfaceRegionTypeDef;

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

        // 1. 计算总权重并准备候选类型喵
        double totalWeight = 0;
        List<String> typeIds = new ArrayList<>();
        List<Double> cumulativeWeights = new ArrayList<>();

        for (Map.Entry<String, Double> entry : type.surfaceRegionWeights.entrySet()) {
            totalWeight += entry.getValue();
            typeIds.add(entry.getKey());
            cumulativeWeights.add(totalWeight);
        }

        if (totalWeight <= 0)
            return;

        // 2. 确定区域数量（暂定 3-6 个，未来可随行星大小/质量缩放）喵
        int regionCount = 3 + rng.nextInt(4);

        // 3. 分配面积份额：先随机分配原始份额，再归一化到 1.0 喵
        double[] shares = new double[regionCount];
        double totalShare = 0;
        for (int i = 0; i < regionCount; i++) {
            // 0.5 ~ 1.5 的随机权重，保证区划面积不会差距过大喵
            shares[i] = 0.5 + rng.nextDouble();
            totalShare += shares[i];
        }

        // 4. 按权重抽取类型并生成区域喵
        for (int i = 0; i < regionCount; i++) {
            double r = rng.nextDouble() * totalWeight;
            String selectedTypeId = typeIds.get(0);
            for (int j = 0; j < cumulativeWeights.size(); j++) {
                if (r <= cumulativeWeights.get(j)) {
                    selectedTypeId = typeIds.get(j);
                    break;
                }
            }

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

            surface.addSurfaceRegion(region);
        }
    }
}
