package com.staraxis.game.client.render;

import java.util.HashMap;

import com.staraxis.game.shared.model.EntityState;
import com.staraxis.game.shared.model.GameState;
import com.staraxis.game.shared.model.Vector2;

/**
 * 线性插值器 (Linear Interpolator)
 *
 * 使用的接口: GameState, EntityState 提供的接口: 根据两个逻辑帧之间的渲染时间比例，计算平滑的显示坐标 (SC-003)
 */
public class LinearInterpolator {

    /**
     * 对游戏世界进行插值 (Interpolate World State)
     *
     * @param alpha 插值比例 (0.0 到 1.0)
     */
    public GameState interpolate(GameState from, GameState to, float alpha) {
        if (from == null || to == null) {
            return to;
        }

        GameState result = new GameState();
        result.tick = to.tick;
        result.timestamp = to.timestamp;
        result.entities = new HashMap<>();

        for (Long id : to.entities.keySet()) {
            EntityState end = to.entities.get(id);
            EntityState start = from.entities.get(id);

            if (start != null && end != null) {
                EntityState interpolated = new EntityState();
                interpolated.id = id;
                interpolated.type = end.type;

                // 坐标插值 (Position Interpolation)
                if (start.position != null && end.position != null) {
                    float x = start.position.x + (end.position.x - start.position.x) * alpha;
                    float y = start.position.y + (end.position.y - start.position.y) * alpha;
                    interpolated.position = new Vector2(x, y);
                }

                // 旋转插值 (Rotation Interpolation)
                interpolated.rotation = start.rotation + (end.rotation - start.rotation) * alpha;
                interpolated.health = end.health;

                result.entities.put(id, interpolated);
            } else {
                // 如果 start 不存在（新生成的实体），直接使用 end
                result.entities.put(id, end);
            }
        }

        return result;
    }
}
