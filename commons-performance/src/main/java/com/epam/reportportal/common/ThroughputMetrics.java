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
    private final BigDecimal steadyStateRps;
    private final BigDecimal peakRps;
    private final boolean steadyStateFellBackToOverall;
    private final long totalRequests;
    private final long steadyStateRequests;
    private final long totalDurationMs;
    private final long steadyStateDurationMs;

    public ThroughputMetrics(BigDecimal overallMeanRps,
                             BigDecimal steadyStateRps,
                             BigDecimal peakRps,
                             boolean steadyStateFellBackToOverall,
                             long totalRequests,
                             long steadyStateRequests,
                             long totalDurationMs,
                             long steadyStateDurationMs) {
        this.overallMeanRps = scale(overallMeanRps);
        this.steadyStateRps = scale(steadyStateRps);
        this.peakRps = scale(peakRps);
        this.steadyStateFellBackToOverall = steadyStateFellBackToOverall;
        this.totalRequests = totalRequests;
        this.steadyStateRequests = steadyStateRequests;
        this.totalDurationMs = totalDurationMs;
        this.steadyStateDurationMs = steadyStateDurationMs;
    }

    public static ThroughputMetrics empty() {
        return new ThroughputMetrics(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                false, 0L, 0L, 0L, 0L
        );
    }

    /** Overall mean: {@code total_requests / total_duration_seconds}. */
    public double getOverallMeanRps() {
        return overallMeanRps.doubleValue();
    }

    /** Steady-state mean inside the ramp-excluded window, or overall mean on fallback. */
    public double getSteadyStateRps() {
        return steadyStateRps.doubleValue();
    }

    /** Peak: {@code max(requests_in_window / windowSizeSeconds)}. */
    public double getPeakRps() {
        return peakRps.doubleValue();
    }

    public BigDecimal getOverallMeanRpsExact() {
        return overallMeanRps;
    }

    public BigDecimal getSteadyStateRpsExact() {
        return steadyStateRps;
    }

    public BigDecimal getPeakRpsExact() {
        return peakRps;
    }

    public boolean isSteadyStateFellBackToOverall() {
        return steadyStateFellBackToOverall;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public long getSteadyStateRequests() {
        return steadyStateRequests;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public long getSteadyStateDurationMs() {
        return steadyStateDurationMs;
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
        return steadyStateFellBackToOverall == that.steadyStateFellBackToOverall
                && totalRequests == that.totalRequests
                && steadyStateRequests == that.steadyStateRequests
                && totalDurationMs == that.totalDurationMs
                && steadyStateDurationMs == that.steadyStateDurationMs
                && Objects.equals(overallMeanRps, that.overallMeanRps)
                && Objects.equals(steadyStateRps, that.steadyStateRps)
                && Objects.equals(peakRps, that.peakRps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(overallMeanRps, steadyStateRps, peakRps, steadyStateFellBackToOverall,
                totalRequests, steadyStateRequests, totalDurationMs, steadyStateDurationMs);
    }

    @Override
    public String toString() {
        return "ThroughputMetrics{overall=" + overallMeanRps
                + ", steadyState=" + steadyStateRps
                + ", peak=" + peakRps
                + ", fallback=" + steadyStateFellBackToOverall
                + "}";
    }
}
