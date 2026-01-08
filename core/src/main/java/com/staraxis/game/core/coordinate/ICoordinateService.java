package com.staraxis.game.core.coordinate;

/**
 * 世界坐标与比例尺服务接口。
 *
 * 宪章要求：core 不依赖 UI/渲染；因此这里仅暴露纯数据与计算方法。
 */
public interface ICoordinateService {

    ScaleSystem getScaleSystem();

    /**
     * 便于 UI/调试显示：将坐标转换为可读的绝对 km 值。
     */
    default double toAbsoluteXKm(WorldCoordinate coord) {
        return coord.toAbsoluteXKm();
    }

    default double toAbsoluteYKm(WorldCoordinate coord) {
        return coord.toAbsoluteYKm();
    }

    default double toAbsoluteZKm(WorldCoordinate coord) {
        return coord.toAbsoluteZKm();
    }

    /**
     * 渲染前局部化：返回 world 相对 camera 的差值（km）。
     */
    default double deltaXKm(WorldCoordinate world, WorldCoordinate camera) {
        return world.deltaXKm(camera);
    }

    default double deltaYKm(WorldCoordinate world, WorldCoordinate camera) {
        return world.deltaYKm(camera);
    }

    default double deltaZKm(WorldCoordinate world, WorldCoordinate camera) {
        return world.deltaZKm(camera);
    }
}
