package com.epam.reportportal.common;

/**
 * Optional SLA thresholds. Blank / null values disable that check.
 */
public final class SlaConfig {

    private final Long p95Ms;
    private final Long p99Ms;
    private final Double errorRatePct;
    private final Double minOverallThroughputRps;
    private final Double minPeakThroughputRps;

    public SlaConfig(Long p95Ms, Long p99Ms, Double errorRatePct,
                     Double minOverallThroughputRps, Double minPeakThroughputRps) {
        this.p95Ms = p95Ms;
        this.p99Ms = p99Ms;
        this.errorRatePct = errorRatePct;
        this.minOverallThroughputRps = minOverallThroughputRps;
        this.minPeakThroughputRps = minPeakThroughputRps;
    }

    public static SlaConfig fromParameters(String p95Ms, String p99Ms, String errorRatePct) {
        return fromParameters(p95Ms, p99Ms, errorRatePct, null, null);
    }

    public static SlaConfig fromParameters(String p95Ms, String p99Ms, String errorRatePct,
                                           String minOverallThroughputRps, String minPeakThroughputRps) {
        return new SlaConfig(
                parsePositiveLong(p95Ms),
                parsePositiveLong(p99Ms),
                parseNonNegativeDouble(errorRatePct),
                parseNonNegativeDouble(minOverallThroughputRps),
                parseNonNegativeDouble(minPeakThroughputRps)
        );
    }

    public boolean hasAnyThreshold() {
        return p95Ms != null || p99Ms != null || errorRatePct != null
                || minOverallThroughputRps != null || minPeakThroughputRps != null;
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

    /** Minimum overall mean throughput (req/s) for the launch. */
    public Double getMinOverallThroughputRps() {
        return minOverallThroughputRps;
    }

    /** Minimum peak throughput (req/s) over the configured sliding window. */
    public Double getMinPeakThroughputRps() {
        return minPeakThroughputRps;
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
