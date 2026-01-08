package com.staraxis.universegen.model;

import java.util.List;

/**
 * 恒星系（star_system）。
 * 
 * 术语：
 * - galaxy：六边形星区类型
 * - star_system：galaxy 星区内部的恒星系
 */
public record StarSystem(String name, List<Star> stars) {

    public StarSystem {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (stars == null) {
            throw new IllegalArgumentException("stars 不能为空");
        }
        if (stars.size() < 1 || stars.size() > 3) {
            throw new IllegalArgumentException("stars 数量必须在 [1,3]");
        }
    }
}
