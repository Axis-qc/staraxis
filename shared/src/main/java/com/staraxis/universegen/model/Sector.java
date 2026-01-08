package com.staraxis.universegen.model;

/**
 * 星区（sector）。
 * 
 * sectorType：
 * - galaxy：星系星区，包含一个 star_system
 * - nebula：星云
 * - deep_space：深空
 */
public record Sector(int id, String sectorType, StarSystem starSystem) {
}
