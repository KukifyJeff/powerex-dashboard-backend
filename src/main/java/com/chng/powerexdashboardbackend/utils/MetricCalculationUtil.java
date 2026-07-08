package com.chng.powerexdashboardbackend.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * MetricCalculationUtil
 * ----------------------
 * Unified utility for SpotTracking & energy market calculations.
 *
 * RULES:
 * 1. 0 is valid data
 * 2. null only means missing data
 * 3. all ratio calculations must use safe divide
 * 4. all outputs should be stable and frontend-friendly
 */
public class MetricCalculationUtil {

    /**
     * Safe division for ratio calculations
     */
    public static BigDecimal safeDivide(BigDecimal a, BigDecimal b) {
        if (a == null || b == null || b.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return a.divide(b, 6, RoundingMode.HALF_UP);
    }

    /**
     * Safe percentage conversion (0~1 scale)
     */
    public static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        return safeDivide(numerator, denominator);
    }

    /**
     * Convert raw energy value if needed (placeholder for consistency)
     */
    public static BigDecimal normalize(BigDecimal value, BigDecimal factor) {
        if (value == null) return BigDecimal.ZERO;
        if (factor == null || factor.compareTo(BigDecimal.ZERO) == 0) return value;
        return value.divide(factor, 6, RoundingMode.HALF_UP);
    }

    /**
     * Multiply with scale safety
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return BigDecimal.ZERO;
        return a.multiply(b);
    }

    /**
     * Default zero-safe value
     */
    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
