package com.staraxis.game.shared.net.worldgen.snapshot;

import java.util.ArrayList;
import java.util.List;

public class StarSnapshot {

    private String id;
    private String starTypeId;
    private List<PlanetSnapshot> planets;

    public StarSnapshot() {
        this.planets = new ArrayList<>();
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

    public List<PlanetSnapshot> getPlanets() {
        return planets;
    }

    public void setPlanets(List<PlanetSnapshot> planets) {
        this.planets = planets != null ? planets : new ArrayList<>();
    }
}
