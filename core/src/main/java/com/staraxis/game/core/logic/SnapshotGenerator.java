package com.staraxis.game.core.logic;

import java.util.HashMap;
import java.util.Map;

import com.staraxis.game.shared.model.EntityState;
import com.staraxis.game.shared.model.GameState;
import com.staraxis.game.shared.model.WorldMetadata;

/**
 * 快照生成器 (Snapshot Generator)
 *
 * 使用的接口: GameState 提供的接口: 为新连接的客户端生成当前世界状态的完整快照
 */
public class SnapshotGenerator {

    /**
     * 生成完整快照 (Generate Full Snapshot)
     */
    public GameState generate(GameState currentState) {
        GameState snapshot = new GameState();
        snapshot.tick = currentState.tick;
        snapshot.timestamp = currentState.timestamp;

        // 深拷贝元数据
        if (currentState.worldData != null) {
            snapshot.worldData = new WorldMetadata();
            snapshot.worldData.galaxySize = currentState.worldData.galaxySize;
            snapshot.worldData.totalResources = currentState.worldData.totalResources;
        }

        // 深拷贝实体状态
        snapshot.entities = new HashMap<>();
        for (Map.Entry<Long, EntityState> entry : currentState.entities.entrySet()) {
            EntityState original = entry.getValue();
            EntityState copy = new EntityState();
            copy.id = original.id;
            copy.type = original.type;
            if (original.position != null) {
                copy.position = new com.staraxis.game.shared.model.Vector2(original.position.x, original.position.y);
            }
            copy.rotation = original.rotation;
            copy.health = original.health;
            if (original.metadata != null) {
                copy.metadata = new HashMap<>(original.metadata);
            }
            snapshot.entities.put(entry.getKey(), copy);
        }

        return snapshot;
    }
}
