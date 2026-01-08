package com.staraxis.universegen;

import com.staraxis.universegen.model.Planet;
import com.staraxis.universegen.model.StarSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 基于开普勒第三定律 (T^2 ∝ a^3) 生成恒星系。
 * 假设行星轨道近似圆形，忽略行星质量。
 */
public final class StarSystemGenerator {

    private static final double G = 6.67430e-11; // m^3 kg^-1 s^-2
    private static final double KM_TO_M = 1_000.0;
    private static final double TWO_PI = Math.PI * 2;

    private final Random rng;

    public StarSystemGenerator(long seed) {
        this.rng = new Random(seed);
    }

    public StarSystem generate(String systemName, double starMassKg, int planetMin, int planetMax) {
        int planetCount = planetMin + rng.nextInt(Math.max(1, planetMax - planetMin + 1));
        List<Planet> planets = new ArrayList<>(planetCount);

        // Simple spacing: first planet at 0.4 AU, multiplicative factor 1.7 (roughly Titius–Bode-like)
        double semiMajorKm = 0.4 * 1.496e+8; // km (0.4 AU)
        double spacingFactor = 1.7;

        for (int i = 0; i < planetCount; i++) {
            String planetName = systemName + "-p" + (i + 1);
            double radiusKm = 2_000 + rng.nextDouble() * 5_000; // random radius 2000–7000 km
            // Each subsequent planet further away
            semiMajorKm *= (i == 0 ? 1 : spacingFactor);
            double periodSec = orbitalPeriodSeconds(semiMajorKm, starMassKg);
            planets.add(new Planet(planetName, radiusKm, semiMajorKm, periodSec));
        }
        return new StarSystem(systemName, starMassKg, planets);
    }

    /**
     * 开普勒第三定律推导：T = 2π * sqrt(a^3 / (G*M))
     * @param semiMajorKm 轨道半长轴 km
     * @param starMassKg 恒星质量 kg
     */
    public static double orbitalPeriodSeconds(double semiMajorKm, double starMassKg) {
        double a_m = semiMajorKm * KM_TO_M;
        double period = TWO_PI * Math.sqrt(a_m * a_m * a_m / (G * starMassKg));
        return period;
    }
}
