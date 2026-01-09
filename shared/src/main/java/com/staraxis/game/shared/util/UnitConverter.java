package com.staraxis.game.shared.util;

/**
 * 单位转换工具类。
 */
public final class UnitConverter {

    private UnitConverter() {}

    /** 1 天文单位 (AU) 等于多少公里 (km) */
    public static final double KM_PER_AU = 149_597_870.7;

    /** 1 光年 (ly) 等于多少天文单位 (AU) */
    public static final double AU_PER_LIGHT_YEAR = 63_241.0;

    /**
     * 将光年转换为公里。
     * @param lightYears 光年值
     * @return 公里值
     */
    public static double lightYearsToKm(double lightYears) {
        return lightYears * AU_PER_LIGHT_YEAR * KM_PER_AU;
    }

    /**
     * 将天文单位转换为公里。
     * @param au 天文单位值
     * @return 公里值
     */
    public static double auToKm(double au) {
        return au * KM_PER_AU;
    }

    /**
     * 将公里转换为天文单位。
     * @param km 公里值
     * @return 天文单位值
     */
    public static double kmToAu(double km) {
        return km / KM_PER_AU;
    }

    /**
     * 将公里转换为光年。
     * @param km 公里值
     * @return 光年值
     */
    public static double kmToLightYears(double km) {
        return (km / KM_PER_AU) / AU_PER_LIGHT_YEAR;
    }
}