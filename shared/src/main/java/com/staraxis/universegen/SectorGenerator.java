package com.staraxis.universegen;

import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.universegen.model.Planet;
import com.staraxis.universegen.model.Sector;
import com.staraxis.universegen.model.Star;
import com.staraxis.universegen.model.StarSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * 星区生成器：为每个 sector 生成类型（star-system/deep_space/nebula）以及可选的恒星系（Star System）。
 *
 * 当前实现目标：最小可用但“非占位”
 * - star_system：支持 1~3 恒星
 * - 每颗恒星可生成 0~N 行星（planetComplexity 暂不接入，这里用简单分布）
 */
public class SectorGenerator {

    public Sector generate(long sectorId, int q, int r, UniverseGenConfig cfg, SplittableRandom rng) {
        String sectorType = sampleSectorType(cfg, rng);

        StarSystem starSystem = null;
        if (com.staraxis.game.shared.net.worldgen.snapshot.SectorTypes.STAR_SYSTEM.equals(sectorType)) {
            // 星系星区：生成恒星系（当前实现为历史版本的“非占位”，后续 015 会改为占位符）
            starSystem = generateStarSystem((int) sectorId, rng);
        }

        com.staraxis.game.shared.net.worldgen.snapshot.HexCoordSnapshot coord = new com.staraxis.game.shared.net.worldgen.snapshot.HexCoordSnapshot();
        coord.setX(q);
        coord.setY(r);
        coord.setZ(-q - r);

        return new Sector(sectorId, coord, sectorType, starSystem);
    }

    private String sampleSectorType(UniverseGenConfig cfg, SplittableRandom rng) {
        // 使用 cfg.starToDeepSpaceRatio 作为 star-system vs deep_space 的比例。
        // nebula 先给一个很小的固定概率（后续可扩展为配置项）。
        double pNebula = 0.05;
        double pGalaxy = clamp01(cfg.getStarToDeepSpaceRatio());

        // 归一化：确保三者和为 1
        double remaining = Math.max(0.0, 1.0 - pNebula);
        if (pGalaxy > remaining) {
            pGalaxy = remaining;
        }
        double pDeep = remaining - pGalaxy;

        double roll = rng.nextDouble();
        if (roll < pGalaxy) {
            return com.staraxis.game.shared.net.worldgen.snapshot.SectorTypes.STAR_SYSTEM;
        }
        if (roll < pGalaxy + pNebula) {
            return "nebula";
        }
        return "deep_space";
    }

    private StarSystem generateStarSystem(int sectorId, SplittableRandom rng) {
        // T018: 实现内容生成占位符。StarSystem 仅含唯一 ID（name），不生成复杂内容。
        String systemName = "SYS-" + sectorId + "-" + Long.toUnsignedString(rng.nextLong(), 36);

        // StarSystem record 要求至少有一颗星，因此生成一个占位符 Star。
        List<Star> stars = List.of(generatePlaceholderStar(systemName, rng));

        return new StarSystem(systemName, stars);
    }

    private Star generatePlaceholderStar(String systemName, SplittableRandom rng) {
        String name = systemName + "-STAR-0";
        String type = "placeholder"; // Simple type for the placeholder
        double massKg = 0.0; // No real mass
        List<Planet> planets = new ArrayList<>(); // Empty planet list
        return new Star(name, type, massKg, planets);
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
