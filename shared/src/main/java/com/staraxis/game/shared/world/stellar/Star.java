package com.staraxis.game.shared.world.stellar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 恒星（Star）。
 *
 * 作用（Purpose）：描述一个恒星实体及其所属行星集合。 依赖（Dependencies）：仅 Java 标准库。 对外接口（Public
 * API）：getId/setId/getStarTypeId/setStarTypeId/getPlanets/setPlanets。
 */
public class Star implements Serializable {

    private String id; // id（在所属 StarSystem 内唯一标识）
    private String starTypeId; // starTypeId（恒星类型标识，数据驱动）
    private List<Planet> planets; // planets（归属该恒星的行星列表，允许为空）

    public Star() {
        this.planets = new ArrayList<>();
    }

    public Star(String id, String starTypeId, List<Planet> planets) {
        this.id = id;
        this.starTypeId = starTypeId;
        setPlanets(planets);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStarTypeId() {
        return starTypeId;
    }

    public void setStarTypeId(String starTypeId) {
        this.starTypeId = starTypeId;
    }

    public List<Planet> getPlanets() {
        return Collections.unmodifiableList(planets);
    }

    public void setPlanets(List<Planet> planets) {
        if (planets == null) {
            this.planets = new ArrayList<>();
            return;
        }
        this.planets = new ArrayList<>(planets);
    }
}
