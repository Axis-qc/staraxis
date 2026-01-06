package com.staraxis.game.shared.world.stellar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 星系系统（StarSystem）。
 *
 * 作用（Purpose）：承载单个星系区块内的恒星集合。 依赖（Dependencies）：仅 Java 标准库。 对外接口（Public
 * API）：getId/setId/getStars/setStars。
 */
public class StarSystem implements Serializable {

    private String id; // id（系统唯一标识）
    private List<Star> stars; // stars（恒星列表，长度必须在 [1,3]）

    private List<String> barycenterIds; // barycenterIds（共同质心/子系统节点标识列表，允许为空）

    private WorldGenDiagnostics diagnostics; // diagnostics（生成诊断信息；可为空）

    public StarSystem() {
        this.stars = new ArrayList<>();
        this.barycenterIds = new ArrayList<>();
    }

    public StarSystem(String id, List<Star> stars) {
        this.id = id;
        setStars(stars);
        this.barycenterIds = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Star> getStars() {
        return Collections.unmodifiableList(stars);
    }

    public void setStars(List<Star> stars) {
        if (stars == null) {
            throw new IllegalArgumentException("stars（恒星列表）不能为空");
        }
        if (stars.size() < 1 || stars.size() > 3) {
            throw new IllegalArgumentException("stars（恒星列表）长度必须在 [1,3]");
        }
        this.stars = new ArrayList<>(stars);
    }

    public List<String> getBarycenterIds() {
        return Collections.unmodifiableList(barycenterIds);
    }

    public void setBarycenterIds(List<String> barycenterIds) {
        if (barycenterIds == null) {
            this.barycenterIds = new ArrayList<>();
            return;
        }
        this.barycenterIds = new ArrayList<>(barycenterIds);
    }

    public WorldGenDiagnostics getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(WorldGenDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }
}
