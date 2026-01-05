package com.staraxis.game.shared.model;

import java.util.Map;

/**
 * 游戏状态同步包 (Game State Snapshot)
 *
 * 使用的接口: 无 提供的接口: 由服务端分发，包含当前 Tick 的完整世界快照
 */
public class GameState {

    public long tick; // 当前逻辑帧
    public long timestamp; // 服务端产生时间戳
    public long simulationTime; // 累计模拟时间 (ms)
    public Map<Long, EntityState> entities; // 实体列表
    public WorldMetadata worldData; // 世界数据

    public GameState() {
    }
}
