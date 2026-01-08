package com.staraxis.universegen;

import com.staraxis.universegen.model.Planet;
import com.staraxis.universegen.model.Star;
import com.staraxis.universegen.model.StarSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 基于开普勒第三定律 (T^2 ∝ a^3) 生成恒星系。
 * 假设行星轨道近似圆形，忽略行星质量。
 *
 * 013：StarSystem 模型已升级为支持多恒星（1~3）。
 * 该生成器仍用于“给定单颗恒星质量”时生成该恒星的行星轨道参数。
 */
public final class StarSystemGenerator {

    private static final double G = 6.67430e-11; // m^3 kg^-1 s^-2
    private static final double KM_TO_M = 1_000.0;
    private static final double TWO_PI = Math.PI * 2;

    private final Random rng;

    public StarSystemGenerator(long seed) {
        this.rng = new Random(seed);
    }

    /**
     * 生成一个恒星系（star_system），其中包含一颗恒星（最小可用版本）。
     * planets 会挂在该恒星下。
     */
    public StarSystem generate(String systemName, double starMassKg, int planetMin, int planetMax) {
        Star star = generateStarWithPlanets(systemName + "-star-0", starMassKg, planetMin, planetMax);
        return new StarSystem(systemName, List.of(star));
    }

    /**
     * 生成单颗恒星及其行星列表。
     */
    public Star generateStarWithPlanets(String starName, double starMassKg, int planetMin, int planetMax) {
        int planetCount = planetMin + rng.nextInt(Math.max(1, planetMax - planetMin + 1));
        List<Planet> planets = new ArrayList<>(planetCount);

        // Simple spacing: first planet at 0.4 AU, multiplicative factor 1.7 (roughly Titius–Bode-like)
        double semiMajorKm = 0.4 * 1.496e+8; // km (0.4 AU)
        double spacingFactor = 1.7;

        for (int i = 0; i < planetCount; i++) {
            String planetName = starName + "-p" + (i + 1);
            double radiusKm = 2_000 + rng.nextDouble() * 5_000; // random radius 2000–7000 km
            // Each subsequent planet further away
            semiMajorKm *= (i == 0 ? 1 : spacingFactor);
            double periodSec = orbitalPeriodSeconds(semiMajorKm, starMassKg);
            planets.add(new Planet(planetName, radiusKm, semiMajorKm, periodSec));
        }

        // type 先用 unknown，后续可对齐到 WorldGenDefinitions 的数据驱动类型
        return new Star(starName, "unknown", starMassKg, planets);
    }

    /**
     * 开普勒第三定律推导：T = 2π * sqrt(a^3 / (G*M))
     *
     * @param semiMajorKm 轨道半长轴 km
     * @param starMassKg 恒星质量 kg
     */
    public static double orbitalPeriodSeconds(double semiMajorKm, double starMassKg) {
        double a_m = semiMajorKm * KM_TO_M;
        return TWO_PI * Math.sqrt(a_m * a_m * a_m / (G * starMassKg));
    }
}
