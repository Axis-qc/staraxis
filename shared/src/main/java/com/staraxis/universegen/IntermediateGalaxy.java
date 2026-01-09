package com.staraxis.universegen;

import com.staraxis.universegen.model.Galaxy;

import java.util.Map;

/**
 * 用于在生成器内部传递领域对象与附加数据的中间记录。
 */
public record IntermediateGalaxy(Galaxy galaxy, Map<Long, String> presetOccupancy) {
}
