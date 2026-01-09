package com.staraxis.universegen.model;

import com.staraxis.game.shared.net.worldgen.snapshot.HexCoordSnapshot;

/**
 * 星区（Sector）。
 * 
 * <p>术语对齐（见 术语对齐.md）：
 * <ul>
 *   <li>star-system：星系星区（会生成一个恒星系 Star System）</li>
 *   <li>nebula：星云</li>
 *   <li>deep_space：深空</li>
 * </ul>
 *
 * <p>注意：本模块历史代码曾使用 "galaxy" 作为星系星区类型；已统一迁移为 "star-system"。
 */
public record Sector(long id, HexCoordSnapshot hexCoord, String sectorType, StarSystem starSystem) {
}
