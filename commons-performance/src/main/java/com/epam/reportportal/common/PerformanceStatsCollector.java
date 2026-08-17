package com.epam.reportportal.common;

import org.HdrHistogram.ConcurrentHistogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Streaming performance metrics with fixed memory via HdrHistogram.
 * Does not retain individual response times.
 */
public class PerformanceStatsCollector {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceStatsCollector.class);

    // Highest trackable latency: 1 hour. 2 significant digits (1% precision) costs ~32 KB per
    // histogram; 3 digits would cost ~208 KB, which only pays off with few request names.
    private static final long HIGHEST_TRACKABLE_VALUE_MS = 3_600_000L;
    private static final int SIGNIFICANT_DIGITS = 2;

    /**
     * Upper bound on distinct request names. Simulations that build names dynamically
     * (an id in the name) would otherwise grow memory and the reported tree without limit.
     */
    public static final int MAX_TRACKED_NAMES = 1000;

    /** Request name that absorbs everything past {@link #MAX_TRACKED_NAMES}. */
    public static final String OVERFLOW_NAME = "(other requests)";

    public static class SamplerStats {
        public final String name;
        private final LongAdder total = new LongAdder();
        private final LongAdder success = new LongAdder();
        private final LongAdder failed = new LongAdder();
        private final LongAdder sumResponseTime = new LongAdder();
        private final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxResponseTime = new AtomicLong(0L);
        private final ConcurrentHistogram histogram =
                new ConcurrentHistogram(HIGHEST_TRACKABLE_VALUE_MS, SIGNIFICANT_DIGITS);

        public SamplerStats(String name) {
            this.name = name;
            this.histogram.setAutoResize(true);
        }

        public void addSample(long durationMs, boolean successful) {
            total.increment();
            if (successful) {
                success.increment();
            } else {
                failed.increment();
            }

            long duration = Math.max(0L, durationMs);
            if (duration > HIGHEST_TRACKABLE_VALUE_MS) {
                duration = HIGHEST_TRACKABLE_VALUE_MS;
            }

            sumResponseTime.add(duration);
            histogram.recordValue(duration);
            updateMin(duration);
            updateMax(duration);
        }

        private void updateMin(long duration) {
            long current;
            do {
                current = minResponseTime.get();
                if (duration >= current) {
                    return;
                }
            } while (!minResponseTime.compareAndSet(current, duration));
        }

        private void updateMax(long duration) {
            long current;
            do {
                current = maxResponseTime.get();
                if (duration <= current) {
                    return;
                }
            } while (!maxResponseTime.compareAndSet(current, duration));
        }

        public long getTotal() {
            return total.sum();
        }

        public long getSuccess() {
            return success.sum();
        }

        public long getFailed() {
            return failed.sum();
        }

        public double getErrorRate() {
            long t = getTotal();
            return t == 0 ? 0.0 : ((double) getFailed() / t) * 100.0;
        }

        public long getMin() {
            long min = minResponseTime.get();
            return min == Long.MAX_VALUE ? 0L : min;
        }

        public long getMax() {
            return maxResponseTime.get();
        }

        public long getAvg() {
            long t = getTotal();
            return t == 0 ? 0L : sumResponseTime.sum() / t;
        }

        public long getPercentile(double percentile) {
            if (histogram.getTotalCount() == 0) {
                return 0L;
            }
            return histogram.getValueAtPercentile(percentile);
        }

        void mergeInto(SamplerStats target) {
            target.total.add(getTotal());
            target.success.add(getSuccess());
            target.failed.add(getFailed());
            target.sumResponseTime.add(sumResponseTime.sum());
            target.updateMin(getMin());
            target.updateMax(getMax());
            target.histogram.add(histogram);
        }
    }

    private final Map<String, SamplerStats> statsMap = new ConcurrentHashMap<>();
    private final AtomicBoolean overflowReported = new AtomicBoolean();

    public void registerSample(PerformanceSample sample) {
        registerSample(sample.getLabel(), sample.getDurationMs(), sample.isSuccess());
    }

    public void registerSample(String name, long durationMs, boolean success) {
        statsFor(name).addSample(durationMs, success);
    }

    private SamplerStats statsFor(String name) {
        // Plain get() first: computeIfAbsent locks the bin whenever the key is not its first node.
        SamplerStats existing = statsMap.get(name);
        if (existing != null) {
            return existing;
        }

        if (statsMap.size() >= MAX_TRACKED_NAMES) {
            if (overflowReported.compareAndSet(false, true)) {
                logger.warn("Reached {} distinct request names; the rest are merged into '{}'. "
                                + "Request names that embed dynamic values make per-request metrics unusable.",
                        MAX_TRACKED_NAMES, OVERFLOW_NAME);
            }
            return statsMap.computeIfAbsent(OVERFLOW_NAME, SamplerStats::new);
        }

        return statsMap.computeIfAbsent(name, SamplerStats::new);
    }

    public Map<String, SamplerStats> getStatsMap() {
        return statsMap;
    }

    public SamplerStats getGlobalStats() {
        SamplerStats global = new SamplerStats("Global");
        for (SamplerStats s : statsMap.values()) {
            s.mergeInto(global);
        }
        return global;
    }
}
