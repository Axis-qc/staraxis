package staraxis.game.space;

/**
 * OrbitalElements（轨道根数）。
 *
 * 描述天体绕中心天体运动的开普勒轨道参数。
 * 所有角度使用弧度制，距离使用 GU（Game Unit），时间使用游戏秒。
 *
 * 轨道周期 T 是游戏设计值，不由开普勒第三定律推导（因为轨道距离已被压缩）。
 * 给定时间 t + 轨道根数 -> 唯一位置，无累积误差（确定性模拟）。
 */
public record OrbitalElements(
    /** 半长轴 a（GU）。 */
    double semiMajorAxis,

    /** 偏心率 e [0, 1)。0 = 圆轨道，接近1 = 高椭圆轨道。 */
    double eccentricity,

    /** 轨道倾角 i（弧度，相对星系盘面）。 */
    double inclination,

    /** 升交点经度 Omega（弧度）。 */
    double longitudeOfAscendingNode,

    /** 近心点幅角 omega（弧度）。 */
    double argumentOfPeriapsis,

    /** 历元平近点角 M0（弧度，t=epoch 时）。 */
    double meanAnomalyAtEpoch,

    /** 历元时间 t0（游戏秒）。 */
    double epoch,

    /** 轨道周期 T（游戏秒）。 */
    double period
) {

    /**
     * 验证轨道根数是否在合理范围内。
     */
    public boolean isValid() {
        return semiMajorAxis > 0
            && eccentricity >= 0 && eccentricity < 1
            && period > 0;
    }

    @Override
    public String toString() {
        return String.format(
            "OrbitalElements(a=%.1f, e=%.4f, i=%.4f, Omega=%.4f, omega=%.4f, M0=%.4f, epoch=%.1f, T=%.1f)",
            semiMajorAxis, eccentricity, inclination,
            longitudeOfAscendingNode, argumentOfPeriapsis,
            meanAnomalyAtEpoch, epoch, period
        );
    }
}
