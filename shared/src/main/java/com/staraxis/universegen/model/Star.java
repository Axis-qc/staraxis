package com.staraxis.universegen.model;

import java.util.List;

/**
 * 代表 universegen 模块生成的单个恒星。
 */
public record Star(String name, String type, double massKg, List<Planet> planets) {
}
