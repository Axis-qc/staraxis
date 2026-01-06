package com.staraxis.game.shared.net.worldgen.snapshot;

import java.util.ArrayList;
import java.util.List;

public class StarSystemSnapshot {

    private String id;
    private List<StarSnapshot> stars;

    public StarSystemSnapshot() {
        this.stars = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<StarSnapshot> getStars() {
        return stars;
    }

    public void setStars(List<StarSnapshot> stars) {
        this.stars = stars != null ? stars : new ArrayList<>();
    }
}
