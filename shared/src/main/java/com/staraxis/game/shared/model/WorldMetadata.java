package com.staraxis.game.shared.model;

/**
 * 世界元数据 (World Metadata)
 *
 * 使用的接口: 无 提供的接口: 包含宇宙规模、全局资源等非实体信息
 */
public class WorldMetadata {

    public int galaxySize; // 星系规模
    public long totalResources; // 全局资源总额

    public WorldMetadata() {
    }
}
