package com.staraxis.universegen;

import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.universegen.util.RandomUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.SplittableRandom;

/**
 * 星区内容分配器：
 * - 输入：全部星区集合 + 预设占用表
 * - 输出：每个星区的最终 contentTypeId
 *
 * 规则（对齐 015 spec）：
 * - 预设占用的星区不可被覆盖
 * - 对剩余星区按比例分配（配额 + score 排序），且确定性（seed + HexCoord 派生随机，与遍历顺序/并行无关）
 * - 若剩余不足：按顺序截断（star-system → nebula → deep_space）
 */
public final class SectorContentAllocator {

    private static final Logger LOG = LoggerFactory.getLogger(SectorContentAllocator.class);

    private SectorContentAllocator() {
    }

    /**
     * @param cfg 配置（含 contentRatios）
     * @param sectorIds 所有 sectorId（建议已稳定排序）
     * @param presetOccupancy 预设占用（sectorId -> typeId）
     * @return 分配结果（sectorId -> typeId），包含预设占用与剩余分配
     */
    public static Map<Long, String> allocate(UniverseGenConfig cfg,
                                             List<Long> sectorIds,
                                             Map<Long, String> presetOccupancy) {
        Map<Long, String> result = new HashMap<>();
        if (presetOccupancy != null) {
            result.putAll(presetOccupancy);
        }

        // T015：计算剩余星区（排除 preset 占用），并保持稳定排序
        List<Long> remaining = new ArrayList<>();
        for (Long id : sectorIds) {
            if (presetOccupancy != null && presetOccupancy.containsKey(id)) {
                continue;
            }
            remaining.add(id);
        }
        remaining.sort(Comparator.naturalOrder());

        if (remaining.isEmpty()) {
            return result;
        }

        // T016：计算配额（配额 + score 排序）
        Map<String, Double> ratios = cfg.getContentRatios();
        Map<String, Integer> quotas = computeQuotas(ratios, remaining.size());

        // 为每个剩余星区计算 score（确定性）
        class Scored {
            final long sectorId;
            final double score;

            Scored(long sectorId, double score) {
                this.sectorId = sectorId;
                this.score = score;
            }
        }

        List<Scored> scored = new ArrayList<>(remaining.size());
        for (long sectorId : remaining) {
            int q = (int) (sectorId >> 32);
            int r = (int) (sectorId & 0xffffffffL);
            SplittableRandom rng = RandomUtil.deriveFromHexCoord(cfg.getSeed(), q, r);
            scored.add(new Scored(sectorId, rng.nextDouble()));
        }
        scored.sort(Comparator.comparingDouble(s -> s.score));

        // 固定顺序（用于截断/可解释性）：star-system → nebula → deep_space
        List<String> order = List.of("star-system", "nebula", "deep_space");

        int idx = 0;
        for (String typeId : order) {
            int quota = quotas.getOrDefault(typeId, 0);
            int assignedCount = 0;
            for (int i = 0; i < quota && idx < scored.size(); i++) {
                result.put(scored.get(idx).sectorId, typeId);
                idx++;
                assignedCount++;
            }
            // T017: 增加日志/调试提示
            if (assignedCount < quota) {
                LOG.warn("Sector content allocation truncated for type '{}': wanted {}, got {}. Not enough remaining sectors.",
                        typeId, quota, assignedCount);
            }
        }

        // 若还没填满，则按 deep_space 填充
        while (idx < scored.size()) {
            result.put(scored.get(idx).sectorId, "deep_space");
            idx++;
        }

        return result;
    }

    private static Map<String, Integer> computeQuotas(Map<String, Double> ratios, int remainingCount) {
        Map<String, Integer> quotas = new HashMap<>();
        if (ratios == null || ratios.isEmpty()) {
            quotas.put("deep_space", remainingCount);
            return quotas;
        }

        // 先按 round 分配
        int sum = 0;
        List<String> keys = new ArrayList<>(ratios.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            int q = (int) Math.round(ratios.getOrDefault(k, 0.0) * remainingCount);
            quotas.put(k, q);
            sum += q;
        }

        // 调整总和到 remainingCount
        int diff = remainingCount - sum;
        if (diff != 0 && !keys.isEmpty()) {
            String adjustKey = quotas.containsKey("deep_space") ? "deep_space" : keys.get(keys.size() - 1);
            quotas.put(adjustKey, quotas.getOrDefault(adjustKey, 0) + diff);
        }

        // 兜底：避免负数
        for (Map.Entry<String, Integer> e : quotas.entrySet()) {
            if (e.getValue() < 0) {
                e.setValue(0);
            }
        }

        return quotas;
    }
}
