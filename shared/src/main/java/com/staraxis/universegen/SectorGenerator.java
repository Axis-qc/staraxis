package com.staraxis.universegen;

import com.staraxis.universegen.config.UniverseGenConfig;

import java.util.SplittableRandom;

public class SectorGenerator {
    public com.staraxis.universegen.model.Sector generate(int id, UniverseGenConfig cfg, SplittableRandom rng) {
        // TODO: 真实实现星系与深空比率
        return new com.staraxis.universegen.model.Sector(id);
    }
}
