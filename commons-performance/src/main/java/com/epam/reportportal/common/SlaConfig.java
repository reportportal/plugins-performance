package com.epam.reportportal.common;

/**
 * Optional SLA thresholds. Blank / null values disable that check.
 */
public final class SlaConfig {

    private final Long p95Ms;
    private final Long p99Ms;
    private final Double errorRatePct;

    public SlaConfig(Long p95Ms, Long p99Ms, Double errorRatePct) {
        this.p95Ms = p95Ms;
        this.p99Ms = p99Ms;
        this.errorRatePct = errorRatePct;
    }

    public static SlaConfig fromParameters(String p95Ms, String p99Ms, String errorRatePct) {
        return new SlaConfig(
                parsePositiveLong(p95Ms),
                parsePositiveLong(p99Ms),
                parseNonNegativeDouble(errorRatePct)
        );
    }

    public boolean hasAnyThreshold() {
        return p95Ms != null || p99Ms != null || errorRatePct != null;
    }

    public Long getP95Ms() {
        return p95Ms;
    }

    public Long getP99Ms() {
        return p99Ms;
    }

    public Double getErrorRatePct() {
        return errorRatePct;
    }

    private static Long parsePositiveLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        long parsed = Long.parseLong(value.trim());
        if (parsed < 0) {
            throw new IllegalArgumentException("SLA threshold must be >= 0: " + value);
        }
        return parsed;
    }

    private static Double parseNonNegativeDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        double parsed = Double.parseDouble(value.trim());
        if (parsed < 0) {
            throw new IllegalArgumentException("SLA threshold must be >= 0: " + value);
        }
        return parsed;
    }
}
