package com.epam.reportportal.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Pure throughput math. No I/O, no ReportPortal, no tool-specific types beyond
 * {@link ThroughputSample} / {@link PerformanceSample}.
 * <p>
 * Test lifecycle is {@code [T_start, T_end]} where {@code T_start} is the earliest
 * request start and {@code T_end} is the latest request completion.
 * Rates use the real fractional duration (a 500 ms run is 0.5 s, not padded to 1 s).
 * A zero-length timeline (every sample at the same instant) or an empty input
 * yields {@code 0.00} rather than dividing by zero.
 */
public final class ThroughputCalculator {

    private static final Logger logger = LoggerFactory.getLogger(ThroughputCalculator.class);
    private static final int SCALE = 2;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);

    private ThroughputCalculator() {
    }

    public static ThroughputMetrics calculate(Iterable<ThroughputSample> samples) {
        return calculate(samples, ThroughputConfig.defaults());
    }

    public static ThroughputMetrics calculate(Iterable<ThroughputSample> samples, ThroughputConfig config) {
        if (samples == null) {
            return ThroughputMetrics.empty();
        }
        return calculate(samples.iterator(), config);
    }

    public static ThroughputMetrics calculate(Stream<ThroughputSample> samples, ThroughputConfig config) {
        if (samples == null) {
            return ThroughputMetrics.empty();
        }
        return calculate(samples.iterator(), config);
    }

    public static ThroughputMetrics fromPerformanceSamples(Iterable<PerformanceSample> samples,
                                                           ThroughputConfig config) {
        if (samples == null) {
            return ThroughputMetrics.empty();
        }
        List<ThroughputSample> mapped = new ArrayList<>();
        for (PerformanceSample sample : samples) {
            ThroughputSample converted = ThroughputSample.from(sample);
            if (converted != null) {
                mapped.add(converted);
            }
        }
        return calculate(mapped, config);
    }

    private static ThroughputMetrics calculate(Iterator<ThroughputSample> iterator, ThroughputConfig config) {
        ThroughputConfig effective = config != null ? config : ThroughputConfig.defaults();

        int n = 0;
        int cap = 16;
        long[] starts = new long[cap];
        long[] ends = new long[cap];

        while (iterator.hasNext()) {
            ThroughputSample sample = iterator.next();
            if (sample == null) {
                continue;
            }
            if (n == cap) {
                cap *= 2;
                starts = Arrays.copyOf(starts, cap);
                ends = Arrays.copyOf(ends, cap);
            }
            starts[n] = sample.getStartTimeMs();
            ends[n] = sample.getEndTimeMs();
            n++;
        }

        if (n == 0) {
            return ThroughputMetrics.empty();
        }
        return compute(Arrays.copyOf(starts, n), Arrays.copyOf(ends, n), effective);
    }

    private static ThroughputMetrics compute(long[] startTimes, long[] endTimes, ThroughputConfig config) {
        int n = endTimes.length;
        long tStart = Long.MAX_VALUE;
        long tEnd = Long.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            tStart = Math.min(tStart, startTimes[i]);
            tEnd = Math.max(tEnd, endTimes[i]);
        }

        long totalDurationMs = tEnd - tStart;
        BigDecimal overall = rps(n, totalDurationMs);

        SteadyStateWindow window = resolveSteadyStateWindow(tStart, tEnd, totalDurationMs, config);
        long steadyRequests = window.fallback
                ? n
                : countCompletionsInClosedInterval(endTimes, window.startMs, window.endMs);
        BigDecimal steady = window.fallback
                ? overall
                : rps(steadyRequests, window.durationMs);

        BigDecimal peak = peakRps(endTimes, config.getWindowSizeMs(), totalDurationMs, overall);

        return new ThroughputMetrics(
                overall,
                steady,
                peak,
                window.fallback,
                n,
                steadyRequests,
                totalDurationMs,
                window.durationMs
        );
    }

    private static SteadyStateWindow resolveSteadyStateWindow(long tStart,
                                                              long tEnd,
                                                              long totalDurationMs,
                                                              ThroughputConfig config) {
        long rampUpMs = config.getRampUpMs();
        long rampDownMs = config.getRampDownMs();
        long excluded = rampUpMs + rampDownMs;

        if (excluded >= totalDurationMs) {
            if (excluded > 0) {
                logger.warn("rampUpDuration ({}) + rampDownDuration ({}) >= total duration ({} ms); "
                                + "falling back to overall mean throughput",
                        config.getRampUpDuration(), config.getRampDownDuration(), totalDurationMs);
            }
            return new SteadyStateWindow(tStart, tEnd, totalDurationMs, excluded > 0);
        }

        long windowStart = tStart + rampUpMs;
        long windowEnd = tEnd - rampDownMs;
        return new SteadyStateWindow(windowStart, windowEnd, windowEnd - windowStart, false);
    }

    /**
     * Inclusive on both ends, matching {@code [T_start + rampUp, T_end - rampDown]}.
     */
    private static long countCompletionsInClosedInterval(long[] endTimes, long fromInclusive, long toInclusive) {
        long count = 0L;
        for (long end : endTimes) {
            if (end >= fromInclusive && end <= toInclusive) {
                count++;
            }
        }
        return count;
    }

    /**
     * Sliding window over completion times: the maximum number of completions whose
     * timestamps all lie in some half-open interval {@code [t, t + windowMs)}.
     * Normalized as {@code count / windowSizeSeconds}.
     * <p>
     * When the whole run is shorter than the window, the peak is the overall mean
     * so a sub-second burst is not diluted by a 1 s (or 5 s, 10 s) denominator.
     */
    private static BigDecimal peakRps(long[] endTimes, long windowMs, long totalDurationMs, BigDecimal overallMean) {
        if (endTimes.length == 0 || totalDurationMs <= 0) {
            return ZERO;
        }
        if (totalDurationMs < windowMs) {
            return overallMean;
        }

        long[] sorted = Arrays.copyOf(endTimes, endTimes.length);
        Arrays.sort(sorted);

        int maxCount = 0;
        int left = 0;
        for (int right = 0; right < sorted.length; right++) {
            while (sorted[right] - sorted[left] >= windowMs) {
                left++;
            }
            maxCount = Math.max(maxCount, right - left + 1);
        }

        return rps(maxCount, windowMs);
    }

    /**
     * {@code requests / (durationMs / 1000)}. Zero duration or zero requests → 0.00.
     */
    private static BigDecimal rps(long requests, long durationMs) {
        if (requests <= 0 || durationMs <= 0) {
            return ZERO;
        }
        return BigDecimal.valueOf(requests)
                .multiply(BigDecimal.valueOf(1000L))
                .divide(BigDecimal.valueOf(durationMs), SCALE, RoundingMode.HALF_UP);
    }

    private static final class SteadyStateWindow {
        private final long startMs;
        private final long endMs;
        private final long durationMs;
        private final boolean fallback;

        private SteadyStateWindow(long startMs, long endMs, long durationMs, boolean fallback) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.durationMs = durationMs;
            this.fallback = fallback;
        }
    }
}
