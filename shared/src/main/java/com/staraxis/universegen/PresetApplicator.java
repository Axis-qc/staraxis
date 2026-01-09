package com.staraxis.universegen;

import com.staraxis.game.shared.net.worldgen.snapshot.HexCoordSnapshot;
import com.staraxis.universegen.config.GalaxyPreset;
import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.universegen.util.RandomUtil;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * 预设应用器：在生成顺序的第 1 步，将 GalaxyPreset 应用于星区占用。
 *
 * <p>规则：
 * - 冲突处理：后来者覆盖（按 loadOrder 升序应用，后写覆盖前写）
 * - random-hex 必须可复现：使用 seed + presetId 派生 RNG 后再挑选
 */
public final class PresetApplicator {

    private PresetApplicator() {
    }

    /**
     * 返回占用表：key=sectorId，value=contentTypeId。
     */
    public static Map<Long, String> applyPresets(UniverseGenConfig cfg, List<Long> sectorIds) {
        Map<Long, String> occupied = new HashMap<>();
        List<GalaxyPreset> presets = cfg.getGalaxyPresets();
        if (presets == null || presets.isEmpty()) {
            return occupied;
        }

        presets.stream()
                .sorted(Comparator.comparingInt(GalaxyPreset::getLoadOrder))
                .forEach(p -> applySinglePreset(cfg, sectorIds, occupied, p));

        return occupied;
    }

    private static void applySinglePreset(UniverseGenConfig cfg,
                                          List<Long> sectorIds,
                                          Map<Long, String> occupied,
                                          GalaxyPreset preset) {
        if (preset == null) return;
        String placement = preset.getPlacementType();
        String contentTypeId = preset.getContentTypeId();
        if (placement == null || contentTypeId == null) {
            return;
        }

        if ("fixed-hex".equals(placement)) {
            List<HexCoordSnapshot> coords = preset.getFixedHexCoords();
            if (coords == null) return;
            for (HexCoordSnapshot c : coords) {
                long id = SectorLocatorService.packAxialToSectorId(c.getX(), c.getY());
                if (sectorIds.contains(id)) {
                    occupied.put(id, contentTypeId);
                }
            }
            return;
        }

        if ("random-hex".equals(placement)) {
            int count = Math.max(0, preset.getRandomCount());
            if (count == 0) return;

            // 使用 presetId 的 hash 作为 salt，保证不同 preset 派生不同 RNG。
            long salt = preset.getPresetId() != null ? preset.getPresetId().hashCode() : 0L;
            SplittableRandom rng = RandomUtil.derive(cfg.getSeed(), salt);

            // 简单策略：从 sectorIds 中随机抽取 count 个（允许与已占用冲突，后续覆盖规则由外层保证）
            for (int i = 0; i < count; i++) {
                if (sectorIds.isEmpty()) break;
                long picked = sectorIds.get(rng.nextInt(sectorIds.size()));
                occupied.put(picked, contentTypeId);
            }
        }
    }
}
