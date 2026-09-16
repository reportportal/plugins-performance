package com.epam.reportportal.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Throughput in requests per second, each value already rounded to 2 decimal places.
 */
public final class ThroughputMetrics {

    private static final int SCALE = 2;

    private final BigDecimal overallMeanRps;
    private final BigDecimal peakRps;
    private final long totalRequests;
    private final long totalDurationMs;

    public ThroughputMetrics(BigDecimal overallMeanRps,
                             BigDecimal peakRps,
                             long totalRequests,
                             long totalDurationMs) {
        this.overallMeanRps = scale(overallMeanRps);
        this.peakRps = scale(peakRps);
        this.totalRequests = totalRequests;
        this.totalDurationMs = totalDurationMs;
    }

    public static ThroughputMetrics empty() {
        return new ThroughputMetrics(BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L);
    }

    /** Overall mean: {@code total_requests / total_duration_seconds}. */
    public double getOverallMeanRps() {
        return overallMeanRps.doubleValue();
    }

    /** Peak: {@code max(requests_in_window / windowSizeSeconds)}. */
    public double getPeakRps() {
        return peakRps.doubleValue();
    }

    public BigDecimal getOverallMeanRpsExact() {
        return overallMeanRps;
    }

    public BigDecimal getPeakRpsExact() {
        return peakRps;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    private static BigDecimal scale(BigDecimal value) {
        BigDecimal v = value != null ? value : BigDecimal.ZERO;
        return v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ThroughputMetrics)) {
            return false;
        }
        ThroughputMetrics that = (ThroughputMetrics) o;
        return totalRequests == that.totalRequests
                && totalDurationMs == that.totalDurationMs
                && Objects.equals(overallMeanRps, that.overallMeanRps)
                && Objects.equals(peakRps, that.peakRps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(overallMeanRps, peakRps, totalRequests, totalDurationMs);
    }

    @Override
    public String toString() {
        return "ThroughputMetrics{overall=" + overallMeanRps + ", peak=" + peakRps + "}";
    }
}
