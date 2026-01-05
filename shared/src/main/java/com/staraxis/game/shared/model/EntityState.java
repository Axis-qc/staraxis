package com.staraxis.game.shared.model;

import java.util.Map;

/**
 * 实体状态 (Individual Entity State)
 */
public class EntityState {

    public long id; // 唯一标识符
    public String type; // 实体类别 // 如 SHIP, PLANET
    public Vector2 position; // 逻辑坐标
    public float rotation; // 逻辑朝向
    public float health; // 当前生命值
    public Map<String, Object> metadata; // 扩展属性

    public EntityState() {
    }
}
