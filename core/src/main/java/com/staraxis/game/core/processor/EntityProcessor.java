package com.staraxis.game.core.processor;

import com.staraxis.game.shared.model.GameState;
import com.staraxis.game.shared.model.EntityState;
import com.staraxis.game.shared.model.Vector2;

/**
 * 实体处理器接口 (Entity Processor Interface)
 *
 * 提供的接口: 供 GameServer 调用以更新特定逻辑
 */
public interface EntityProcessor /* 实体处理器 */ {

    void process /* 处理 */(GameState state, float deltaTime);
}

/**
 * 位置更新处理器 (Movement Processor)
 */
class MovementProcessor /* 移动处理器 */ implements EntityProcessor {

    @Override
    public void process /* 处理 */(GameState state, float deltaTime) {
        for (EntityState entity : state.entities.values()) {
            if (entity.position != null) {
                // 简单的位置更新模拟，后续通过指令驱动
                // entity.position.x += 0.1f * deltaTime;
            }
        }
    }
}
