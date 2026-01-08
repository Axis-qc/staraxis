package com.staraxis.game.core.coordinate;

/**
 * 世界坐标与比例尺服务的默认实现。
 */
public final class CoordinateService implements ICoordinateService {

    private final ScaleSystem scaleSystem;

    public CoordinateService() {
        this.scaleSystem = new ScaleSystem();
    }

    public CoordinateService(ScaleSystem scaleSystem) {
        this.scaleSystem = scaleSystem;
    }

    @Override
    public ScaleSystem getScaleSystem() {
        return scaleSystem;
    }
}
