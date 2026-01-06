package com.staraxis.game.shared.world.stellar;

import java.io.Serializable;

import com.staraxis.game.shared.world.astronomical.AstronomicalUnit;
import com.staraxis.game.shared.world.stellar.orbit.Orbit;
import com.staraxis.game.shared.world.stellar.surface.PlanetSurfaceMesh;

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

    private Orbit orbit; // orbit（轨道参数；可为空，后续生成填充）
    private PlanetSurfaceMesh surfaceMesh; // surfaceMesh（行星表面网格；可为空，后续生成填充）
    private AstronomicalUnit radius; // radius（行星半径，以 AU 为单位；新单位系统）

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

    public Orbit getOrbit() {
        return orbit;
    }

    public void setOrbit(Orbit orbit) {
        this.orbit = orbit;
    }

    public PlanetSurfaceMesh getSurfaceMesh() {
        return surfaceMesh;
    }

    public void setSurfaceMesh(PlanetSurfaceMesh surfaceMesh) {
        this.surfaceMesh = surfaceMesh;
    }

    /**
     * 获取行星半径（AU，新单位系统）。
     * 
     * @return 行星半径（AstronomicalUnit），如果未设置则返回 null
     */
    public AstronomicalUnit getRadius() {
        return radius;
    }

    /**
     * 设置行星半径（AU，新单位系统）。
     * 
     * @param radius 行星半径（AU）
     * @throws IllegalArgumentException 如果半径为 null 或 <= 0
     */
    public void setRadius(AstronomicalUnit radius) {
        if (radius != null && radius.toAU() <= 0.0) {
            throw new IllegalArgumentException("行星半径必须 > 0，当前值: " + radius.toAU() + " AU");
        }
        this.radius = radius;
    }
}
