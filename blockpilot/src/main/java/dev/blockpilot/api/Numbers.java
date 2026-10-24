package dev.blockpilot.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Compact number printing shared by every output: integers without a decimal point, others with at most 2 places. */
public final class Numbers {
    private Numbers() {
    }

    public static String format(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** The same rounding as {@link #format}, as a number for JSON output. */
    public static double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
