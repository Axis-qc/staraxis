package staraxis.game.space;

/**
 * OrbitSolver（轨道求解器）。
 *
 * 根据轨道根数和时间计算天体在 3D 空间中的位置。
 * 使用开普勒方程 + 3-1-3 欧拉旋转变换。
 *
 * 计算流程：
 * 1. 计算平近点角 M = M0 + 2pi * (t - t0) / T
 * 2. 求解开普勒方程 M = E - e*sin(E) -> 偏近点角 E（牛顿迭代）
 * 3. 计算真近点角 nu = 2*atan2(sqrt(1+e)*sin(E/2), sqrt(1-e)*cos(E/2))
 * 4. 计算轨道平面位置：r = a*(1 - e*cos(E)), x_local = r*cos(nu), y_local = r*sin(nu)
 * 5. 通过 3-1-3 欧拉旋转 (Omega, i, omega) 变换到星系坐标系
 */
public final class OrbitSolver {

    /** 牛顿迭代最大次数。 */
    private static final int MAX_ITERATIONS = 10;

    /** 收敛精度（弧度）。 */
    private static final double CONVERGENCE_THRESHOLD = 1e-10;

    private OrbitSolver() {
    }

    /**
     * 根据轨道根数和当前时间计算天体位置。
     *
     * @param elements 轨道根数
     * @param time 当前时间（游戏秒）
     * @return 天体在星系坐标系中的 3D 位置（GU）
     */
    public static SpacePosition solve(OrbitalElements elements, double time) {
        // 1. 计算平近点角 M
        double meanMotion = 2.0 * Math.PI / elements.period();
        double meanAnomaly = elements.meanAnomalyAtEpoch() + meanMotion * (time - elements.epoch());
        // 归一化到 [0, 2pi)
        meanAnomaly = normalizeAngle(meanAnomaly);

        // 2. 求解开普勒方程 M = E - e*sin(E) -> 偏近点角 E
        double eccentricAnomaly = solveKeplerEquation(meanAnomaly, elements.eccentricity());

        // 3. 计算真近点角 nu
        double trueAnomaly = computeTrueAnomaly(eccentricAnomaly, elements.eccentricity());

        // 4. 计算轨道平面位置
        double r = elements.semiMajorAxis() * (1.0 - elements.eccentricity() * Math.cos(eccentricAnomaly));
        double xLocal = r * Math.cos(trueAnomaly);
        double yLocal = r * Math.sin(trueAnomaly);

        // 5. 通过 3-1-3 欧拉旋转 (Omega, i, omega) 变换到星系坐标系
        return rotateToGalaxyFrame(
            xLocal, yLocal,
            elements.longitudeOfAscendingNode(),
            elements.inclination(),
            elements.argumentOfPeriapsis()
        );
    }

    /**
     * 求解开普勒方程 M = E - e*sin(E)。
     * 使用牛顿迭代法，通常 3-5 次收敛。
     *
     * @param meanAnomaly 平近点角 M（弧度）
     * @param eccentricity 偏心率 e
     * @return 偏近点角 E（弧度）
     */
    private static double solveKeplerEquation(double meanAnomaly, double eccentricity) {
        // 初始猜测值
        double E = meanAnomaly;
        if (eccentricity > 0.8) {
            E = Math.PI; // 高偏心率时用 PI 作为初始值更稳定
        }

        // 牛顿迭代：E_{n+1} = E_n - f(E_n) / f'(E_n)
        // f(E) = E - e*sin(E) - M
        // f'(E) = 1 - e*cos(E)
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double f = E - eccentricity * Math.sin(E) - meanAnomaly;
            double fPrime = 1.0 - eccentricity * Math.cos(E);
            double delta = f / fPrime;
            E -= delta;

            if (Math.abs(delta) < CONVERGENCE_THRESHOLD) {
                break;
            }
        }

        return E;
    }

    /**
     * 计算真近点角 nu。
     *
     * @param eccentricAnomaly 偏近点角 E（弧度）
     * @param eccentricity 偏心率 e
     * @return 真近点角 nu（弧度）
     */
    private static double computeTrueAnomaly(double eccentricAnomaly, double eccentricity) {
        double halfE = eccentricAnomaly / 2.0;
        double sinHalfE = Math.sin(halfE);
        double cosHalfE = Math.cos(halfE);
        double sqrtOnePlusE = Math.sqrt(1.0 + eccentricity);
        double sqrtOneMinusE = Math.sqrt(1.0 - eccentricity);
        return 2.0 * Math.atan2(sqrtOnePlusE * sinHalfE, sqrtOneMinusE * cosHalfE);
    }

    /**
     * 通过 3-1-3 欧拉旋转 (Omega, i, omega) 将轨道平面坐标变换到星系坐标系。
     *
     * 旋转矩阵 R = R_z(-Omega) * R_x(-i) * R_z(-omega)
     *
     * @param xLocal 轨道平面 X 坐标
     * @param yLocal 轨道平面 Y 坐标
     * @param omega 升交点经度（弧度）
     * @param inclination 轨道倾角（弧度）
     * @param argumentOfPeriapsis 近心点幅角（弧度）
     * @return 星系坐标系中的位置
     */
    private static SpacePosition rotateToGalaxyFrame(
        double xLocal, double yLocal,
        double omega, double inclination, double argumentOfPeriapsis
    ) {
        // 预计算三角函数
        double cosOmega = Math.cos(omega);
        double sinOmega = Math.sin(omega);
        double cosI = Math.cos(inclination);
        double sinI = Math.sin(inclination);
        double cosW = Math.cos(argumentOfPeriapsis);
        double sinW = Math.sin(argumentOfPeriapsis);

        // 旋转矩阵元素（3-1-3 欧拉旋转）
        // P = R_z(-Omega) * R_x(-i) * R_z(-omega)
        double p11 = cosOmega * cosW - sinOmega * sinW * cosI;
        double p12 = -cosOmega * sinW - sinOmega * cosW * cosI;
        double p21 = sinOmega * cosW + cosOmega * sinW * cosI;
        double p22 = -sinOmega * sinW + cosOmega * cosW * cosI;
        double p31 = sinW * sinI;
        double p32 = cosW * sinI;

        // 变换到星系坐标系
        double x = p11 * xLocal + p12 * yLocal;
        double y = p21 * xLocal + p22 * yLocal;
        double z = p31 * xLocal + p32 * yLocal;

        return new SpacePosition(x, y, z);
    }

    /**
     * 将角度归一化到 [0, 2*pi) 范围。
     */
    private static double normalizeAngle(double angle) {
        double twoPi = 2.0 * Math.PI;
        angle = angle % twoPi;
        if (angle < 0) {
            angle += twoPi;
        }
        return angle;
    }
}
