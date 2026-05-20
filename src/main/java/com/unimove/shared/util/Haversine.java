package com.unimove.shared.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Haversine {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private Haversine() {}

    public static BigDecimal distanceKm(BigDecimal lat1, BigDecimal lng1,
                                        BigDecimal lat2, BigDecimal lng2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double dPhi = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double dLambda = Math.toRadians(lng2.subtract(lng1).doubleValue());

        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return BigDecimal.valueOf(EARTH_RADIUS_KM * c).setScale(3, RoundingMode.HALF_UP);
    }
}
