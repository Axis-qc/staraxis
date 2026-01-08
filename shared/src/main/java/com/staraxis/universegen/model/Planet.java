package com.staraxis.universegen.model;

/**
 * 简易 Planet 记录，用于轨道渲染/验证。
 */
public record Planet(String name,
                     double radiusKm,
                     double semiMajorAxisKm,
                     double orbitalPeriodSeconds) {
}
