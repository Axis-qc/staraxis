package com.staraxis.game.client.render;

import com.staraxis.game.shared.model.EntityState;

/**
 * 实体渲染代理 (Entity Renderer Proxy)
 *
 * 使用的接口: EntityState 提供的接口: 负责将服务端逻辑实体转化为客户端可视化对象
 */
public class EntityRendererProxy {

    /**
     * 渲染实体 (Render Entity) 这里目前仅作为占位，实际会调用 LibGDX 的渲染方法
     */
    public void render(EntityState entity) {
        if (entity == null) {
            return;
        }

        // 示例：在控制台输出位置（后续替换为图形渲染）
        // System.out.println("Rendering " + entity.type + " at " + entity.position);
    }
}
