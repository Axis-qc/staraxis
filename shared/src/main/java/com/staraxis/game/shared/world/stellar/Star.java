package com.staraxis.game.shared.world.stellar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.staraxis.game.shared.world.astronomical.AstronomicalUnit;
import com.staraxis.game.shared.world.stellar.orbit.OrbitCenterRef;

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

    private OrbitCenterRef orbitCenterRef; // orbitCenterRef（恒星围绕的中心；双星场景可指向共同质心）
    private AstronomicalUnit radius; // radius（恒星半径，以 AU 为单位；新单位系统）

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

    public OrbitCenterRef getOrbitCenterRef() {
        return orbitCenterRef;
    }

    public void setOrbitCenterRef(OrbitCenterRef orbitCenterRef) {
        this.orbitCenterRef = orbitCenterRef;
    }

    /**
     * 获取恒星半径（AU，新单位系统）。
     * 
     * @return 恒星半径（AstronomicalUnit），如果未设置则返回 null
     */
    public AstronomicalUnit getRadius() {
        return radius;
    }

    /**
     * 设置恒星半径（AU，新单位系统）。
     * 
     * @param radius 恒星半径（AU）
     * @throws IllegalArgumentException 如果半径为 null 或 <= 0
     */
    public void setRadius(AstronomicalUnit radius) {
        if (radius != null && radius.toAU() <= 0.0) {
            throw new IllegalArgumentException("恒星半径必须 > 0，当前值: " + radius.toAU() + " AU");
        }
        this.radius = radius;
    }
}
