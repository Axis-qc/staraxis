package com.staraxis.game.shared.world.stellar;

import java.io.Serializable;

/**
 * 行星（Planet）。
 *
 * 作用（Purpose）：描述一个行星实体。 依赖（Dependencies）：仅 Java 标准库。 对外接口（Public
 * API）：getId/setId/getPlanetTypeId/setPlanetTypeId/getOrbitIndex/setOrbitIndex。
 */
public class Planet implements Serializable {

    private String id; // id（在所属 Star 内唯一标识）
    private String planetTypeId; // planetTypeId（行星类型标识，数据驱动）
    private Integer orbitIndex; // orbitIndex（展示/排序用；可为空；必须 >= 0）

    public Planet() {
    }

    public Planet(String id, String planetTypeId, Integer orbitIndex) {
        this.id = id;
        this.planetTypeId = planetTypeId;
        setOrbitIndex(orbitIndex);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlanetTypeId() {
        return planetTypeId;
    }

    public void setPlanetTypeId(String planetTypeId) {
        this.planetTypeId = planetTypeId;
    }

    public Integer getOrbitIndex() {
        return orbitIndex;
    }

    public void setOrbitIndex(Integer orbitIndex) {
        if (orbitIndex != null && orbitIndex < 0) {
            throw new IllegalArgumentException("orbitIndex（展示轨道序号）必须 >= 0");
        }
        this.orbitIndex = orbitIndex;
    }
}
